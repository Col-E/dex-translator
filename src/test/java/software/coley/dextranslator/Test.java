package software.coley.dextranslator;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.type.IntTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.*;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Test {
	private static final Timing EMPTY_TIMING = Timing.empty();
	private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);

	public static void main(String[] args) throws Exception {
		Path path = Paths.get(Test.class.getResource("/classes2.dex").toURI());
		byte[] content = Files.readAllBytes(path);

		// Configure options
		InternalOptions options = new InternalOptions();
		new DexIndexedConsumer.ArchiveConsumer(Paths.get("out.dex"));
		options.rewriteArrayOptions().minSizeForFilledNewArray = 255;
		options.rewriteArrayOptions().minSizeForFilledArrayData = 255;
		options.programConsumer = new ClassFileConsumer.ArchiveConsumer(Paths.get("out.jar"), true);
		//options.programConsumer = new DexIndexedConsumer.ArchiveConsumer(Paths.get("out.dex"));
		options.debug = true;
		options.desugarState = InternalOptions.DesugarState.OFF;

		// Create input model
		AndroidApp inputApp = AndroidApp.builder()
				.addDexProgramData(content, Origin.root())
				.build();
		ApplicationReader appReader = new ApplicationReader(inputApp, options, EMPTY_TIMING);
		LazyLoadedDexApplication dexApplication = appReader.read();


		// Create app-view of dex application
		SyntheticItems.GlobalSyntheticsStrategy syntheticsStrategy = options.isGeneratingDexIndexed() ?
				SyntheticItems.GlobalSyntheticsStrategy.forSingleOutputMode() :
				SyntheticItems.GlobalSyntheticsStrategy.forPerFileMode();
		MainDexInfo mainDexInfo = appReader.readMainDexClasses(dexApplication);
		AppInfo appInfo = AppInfo.createInitialAppInfo(dexApplication, syntheticsStrategy, mainDexInfo);
		AppView<AppInfo> appView = AppView.createForD8(appInfo);


		DexItemFactory dexItemFactory = new DexItemFactory();

		// Map the method code implementation to JVM bytecode
		Int2ReferenceArrayMap<DebugLocalInfo> EMPTY = new Int2ReferenceArrayMap<>();
		CodeRewriter codeRewriter = new CodeRewriter(appView);
		DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView, codeRewriter);
		dexApplication.forEachProgramType(type -> {
			DexProgramClass dexClass = dexApplication.programDefinitionFor(type);
			for (DexEncodedMethod method : dexClass.methods()) {
				Code code = method.getCode();
				if (code instanceof DexCode dexCode) {
					try {
						ProgramMethod programMethod = method.asProgramMethod(dexClass);
						IRCode irCode = dexCode.buildIR(programMethod, appView, Origin.root());



						BytecodeMetadataProvider.Builder metaBuilder = BytecodeMetadataProvider.builder();
						InstructionListIterator instructionIterator = irCode.instructionListIterator();
						while (instructionIterator.hasNext()) {
							Instruction instruction = instructionIterator.next();

							Position position = instruction.getPosition();
							Consumer<Instruction> adder = i -> {
								instructionIterator.add(i);
								i.setPosition(position);
							};


							/*
							     const-string v2, "enUS"
    						     const-string v3, "en_US"
    						     const-string v4, "en-US"
    						     filled-new-array {v2, v3, v4}, [Ljava/lang/String;  <----- need to replace
							 */

							if (instruction instanceof NewArrayFilledData newArrayFilled) {
								/*
								instructionIterator.remove();

								long size = newArrayFilled.size;
								int dataSize = newArrayFilled.data.length;

								DexType arrayType = dexItemFactory.shortArrayType;
								MemberType memberType = MemberType.fromDexType(arrayType);


								IntTypeElement intTypeElement = TypeElement.getInt();
								ConstNumber constSize = new ConstNumber(new Value(irCode.valueNumberGenerator.next(), intTypeElement, null), size);
								NewArrayEmpty newArray = new NewArrayEmpty(null, null, arrayType);
								adder.accept(constSize);
								adder.accept(newArray);


								// DUP
								// INDEX
								// VALUE
								// PUT
								TypeVerificationHelper.TypeInfo stackTypeInfo = typeHelper.getTypeInfo(newArray.outValue());
								StackValue stackValue = StackValue.create(stackTypeInfo, 0, appView);
								if (size == dataSize) {
									for (int i = 0; i < newArrayFilled.data.length; i++) {
										int v = newArrayFilled.data[i];
										adder.accept(new Dup(stackValue, stackValue, null));
										adder.accept(new ConstNumber(new Value(irCode.valueNumberGenerator.next(), intTypeElement, null), i));
										adder.accept(new ConstNumber(new Value(irCode.valueNumberGenerator.next(), intTypeElement, null), v));
										adder.accept(new ArrayPut(memberType, null, null, null));
									}
								} else if (size == dataSize / 2) {
									for (int i = 0; i < newArrayFilled.data.length; i += 2) {
										int v = newArrayFilled.data[i + 1] | newArrayFilled.data[i] << 8;
										adder.accept(new Dup(stackValue, stackValue, null));
										adder.accept(new ConstNumber(new Value(irCode.valueNumberGenerator.next(), intTypeElement, null), i));
										adder.accept(new ConstNumber(new Value(irCode.valueNumberGenerator.next(), intTypeElement, null), v));
										adder.accept(new ArrayPut(memberType, null, null, null));
									}
								} else {
									// TODO: Unsure how to determine content type of array
									System.err.println("Unequal array size");
								}*/
							} else if (instruction instanceof InvokeNewArray invokeNewArray) {
								/*
								instructionIterator.remove();

								int size = invokeNewArray.size();
								DexType arrayType = invokeNewArray.getArrayType();

								IntTypeElement intTypeElement = TypeElement.getInt();
								ConstNumber constSize = new ConstNumber(new Value(irCode.valueNumberGenerator.next(), intTypeElement, null), size);
								NewArrayEmpty newArray = new NewArrayEmpty(null, null, arrayType);
								adder.accept(constSize);
								adder.accept(newArray);
								*/
								// TODO: Move in-values to out-value somehow
							}
						}

						BytecodeMetadataProvider metadataProvider = metaBuilder.build();
						CfBuilder builder = new CfBuilder(appView, programMethod, irCode, metadataProvider);
						CfCode cfCode = builder.build(deadCodeRemover, EMPTY_TIMING);
						method.setCode(cfCode, EMPTY);
					} catch (Throwable ex) {
						System.err.println(" >>>>>>>>>>>> " + dexClass.getTypeName() + " . " + method.getName() + method.descriptor());
						ex.printStackTrace();

						// TODO: There are some edge cases with arrays, but most stuff works great
						method.setCode(ThrowNullCode.get(), EMPTY);
					}
				}
			}
		});

		// Convert and store results in app-view
		new PrimaryD8L8IRConverter(appView, EMPTY_TIMING)
				.convert(appView, THREAD_POOL);


		// Close any internal archive providers now the application is fully processed.
		inputApp.closeInternalArchiveProviders();

		// Output flags & marker
		boolean hasClassResources = appView.appInfo().app().getFlags().hasReadProgramClassFromCf();
		boolean hasDexResources = appView.appInfo().app().getFlags().hasReadProgramClassFromDex();
		Marker marker = hasClassResources ? options.getMarker() : null;

		// Handle writing output
		if (options.isGeneratingClassFiles()) {
			new CfApplicationWriter(appView, marker)
					.write(options.getClassFileConsumer(), inputApp);
			options.getClassFileConsumer().finished(options.reporter);
		} else {
			ApplicationWriter.create(appView, marker)
					.write(THREAD_POOL, inputApp);
		}

		// Cleanup and shut down.
		inputApp.signalFinishedToProviders(options.reporter);
		options.signalFinishedToConsumers();
		THREAD_POOL.shutdownNow();
	}
}
