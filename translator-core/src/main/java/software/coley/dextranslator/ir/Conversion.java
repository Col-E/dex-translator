package software.coley.dextranslator.ir;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAmender;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ClassFilter;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import software.coley.dextranslator.model.ApplicationData;
import software.coley.dextranslator.resugar.TryCatchResugaring;
import software.coley.dextranslator.util.ThreadPools;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Conversion handling between DEX and JVM bytecode.
 *
 * @author Matt Coley
 */
public class Conversion {
	private static final Int2ReferenceArrayMap<DebugLocalInfo> EMPTY_ARRAY_MAP = new Int2ReferenceArrayMap<>();
	private static final BytecodeMetadataProvider EMPTY_METADATA = BytecodeMetadataProvider.empty();
	protected static final Timing EMPTY_TIMING = Timing.empty();

	/**
	 * @param applicationData
	 * 		Input application model.
	 * @param options
	 * 		Options to handle the conversion with.
	 * @param filter
	 * 		Class filter to apply, used for limiting the visibility to classes within the view.
	 * 		This can be useful when the view is used in a conversion process where only some classes
	 * 		are to be converted, rather than the whole application.
	 * @param replaceInvalid
	 * 		Flag to indicate if invalid method bodies should be replaced with dummy {@code throw} statements.
	 *
	 * @return Result indicating conversion success and which methods got replaced if the replacement flag is set.
	 * The actual conversion output is sent to {@link InternalOptions#programConsumer}.
	 *
	 * @throws ConversionIRReplacementException
	 * 		When replacing {@link CfCode} of input classes fails.
	 * @throws ConversionD8ProcessingException
	 * 		When D8 conversion internals fails.
	 * @throws ConversionExportException
	 * 		When conversion exporting fails.
	 * @see ApplicationData#exportToJvmClassMap()
	 * @see ApplicationData#exportToDexFile()
	 */
	@Nonnull
	public static ConversionResult convert(@Nonnull ApplicationData applicationData,
										   @Nonnull InternalOptions options,
										   @Nonnull ClassFilter filter,
										   boolean replaceInvalid)
			throws ConversionIRReplacementException, ConversionD8ProcessingException, ConversionExportException {
		AndroidApp inputApplication = applicationData.getInputApplication();
		AppView<AppInfo> applicationView = applicationData.createView(options, filter);

		// Run pre-processing operations.
		DesugaredLibraryAmender.run(applicationView);
		SyntheticItems.collectSyntheticInputs(applicationView);

		// Track which methods could not be converted and are replaced (only when the replace flag is set)
		List<ConversionResult.InvalidMethod> invalidMethods = new ArrayList<>();

		// Handle rewriting input code models to the target code model type.
		boolean isJvmTarget = options.isGeneratingClassFiles();
		DeadCodeRemover deadCodeRemover = new DeadCodeRemover(applicationView);
		for (DexProgramClass dexClass : applicationView.appInfo().classes()) {
			// In some configurations, having this be null causes problems.
			// Setting it to any version resolves the problem.
			if (dexClass.getInitialClassFileVersion() == null)
				dexClass.downgradeInitialClassFileVersion(CfVersion.V11);

			// Map method bodies to IR and then to the target output type.
			for (DexEncodedMethod method : dexClass.methods()) {
				Code code = method.getCode();
				ProgramMethod programMethod = method.asProgramMethod(dexClass);
				try {
					// We only need to update the method code bodies if they're in Dalvik form.
					// The D8 converter further below will cover all other cases.
					if (isJvmTarget && code instanceof DexCode) {
						DexCode dexCode = (DexCode) code;

						// Dex --> Java
						IRCode irCode = dexCode.buildIR(programMethod, applicationView, Origin.root());

						// Build CF model.
						CfBuilder builder = new CfBuilder(applicationView, programMethod, irCode, EMPTY_METADATA);
						CfCode cfCode = builder.build(deadCodeRemover, EMPTY_TIMING);

						// Update method.
						method.setCode(cfCode, EMPTY_ARRAY_MAP);
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
					if (replaceInvalid) {
						method.setCode(ThrowNullCode.get(), EMPTY_ARRAY_MAP);
						invalidMethods.add(new ConversionResult.InvalidMethod(programMethod, ex));
					} else {
						throw new ConversionIRReplacementException(ex, programMethod, isJvmTarget);
					}
				}
			}
		}

		// Convert and store results in app-view.
		ExecutorService threadPool = ThreadPools.getMaxFixedThreadPool();
		try {
			new PrimaryD8L8IRConverter(applicationView, EMPTY_TIMING)
					.convert(applicationView, threadPool);

			// Conversion process marks info as obsolete.
			applicationView.appInfo().unsetObsolete();
		} catch (Exception ex) {
			throw new ConversionD8ProcessingException(ex, options.isGeneratingClassFiles());
		}

		// Handle writing output
		try {
			Marker marker = options.getMarker();
			if (options.isGeneratingClassFiles()) {
				ClassFileConsumer classFileConsumer = options.getClassFileConsumer();
				new CfApplicationWriter(applicationView, marker)
						.write(classFileConsumer, inputApplication);
				classFileConsumer.finished(options.reporter);
			} else {
				ApplicationWriter.create(applicationView, marker)
						.write(threadPool, inputApplication);
			}
		} catch (Exception ex) {
			throw new ConversionExportException(ex, options.isGeneratingClassFiles());
		}

		return new ConversionResult(invalidMethods);
	}
}