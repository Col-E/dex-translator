package software.coley.dextranslator.ir;

/**
 * Base type for conversion errors in {@link Conversion}.
 *
 * @author Matt Coley
 */
public class ConversionException extends Exception {
	/**
	 * @param cause
	 * 		Cause exception.
	 * @param message
	 * 		Exception message.
	 */
	public ConversionException(Throwable cause, String message) {
		super(message, cause);
	}
}
