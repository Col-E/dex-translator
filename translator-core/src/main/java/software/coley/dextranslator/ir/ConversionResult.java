package software.coley.dextranslator.ir;

import com.android.tools.r8.graph.ProgramMethod;
import software.coley.dextranslator.Options;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Summary of {@link Conversion} results.
 *
 * @author Matt Coley
 */
public class ConversionResult {
	private final List<InvalidMethod> invalidMethods;

	/**
	 * @param invalidMethods
	 * 		List of methods that could not be converted.
	 */
	public ConversionResult(@Nonnull List<InvalidMethod> invalidMethods) {
		this.invalidMethods = invalidMethods;
	}

	/**
	 * @return List of methods that could not be converted.
	 *
	 * @see Options#setReplaceInvalidMethodBodies(boolean)
	 */
	@Nonnull
	public List<InvalidMethod> getInvalidMethods() {
		return invalidMethods;
	}

	/**
	 * Outline of problematic methods in conversion.
	 */
	public static class InvalidMethod {
		private final ProgramMethod method;
		private final Exception exception;

		/**
		 * @param method
		 * 		Method definition.
		 * @param exception
		 * 		Exception with reason indicating why the method is invalid.
		 */
		public InvalidMethod(@Nonnull ProgramMethod method, @Nonnull Exception exception) {
			this.method = method;
			this.exception = exception;
		}

		/**
		 * @return Method definition.
		 */
		@Nonnull
		public ProgramMethod getMethod() {
			return method;
		}

		/**
		 * @return Exception with reason indicating why the method is invalid.
		 */
		@Nonnull
		public Exception getException() {
			return exception;
		}
	}
}
