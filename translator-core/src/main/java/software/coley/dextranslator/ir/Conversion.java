package software.coley.dextranslator.ir;

import com.android.tools.r8.ClassFileConsumer;
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
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import software.coley.dextranslator.model.ApplicationData;
import software.coley.dextranslator.util.ThreadPools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class Conversion {
	private static final Int2ReferenceArrayMap<DebugLocalInfo> EMPTY_ARRAY_MAP = new Int2ReferenceArrayMap<>();
	protected static final Timing EMPTY_TIMING = Timing.empty();

	// TODO: Want to operate off of a snapshot of the application data, so that any
	//  transforms here do not affect the upstream state. Currently its assumed the
	//  passed value is a copy, but we should have stronger guarantees
	public static ConversionResult convert(ApplicationData applicationData,  InternalOptions options, boolean replaceInvalid)
			throws ConversionIRReplacementException, ConversionD8ProcessingException, ConversionExportException {
		DexApplication application = applicationData.getApplication();
		AndroidApp inputApplication = applicationData.inputApplication();
		AppView<AppInfo> applicationView = applicationData.applicationView();

		// Track which methods could not be converted and are replaced (only when the replace flag is set)
		List<ConversionResult.InvalidMethod> invalidMethods = new ArrayList<>();

		// Handle rewriting input code models to the target code model type.
		CodeRewriter codeRewriter = new CodeRewriter(applicationView);
		DeadCodeRemover deadCodeRemover = new DeadCodeRemover(applicationView, codeRewriter);
		for (DexProgramClass dexClass : application.classes()) {
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
					if (options.isGeneratingClassFiles() && code instanceof DexCode dexCode) {
						// Dex --> Java
						IRCode irCode = IRCodeHacking.buildIR(dexCode, programMethod, applicationView, Origin.root());
						CfBuilder builder = new CfBuilder(applicationView, programMethod, irCode, metadataProvider);
						CfCode cfCode = builder.build(deadCodeRemover, EMPTY_TIMING);
						method.setCode(cfCode, EMPTY_ARRAY_MAP);
					} else if (options.isGeneratingDex() && code instanceof CfCode cfCode) {
						// Java --> Dex
						List<CfCode.LocalVariableInfo> variables = cfCode.getLocalVariables();
						CfSourceCode source = new CfSourceCode(cfCode, variables, programMethod, method.getReference(), null, Origin.root(), applicationView);
						IRCode irCode = IRBuilder.create(programMethod, applicationView, source, Origin.root())
								.build(programMethod, new MethodConversionOptions.MutableMethodConversionOptions(options));
						RegisterAllocator registerAllocator = new CfRegisterAllocator(applicationView, irCode, new TypeVerificationHelper(applicationView, irCode));
						DexBuilder builder = new DexBuilder(
								irCode,
								metadataProvider,
								registerAllocator,
								options
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
					if (replaceInvalid) {
						method.setCode(ThrowNullCode.get(), EMPTY_ARRAY_MAP);
						invalidMethods.add(new ConversionResult.InvalidMethod(programMethod, ex));
					} else {
						throw new ConversionIRReplacementException(ex, programMethod, options.isGeneratingClassFiles());
					}
				}
			}
		}

		// Convert and store results in app-view.
		ExecutorService threadPool = ThreadPools.newMaxFixedThreadPool();
		try {
			new PrimaryD8L8IRConverter(applicationView, EMPTY_TIMING)
					.convert(applicationView, threadPool);
		} catch (Exception ex) {
			threadPool.shutdownNow();
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
		} finally {
			threadPool.shutdownNow();
		}

		return new ConversionResult(invalidMethods);
	}
}