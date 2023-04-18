package software.coley.dextranslator.ir;

import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;

import javax.annotation.Nonnull;

/**
 * Thrown by {@link Conversion} processing when {@link PrimaryD8L8IRConverter} encounters an issue ({@link #getCause()}).
 *
 * @author Matt Coley
 */
public class ConversionD8ProcessingException extends ConversionException {
	/**
	 * @param cause
	 * 		Cause exception.
	 * @param isJvm
	 * 		Flag indicating output type is JVM class files.
	 */
	public ConversionD8ProcessingException(@Nonnull Exception cause, boolean isJvm) {
		super(cause, "Problem in conversion processor '" + PrimaryD8L8IRConverter.class.getSimpleName() +
				"' when targeting output type: " + (isJvm ? "(JVM)" : "(DEX)"));
	}
}
