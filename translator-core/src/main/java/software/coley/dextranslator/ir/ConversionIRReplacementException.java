package software.coley.dextranslator.ir;

import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.ProgramMethod;

import javax.annotation.Nonnull;

/**
 * Thrown by {@link Conversion} processing when converting between {@link CfCode} and {@link DexCode} encounters
 * an issue ({@link #getCause()}).
 *
 * @author Matt Coley
 */
public class ConversionIRReplacementException extends ConversionException {
	private final ProgramMethod targetMethod;

	/**
	 * @param cause
	 * 		Cause exception.
	 * @param targetMethod
	 * 		Method that caused the conversion process to fail.
	 * @param isJvm Flag indicating output type is JVM class files.
	 */
	public ConversionIRReplacementException(@Nonnull Exception cause, @Nonnull ProgramMethod targetMethod, boolean isJvm) {
		super(cause, "Failed to retarget method code to target" + (isJvm ? "(JVM)" : "(DEX)") + " for method: " +
				targetMethod.getHolder().getTypeName() + "." + targetMethod.getName() + targetMethod.getDefinition().descriptor());
		this.targetMethod = targetMethod;
	}

	/**
	 * @return Method that caused the conversion process to fail.
	 */
	@Nonnull
	public ProgramMethod getTargetMethod() {
		return targetMethod;
	}
}
