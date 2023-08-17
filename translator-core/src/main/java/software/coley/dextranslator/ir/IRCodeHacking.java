package software.coley.dextranslator.ir;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexFilledNewArrayRange;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.ir.code.*;
import com.android.tools.r8.ir.conversion.*;
import com.android.tools.r8.origin.Origin;
import software.coley.dextranslator.util.UnsafeUtil;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for build {@link IRCode} models that support full translation back into JVM bytecode.
 *
 * @author xxDark
 */
public class IRCodeHacking {
	private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

	public static IRCode buildIR(@Nonnull DexCode dexCode,
								 @Nonnull ProgramMethod method,
								 @Nonnull AppView<?> appView,
								 @Nonnull Origin origin) {
		MethodConversionOptions.MutableMethodConversionOptions conversionOptions
				= new MethodConversionOptions.MutableMethodConversionOptions(MethodConversionOptions.Target.CF, appView.options());
		return buildIR(dexCode, method, appView, origin, conversionOptions);
	}

	public static IRCode buildIR(@Nonnull DexCode dexCode,
								 @Nonnull ProgramMethod method,
								 @Nonnull AppView<?> appView,
								 @Nonnull Origin origin,
								 @Nonnull MethodConversionOptions.MutableMethodConversionOptions conversionOptions) {
		DexMethod originalMethodSignature = appView.graphLens().getOriginalMethodSignature(method.getReference());
		DexSourceCode source = new HackyDexSourceCode(dexCode, method, originalMethodSignature, null, appView.dexItemFactory());
		return IRBuilder.create(method, appView, source, origin)
				.build(method, conversionOptions);
	}

	static void swap(@Nonnull CfBuilder builder, int right, int left) {
		if (right == 1) {
			if (left == 1) {
				builder.add(CfStackInstruction.SWAP);
				return;
			} else if (left == 2) {
				builder.add(
						CfStackInstruction.DUP_X2,
						CfStackInstruction.POP
				);
				return;
			}
		} else if (right == 2) {
			if (left == 1) {
				builder.add(
						CfStackInstruction.DUP2_X1,
						CfStackInstruction.POP2
				);
				return;
			} else if (left == 2) {
				builder.add(
						CfStackInstruction.DUP2_X2,
						CfStackInstruction.POP2
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

	private static final class HackyDexSourceCode extends DexSourceCode {
		private static final long ARRAY_PAYLOAD_RESOLVER;
		private static final MethodHandle ADD_INSTRUCTION;
		private static final MethodHandle UPDATE_CURRENT_CATCH_HANDLERS;
		private static final MethodHandle UPDATE_DEBUG_POSITION;
		private static final MethodHandle CURRENT_INSTRUCTION;

		static {
			try {
				Unsafe unsafe = UnsafeUtil.getUnsafe();
				ARRAY_PAYLOAD_RESOLVER = unsafe.objectFieldOffset(DexSourceCode.class.getDeclaredField("arrayFilledDataPayloadResolver"));
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

		HackyDexSourceCode(@Nonnull DexCode code,
						   @Nonnull ProgramMethod method,
						   @Nonnull DexMethod originalMethod,
						   @Nullable Position callerPosition,
						   @Nonnull DexItemFactory factory) {
			super(code, method, originalMethod, callerPosition, factory);
			this.code = code;
			this.factory = factory;
		}

		@Override
		public void buildInstruction(@Nonnull IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
			DexInstruction instruction = code.instructions[instructionIndex];
			if (instruction instanceof DexFilledNewArray) {
				DexFilledNewArray newArray = (DexFilledNewArray) instruction;
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

				// 'InvokeNewArray' renamed to 'NewArrayFilled' in R8 after 8.3.0
				NewArrayFilled invokeNewArray = new NewArrayFilled(type, null, arguments) {
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
								CfConstNumber.constNumber(argumentCount, ValueType.INT),
								new CfNewArray(type)
						);
						MemberType memberType = getArrayMemberType(descriptor);
						int index = 0;
						for (int registerIndex = 0; registerIndex < argumentCount; ) {
							// Re-arrange the stack from:
							// [element, array]
							// to [array, array, index, element]

							// [element, array]
							builder.add(CfStackInstruction.DUP); // [element, array, array]
							swap(builder, 2, wordSize); // [array, array, element]
							builder.add(CfConstNumber.constNumber(index++, ValueType.INT)); // [array, array, element, index]
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
			} else if (instruction instanceof DexFilledNewArrayRange) {
				DexFilledNewArrayRange newArray = (DexFilledNewArrayRange) instruction;
				DexType type = newArray.getType();
				String descriptor = type.descriptor.toString();
				ValueTypeConstraint constraint =
						ValueTypeConstraint.fromTypeDescriptorChar(descriptor.charAt(1));
				int argumentCount = newArray.AA;
				int wordSize = constraint.requiredRegisters();
				List<Value> arguments = new ArrayList<>(argumentCount / wordSize);
				int registerStart = newArray.CCCC;
				int registerEnd = registerStart + argumentCount;
				for (int registerIndex = registerStart; registerIndex < registerEnd; ) {
					arguments.add(builder.readRegister(registerIndex, constraint));
					registerIndex += wordSize;
				}

				NewArrayFilled invokeNewArray = new NewArrayFilled(type, null, arguments) {
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
								CfConstNumber.constNumber(argumentCount, ValueType.INT),
								new CfNewArray(type)
						);
						MemberType memberType = getArrayMemberType(descriptor);
						int index = 0;
						for (int registerIndex = 0; registerIndex < argumentCount; ) {
							// Re-arrange the stack from:
							// [element, array]
							// to [array, array, index, element]

							// [element, array]
							builder.add(CfStackInstruction.DUP);  // [element, array, array]
							swap(builder, 2, wordSize); // [array, array, element]
							builder.add(CfConstNumber.constNumber(index++, ValueType.INT)); // [array, array, element, index]
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
		public void resolveAndBuildNewArrayFilledData(int arrayRef, int payloadOffset, @Nonnull IRBuilder builder) {
			Unsafe unsafe = UnsafeUtil.getUnsafe();
			ArrayFilledDataPayloadResolver arrayFilledDataPayloadResolver =
					(ArrayFilledDataPayloadResolver) unsafe.getObject(this, ARRAY_PAYLOAD_RESOLVER);
			int width = arrayFilledDataPayloadResolver.getElementWidth(payloadOffset);
			long size = arrayFilledDataPayloadResolver.getSize(payloadOffset);
			short[] data = arrayFilledDataPayloadResolver.getData(payloadOffset);
			Value src = builder.readRegister(arrayRef, ValueTypeConstraint.OBJECT);
			String typeName = src.getType().asArrayType().getMemberType().toString();
			if (typeName.equals("BYTE") || typeName.equals("BOOLEAN"))
				typeName = "BOOLEAN_OR_BYTE"; // R8 conventions can be weird sometimes...
			MemberType type = MemberType.valueOf(typeName);
			NewArrayFilledData instruction = new NewArrayFilledData(src, width, size, data) {

				@Override
				public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
					helper.loadInValues(this, it);
				}

				@Override
				public void buildCf(CfBuilder builder) {
					Unsafe unsafe = UnsafeUtil.getUnsafe();
					short[] arrayData = data;
					switch (element_width) {
						case 1:
							for (int i = 0; i < size; i++) {
								builder.add(
										CfStackInstruction.DUP,
										CfConstNumber.constNumber(i, ValueType.INT),
										CfConstNumber.constNumber(unsafe.getByte(arrayData, Unsafe.ARRAY_SHORT_BASE_OFFSET + i), ValueType.INT),
										new CfArrayStore(type)
								);
							}
							break;
						case 2:
							for (int i = 0; i < size; i++) {
								builder.add(
										CfStackInstruction.DUP,
										CfConstNumber.constNumber(i, ValueType.INT),
										CfConstNumber.constNumber(reverseShort(unsafe.getShort(arrayData, Unsafe.ARRAY_SHORT_BASE_OFFSET + i * 2L)), ValueType.INT),
										new CfArrayStore(type)
								);
							}
							break;
						case 4:
							for (int i = 0; i < size; i++) {
								builder.add(
										CfStackInstruction.DUP,
										CfConstNumber.constNumber(i, ValueType.INT),
										CfConstNumber.constNumber(reverseInt(unsafe.getInt(arrayData, Unsafe.ARRAY_SHORT_BASE_OFFSET + i * 4L)), ValueType.INT),
										new CfArrayStore(type)
								);
							}
							break;
						case 8:
							for (int i = 0; i < size; i++) {
								builder.add(
										CfStackInstruction.DUP,
										CfConstNumber.constNumber(i, ValueType.INT),
										CfConstNumber.constNumber(reverseLong(unsafe.getLong(arrayData, Unsafe.ARRAY_SHORT_BASE_OFFSET + i * 8L)), ValueType.LONG),
										new CfArrayStore(type)
								);
							}
							break;
					}
				}
			};
			try {
				ADD_INSTRUCTION.invokeExact(builder, (Instruction) instruction);
			} catch (Throwable t) {
				throw new RuntimeException(t);
			}
		}

		private MemberType getArrayMemberType(String arrayDescriptor) {
			int lastDim = arrayDescriptor.lastIndexOf('[');
			char next = arrayDescriptor.charAt(lastDim + 1);
			return MemberType.fromTypeDescriptorChar(next);
		}
	}
}
