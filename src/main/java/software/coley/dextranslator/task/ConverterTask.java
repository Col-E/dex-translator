package software.coley.dextranslator.task;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.ir.IRCodeHacking;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Task for converting content within am {@link ApplicationData} into a target platform determined by the {@link Options}.
 * <p/>
 * To convert all content in the {@link ApplicationData} to Java ensure the {@link Options}
 * are configured to write to one of the following:
 * <ul>
 *     <li>{@link Options#setJvmArchiveOutput(Path, boolean)}</li>
 *     <li>{@link Options#setJvmDirectoryOutput(Path, boolean)}</li>
 *     <li>{@link Options#setJvmOutput(ClassFileConsumer)}</li>
 * </ul>
 * <p/>
 * To convert all content in the {@link ApplicationData} to Dalvik ensure the {@link Options}
 * are configured to write to one of the following:
 * <ul>
 *     <li>{@link Options#setDexFileOutput(Path)}</li>
 *     <li>{@link Options#setDexDirectoryOutput(Path)}</li>
 *     <li>{@link Options#setDexOutput(DexIndexedConsumer)}</li>
 * </ul>
 *
 * @author Matt Coley
 */
public class ConverterTask extends AbstractTask<ConverterResult> {
	private static final Int2ReferenceArrayMap<DebugLocalInfo> EMPTY_ARRAY_MAP = new Int2ReferenceArrayMap<>();
	private final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);
	private final Supplier<ApplicationData> dataSupplier;

	/**
	 * @param dataSupplier
	 * 		Supplier to provide application data. Typically a plug for {@link LoaderTask}.
	 * @param options
	 * 		D8/R8 options wrapper.
	 */
	public ConverterTask(@Nonnull Supplier<ApplicationData> dataSupplier, @Nonnull Options options) {
		super(options);
		this.dataSupplier = dataSupplier;
	}

	@Override
	protected boolean run(@Nonnull CompletableFuture<ConverterResult> future) {
		ApplicationData applicationData = dataSupplier.get();
		if (applicationData == null)
			return fail(new NullPointerException("Application data missing"), future);
		DexApplication application = applicationData.getApplication();
		AndroidApp inputApplication = applicationData.getInputApplication();
		AppView<AppInfo> applicationView = applicationData.getApplicationView();

		// Map the method code implementation to JVM bytecode
		List<ProgramMethod> invalidMethods = new ArrayList<>();
		CodeRewriter codeRewriter = new CodeRewriter(applicationView);
		DeadCodeRemover deadCodeRemover = new DeadCodeRemover(applicationView, codeRewriter);
		application.forEachProgramType(type -> {
			DexProgramClass dexClass = application.programDefinitionFor(type);

			// In some configurations, having this be null causes problems.
			// Setting it to any version resolves the problem.
			if (dexClass.getInitialClassFileVersion() == null)
				dexClass.downgradeInitialClassFileVersion(CfVersion.V11);

			// Map method bodies to IR and then to the target output type.
			for (DexEncodedMethod method : dexClass.methods()) {
				// TODO: Handle for non-JVM output
				//  - Dex2Jar
				//  - Jar2Dex
				Code code = method.getCode();
				if (code instanceof DexCode dexCode) {
					ProgramMethod programMethod = method.asProgramMethod(dexClass);
					try {
						IRCode irCode = IRCodeHacking.buildIR(dexCode, programMethod, applicationView, Origin.root());
						CfBuilder builder = new CfBuilder(applicationView, programMethod, irCode, BytecodeMetadataProvider.empty());
						CfCode cfCode = builder.build(deadCodeRemover, EMPTY_TIMING);
						method.setCode(cfCode, EMPTY_ARRAY_MAP);
					} catch (Exception ex) {
						if (options.isReplaceInvalidMethodBodies()) {
							method.setCode(ThrowNullCode.get(), EMPTY_ARRAY_MAP);
							invalidMethods.add(programMethod);
						} else {
							fail(ex, future);
							return;
						}
					}
				}
			}
		});

		// Can be set in the consumer loop, see above.
		if (future.isCompletedExceptionally())
			return false;

		// Convert and store results in app-view.
		try {
			new PrimaryD8L8IRConverter(applicationView, EMPTY_TIMING)
					.convert(applicationView, threadPool);
		} catch (Exception ex) {
			return fail(ex, future);
		}


		// Close any internal archive providers now the application is fully processed.
		try {
			inputApplication.closeInternalArchiveProviders();
		} catch (IOException ex) {
			return fail(ex, future);
		}

		// Output flags & marker
		InternalOptions internalOptions = options.getOptions();
		boolean hasClassResources = applicationView.appInfo().app().getFlags().hasReadProgramClassFromCf();
		boolean hasDexResources = applicationView.appInfo().app().getFlags().hasReadProgramClassFromDex();
		Marker marker = hasClassResources ? internalOptions.getMarker() : null;

		// Handle writing output
		try {
			if (internalOptions.isGeneratingClassFiles()) {
				ClassFileConsumer classFileConsumer = internalOptions.getClassFileConsumer();
				new CfApplicationWriter(applicationView, marker).write(classFileConsumer, inputApplication);
				classFileConsumer.finished(internalOptions.reporter);
			} else {
				ApplicationWriter.create(applicationView, marker).write(threadPool, inputApplication);
			}
		} catch (Exception ex) {
			return fail(ex, future);
		}

		// Cleanup and shut down.
		try {
			inputApplication.signalFinishedToProviders(internalOptions.reporter);
			internalOptions.signalFinishedToConsumers();

			// Stop any remaining threads
			threadPool.shutdownNow();
		} catch (Exception ignored) {
			// We're already done exporting, so it doesn't really matter.
		}

		// Done
		return future.complete(new ConverterResult(invalidMethods));
	}
}
