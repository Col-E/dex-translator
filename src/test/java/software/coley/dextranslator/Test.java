package software.coley.dextranslator;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.code.ValueTypeConstraint;
import com.android.tools.r8.ir.conversion.ArrayFilledDataPayloadResolver;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexSourceCode;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
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
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Test {
	private static final Timing EMPTY_TIMING = Timing.empty();
	private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);
	private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

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


		// Map the method code implementation to JVM bytecode
		Int2ReferenceArrayMap<DebugLocalInfo> EMPTY = new Int2ReferenceArrayMap<>();
		CodeRewriter codeRewriter = new CodeRewriter(appView);
		DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView, codeRewriter);
		dexApplication.forEachProgramType(type -> {
			DexProgramClass dexClass = dexApplication.programDefinitionFor(type);
			for (DexEncodedMethod method : dexClass.methods()) {
				Code code = method.getCode();
				if (code instanceof DexCode dexCode) {
					ProgramMethod programMethod = method.asProgramMethod(dexClass);
					IRCode irCode = buildIR(dexCode, programMethod, appView, Origin.root());

					CfBuilder builder = new CfBuilder(appView, programMethod, irCode, BytecodeMetadataProvider.empty());
					CfCode cfCode = builder.build(deadCodeRemover, EMPTY_TIMING);
					method.setCode(cfCode, EMPTY);
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
		THREAD_POOL.shutdown();
		THREAD_POOL.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
	}

	// Evil hacks start here!
	private static IRCode buildIR(DexCode dexCode, ProgramMethod method, AppView<?> appView, Origin origin) {
		return buildIR(dexCode, method, appView, origin, new MethodConversionOptions.MutableMethodConversionOptions(appView.options()));
	}

	private static IRCode buildIR(DexCode dexCode, ProgramMethod method, AppView<?> appView, Origin origin, MethodConversionOptions.MutableMethodConversionOptions conversionOptions) {
		DexSourceCode source = new HackyDexSourceCode(dexCode, method, appView.graphLens().getOriginalMethodSignature(method.getReference()), null, appView.dexItemFactory());
		return IRBuilder.create(method, appView, source, origin).build(method, conversionOptions);
	}

	private static final class HackyDexSourceCode extends DexSourceCode {
		private static final Unsafe UNSAFE;
		private static final long ARRAY_PAYLOAD_RESOLVER;
		private static final MethodHandle ADD_INSTRUCTION;
		private static final MethodHandle UPDATE_CURRENT_CATCH_HANDLERS;
		private static final MethodHandle UPDATE_DEBUG_POSITION;
		private static final MethodHandle CURRENT_INSTRUCTION;

		static {
			try {
				Field f = Unsafe.class.getDeclaredField("theUnsafe");
				f.setAccessible(true);
				Unsafe u = (Unsafe) f.get(null);
				UNSAFE = u;
				ARRAY_PAYLOAD_RESOLVER = u.objectFieldOffset(DexSourceCode.class.getDeclaredField("arrayFilledDataPayloadResolver"));
				MethodHandles.Lookup myLookup = MethodHandles.lookup();
				MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(IRBuilder.class, myLookup);
				ADD_INSTRUCTION = lookup
						.findVirtual(IRBuilder.class, "addInstruction", MethodType.methodType(void.class, Instruction.class));
				lookup = MethodHandles.privateLookupIn(DexSourceCode.class, myLookup);
				UPDATE_CURRENT_CATCH_HANDLERS = lookup
						.findVirtual(DexSourceCode.class, "updateCurrentCatchHandlers", MethodType.methodType(void.class, int.class, DexItemFactory.class));
				UPDATE_DEBUG_POSITION = lookup
						.findVirtual(DexSourceCode.class, "updateDebugPosition", MethodType.methodType(void.class, int.class, IRBuilder.class));
				CURRENT_INSTRUCTION = lookup
						.findSetter(DexSourceCode.class, "currentDexInstruction", DexInstruction.class);
			} catch (ReflectiveOperationException ex) {
				throw new ExceptionInInitializerError(ex);
			}
		}

		private final DexCode code;
		private final DexItemFactory factory;
		private IRBuilder irBuilder;

		HackyDexSourceCode(DexCode code, ProgramMethod method, DexMethod originalMethod, Position callerPosition, DexItemFactory factory) {
			super(code, method, originalMethod, callerPosition, factory);
			this.code = code;
			this.factory = factory;
		}

		@Override
		public void buildPrelude(IRBuilder builder) {
			irBuilder = builder;
			super.buildPrelude(builder);
		}

		@Override
		public void buildInstruction(IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
			DexInstruction instruction = code.instructions[instructionIndex];
			if (instruction instanceof DexFilledNewArray newArray) {
				DexType type = newArray.getType();
				String descriptor = type.descriptor.toString();
				ValueTypeConstraint constraint =
						ValueTypeConstraint.fromTypeDescriptorChar(descriptor.charAt(1));
				int argumentCount = newArray.A;
				int wordSize = constraint.requiredRegisters();
				List<Value> arguments = new ArrayList<>(argumentCount / wordSize);
				int[] argumentRegisters = {
						newArray.C,
						newArray.D,
						newArray.E,
						newArray.F,
						newArray.G
				};
				for (int registerIndex = 0; registerIndex < argumentCount; ) {
					arguments.add(builder.readRegister(argumentRegisters[registerIndex], constraint));
					registerIndex += wordSize;
				}

				InvokeNewArray invokeNewArray = new InvokeNewArray(type, null, arguments) {
					@Override
					public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
						helper.loadInValues(this, it);
						helper.storeOutValue(this, it);
					}

					@Override
					public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
						return type;
					}

					@Override
					public void buildCf(CfBuilder builder) {
						builder.add(
								new CfConstNumber(argumentCount, ValueType.INT),
								new CfNewArray(type)
						);
						DexType elementType = factory.createType(descriptor.substring(0, descriptor.length() - 2));
						MemberType memberType = MemberType.fromDexType(elementType);
						int index = 0;
						for (int registerIndex = 0; registerIndex < argumentCount; ) {
							// Re-arrange the stack from:
							// [element, array]
							// to [array, array, index, element]

							// [element, array]
							builder.add(
									new CfStackInstruction(CfStackInstruction.Opcode.Dup)
							);  // [element, array, array]
							swap(builder, 2, wordSize); // [array, array, element]
							builder.add(new CfConstNumber(index++, ValueType.INT)); // [array, array, element, index]
							swap(builder, 1, wordSize); // [array, array, index, element]
							builder.add(new CfArrayStore(memberType)); // [element?, array]
							registerIndex += wordSize;
						}
					}
				};
				try {
					UPDATE_CURRENT_CATCH_HANDLERS.invokeExact((DexSourceCode) this, instructionIndex, factory);
					UPDATE_DEBUG_POSITION.invokeExact((DexSourceCode) this, instructionIndex, builder);
					CURRENT_INSTRUCTION.invokeExact((DexSourceCode) this, (DexInstruction) newArray);
					ADD_INSTRUCTION.invokeExact(builder, (Instruction) invokeNewArray);
				} catch (Throwable t) {
					throw new RuntimeException(t);
				}
				return;
			}
			super.buildInstruction(builder, instructionIndex, firstBlockInstruction);
		}

		@Override
		public void resolveAndBuildNewArrayFilledData(int arrayRef, int payloadOffset, IRBuilder builder) {
			ArrayFilledDataPayloadResolver arrayFilledDataPayloadResolver =
					(ArrayFilledDataPayloadResolver) UNSAFE.getObject(this, ARRAY_PAYLOAD_RESOLVER);
			int width = arrayFilledDataPayloadResolver.getElementWidth(payloadOffset);
			long size = arrayFilledDataPayloadResolver.getSize(payloadOffset);
			short[] data = arrayFilledDataPayloadResolver.getData(payloadOffset);
			Value src = irBuilder.readRegister(arrayRef, ValueTypeConstraint.OBJECT);
			MemberType type = MemberType.valueOf(src.getType().asArrayType().getMemberType().toString());
			NewArrayFilledData instruction = new NewArrayFilledData(src, width, size, data) {

				@Override
				public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
					helper.loadInValues(this, it);
				}

				@Override
				public void buildCf(CfBuilder builder) {
					short[] arrayData = data;
					switch (element_width) {
						case 1 -> {
							for (int i = 0; i < size; i++) {
								builder.add(
										new CfStackInstruction(CfStackInstruction.Opcode.Dup),
										new CfConstNumber(i, ValueType.INT),
										new CfConstNumber(UNSAFE.getByte(arrayData, Unsafe.ARRAY_SHORT_BASE_OFFSET + i), ValueType.INT),
										new CfArrayStore(type)
								);
							}
						}
						case 2 -> {
							for (int i = 0; i < size; i++) {
								builder.add(
										new CfStackInstruction(CfStackInstruction.Opcode.Dup),
										new CfConstNumber(i, ValueType.INT),
										new CfConstNumber(reverseShort(UNSAFE.getShort(arrayData, Unsafe.ARRAY_SHORT_BASE_OFFSET + i * 2L)), ValueType.INT),
										new CfArrayStore(type)
								);
							}
						}
						case 4 -> {
							for (int i = 0; i < size; i++) {
								builder.add(
										new CfStackInstruction(CfStackInstruction.Opcode.Dup),
										new CfConstNumber(i, ValueType.INT),
										new CfConstNumber(reverseInt(UNSAFE.getInt(arrayData, Unsafe.ARRAY_SHORT_BASE_OFFSET + i * 4L)), ValueType.INT),
										new CfArrayStore(type)
								);
							}
						}
						case 8 -> {
							for (int i = 0; i < size; i++) {
								builder.add(
										new CfStackInstruction(CfStackInstruction.Opcode.Dup),
										new CfConstNumber(i, ValueType.INT),
										new CfConstNumber(reverseLong(UNSAFE.getLong(arrayData, Unsafe.ARRAY_SHORT_BASE_OFFSET + i * 8L)), ValueType.LONG),
										new CfArrayStore(type)
								);
							}
						}
					}
				}
			};
			try {
				ADD_INSTRUCTION.invokeExact(irBuilder, (Instruction) instruction);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}
	}

	static void swap(CfBuilder builder, int right, int left) {
		if (right == 1) {
			if (left == 1) {
				builder.add(new CfStackInstruction(CfStackInstruction.Opcode.Swap));
				return;
			} else if (left == 2) {
				builder.add(
						new CfStackInstruction(CfStackInstruction.Opcode.DupX2),
						new CfStackInstruction(CfStackInstruction.Opcode.Pop)
				);
				return;
			}
		} else if (right == 2) {
			if (left == 1) {
				builder.add(
						new CfStackInstruction(CfStackInstruction.Opcode.Dup2X1),
						new CfStackInstruction(CfStackInstruction.Opcode.Pop2)
				);
				return;
			} else if (left == 2) {
				builder.add(
						new CfStackInstruction(CfStackInstruction.Opcode.Dup2X2),
						new CfStackInstruction(CfStackInstruction.Opcode.Pop2)
				);
				return;
			}
		}
		throw new IllegalStateException("Not implemented");
	}

	static short reverseShort(short s) {
		if (LITTLE_ENDIAN) return Short.reverseBytes(s);
		return s;
	}

	static int reverseInt(int i) {
		if (LITTLE_ENDIAN) return Integer.reverseBytes(i);
		return i;
	}

	static long reverseLong(long l) {
		if (LITTLE_ENDIAN) return Long.reverseBytes(l);
		return l;
	}
}
