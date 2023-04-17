package software.coley.dextranslator.command;

import com.android.tools.r8.graph.ProgramMethod;
import software.coley.dextranslator.task.Converter;
import software.coley.dextranslator.task.ConverterResult;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Common base for commands utilizing handling of {@link Converter#run()}.
 *
 * @author Matt Coley
 */
public abstract class AbstractConversionCommand implements Callable<Void> {
	protected void handle(ConverterResult result, Throwable error) {
		if (result != null) {
			List<ConverterResult.InvalidMethod> invalidMethods = result.getInvalidMethods();
			if (!invalidMethods.isEmpty()) {
				System.out.println("Conversion process finished with " + invalidMethods.size() + " invalid methods:");
				for (ConverterResult.InvalidMethod invalidMethod : invalidMethods) {
					ProgramMethod method = invalidMethod.method();
					String reason = invalidMethod.exception().getMessage();
					System.out.println(" - " + method.getHolder().getTypeName() + "." +
							method.getName() + method.getDefinition().descriptor() + " ==> " + reason);
				}
			} else {
				System.out.println("Conversion process finished successfully");
			}
		} else if (error != null) {
			System.out.println("An error occurred in the conversion process:");
			error.printStackTrace();
		}
	}
}
