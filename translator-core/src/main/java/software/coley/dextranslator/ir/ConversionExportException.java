package software.coley.dextranslator.ir;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;

/**
 * Thrown by {@link Conversion} processing when exporting to the configured output
 * type {@link ClassFileConsumer}/{@link DexIndexedConsumer} encounters an issue ({@link #getCause()}).
 *
 * @author Matt Coley
 */
public class ConversionExportException extends ConversionException {
	/**
	 * @param cause
	 * 		Cause exception.
	 * @param isJvm
	 * 		Flag indicating output type is JVM class files.
	 */
	public ConversionExportException(Exception cause, boolean isJvm) {
		super(cause, "Failed to export to configured output " + (isJvm ? "(JVM)" : "(DEX)"));
	}
}
