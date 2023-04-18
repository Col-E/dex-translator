package software.coley.dextranslator.ir;

import com.android.tools.r8.graph.ProgramMethod;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.task.ConverterTask;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Summary of {@link ConverterTask} results.
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
	 * @param method
	 * 		Method definition.
	 * @param exception
	 * 		Exception with reason indicating why the method is invalid.
	 */
	public record InvalidMethod(@Nonnull ProgramMethod method, @Nonnull Exception exception) {
	}
}
