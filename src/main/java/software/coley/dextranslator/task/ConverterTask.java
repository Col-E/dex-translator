package software.coley.dextranslator.task;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.cf.CfRegisterAllocator;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.*;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.ir.IRCodeHacking;
import software.coley.dextranslator.util.ThreadPools;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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
	private final ExecutorService threadPool = ThreadPools.newMaxFixedThreadPool();
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
		AppView<? extends AppInfo> applicationView = applicationData.getApplicationView();

		// Map the method code implementation to JVM bytecode
		List<ConverterResult.InvalidMethod> invalidMethods = new ArrayList<>();
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
				Code code = method.getCode();
				ProgramMethod programMethod = method.asProgramMethod(dexClass);
				try {
					BytecodeMetadataProvider metadataProvider = BytecodeMetadataProvider.empty();
					if (options.isJvmOutput() && code instanceof DexCode dexCode) {
						// Dex --> Java
						IRCode irCode = IRCodeHacking.buildIR(dexCode, programMethod, applicationView, Origin.root());
						CfBuilder builder = new CfBuilder(applicationView, programMethod, irCode, metadataProvider);
						CfCode cfCode = builder.build(deadCodeRemover, EMPTY_TIMING);
						method.setCode(cfCode, EMPTY_ARRAY_MAP);
					} else if (options.isDexOutput() && code instanceof CfCode cfCode) {
						// Java --> Dex
						List<CfCode.LocalVariableInfo> variables = cfCode.getLocalVariables();
						CfSourceCode source = new CfSourceCode(cfCode, variables, programMethod, method.getReference(), null, Origin.root(), applicationView);
						IRCode irCode = IRBuilder.create(programMethod, applicationView, source, Origin.root())
								.build(programMethod, new MethodConversionOptions.MutableMethodConversionOptions(options.getInternalOptions()));
						RegisterAllocator registerAllocator = new CfRegisterAllocator(applicationView, irCode, new TypeVerificationHelper(applicationView, irCode));
						DexBuilder builder = new DexBuilder(
								irCode,
								metadataProvider,
								registerAllocator,
								options.getInternalOptions()
						);
						DexCode dexCode = builder.build();
						method.setCode(dexCode, EMPTY_ARRAY_MAP);
					}
				} catch (Exception ex) {
					// Generic error thrown by R8 when something in IR fails.
					// It has no message, so we'll wrap it with something simple.
					if (ex instanceof Unreachable) {
						StackTraceElement top = ex.getStackTrace()[0];
						String topName = top.getClassName().substring(top.getClassName().lastIndexOf('.') + 1);
						String topMethod = top.getMethodName();
						String lineSuffix = top.getLineNumber() > 0 ? " @" + top.getLineNumber() : "";
						// noinspection AssignmentToCatchBlockParameter
						ex = new IllegalStateException("Unsupported conversion in: " +
								topName + "#" + topMethod + lineSuffix, ex);
					}

					// Depending on the options, we can either:
					//  - Replace the method with no-op and continue
					//  - Fail fast and return
					if (options.isReplaceInvalidMethodBodies()) {
						method.setCode(ThrowNullCode.get(), EMPTY_ARRAY_MAP);
						invalidMethods.add(new ConverterResult.InvalidMethod(programMethod, ex));
					} else {
						fail(ex, future);
						return;
					}
				}
			}
		});

		// Can be set in the consumer loop, see above.
		if (future.isCompletedExceptionally())
			return false;

		// Convert and store results in app-view.
		try {
			// TODO: When getting proper R8 support, use 'PrimaryR8IRConverter'
			//  - if (options.isUseR8()) ... else ...
			@SuppressWarnings("unchecked")
			AppView<AppInfo> castedView = (AppView<AppInfo>) applicationView;
			new PrimaryD8L8IRConverter(castedView, EMPTY_TIMING)
					.convert(castedView, threadPool);
		} catch (Exception ex) {
			return fail(ex, future);
		}

		// Output flags & marker
		InternalOptions internalOptions = options.getInternalOptions();
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
			threadPool.shutdownNow();
		}

		// Done
		return future.complete(new ConverterResult(invalidMethods));
	}

	@Override
	protected boolean fail(@Nonnull Exception ex, @Nonnull CompletableFuture<?> future) {
		threadPool.shutdownNow();
		return super.fail(ex, future);
	}
}
