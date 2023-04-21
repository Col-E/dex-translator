package software.coley.dextranslator.command;

import com.android.tools.r8.graph.ProgramMethod;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.task.Converter;
import software.coley.dextranslator.ir.ConversionResult;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Common base for commands utilizing handling of {@link Converter#run()}.
 *
 * @author Matt Coley
 */
public abstract class AbstractConversionCommand implements Callable<Void> {
	protected void handle(ConversionResult result, Throwable error) {
		if (result != null) {
			List<ConversionResult.InvalidMethod> invalidMethods = result.getInvalidMethods();
			if (!invalidMethods.isEmpty()) {
				System.out.println("Conversion process finished with " + invalidMethods.size() + " invalid methods:");
				for (ConversionResult.InvalidMethod invalidMethod : invalidMethods) {
					ProgramMethod method = invalidMethod.getMethod();
					String reason = invalidMethod.getException().getMessage();
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

	/**
	 * @return Files to plug into {@link Inputs}.
	 */
	public abstract File[] getInputFiles();

	/**
	 * @param inputFiles
	 * 		Files to plug into {@link Inputs}.
	 */
	public abstract void setInputFiles(File[] inputFiles);

	/**
	 * @return File path to write output to.
	 */
	public abstract File getOutputFile();

	/**
	 * @param outputFile
	 * 		File path to write output to.
	 */
	public abstract void setOutputFile(File outputFile);

	/**
	 * @return Value of {@link Options#setLenient(boolean)}.
	 */
	public abstract boolean isLenient();

	/**
	 * @param lenient
	 * 		Value of {@link Options#setLenient(boolean)}.
	 */
	public abstract void setLenient(boolean lenient);

	/**
	 * @return Value of {@link Options#setReplaceInvalidMethodBodies(boolean)}.
	 */
	public abstract boolean isReplaceInvalid();

	/**
	 * @param replaceInvalid
	 * 		Value of {@link Options#setReplaceInvalidMethodBodies(boolean)}.
	 */
	public abstract void setReplaceInvalid(boolean replaceInvalid);
}
