package software.coley.dextranslator.command;

import com.android.tools.r8.graph.ProgramMethod;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.task.Converter;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command to convert a DEX file to a JAR file.
 *
 * @author Matt Coley
 */
@SuppressWarnings("unused")
@Command(name = "d2j",
		description = "Converts a dex file into a jar")
public class Dex2Jar implements Callable<Void> {
	@Parameters(index = "0",
			description = "Path to one or more dex files.",
			arity = "1..*")
	private File[] inputFiles;

	@Parameters(index = "1",
			description = "Path to jar file to write to.",
			arity = "1")
	private File outputFile;

	@Option(names = {"-l", "--lenient"},
			description = "Flag to enable options that allow more leniency in the conversion process. " +
					"Some input validation will be skipped.")
	private boolean lenient;

	@Option(names = {"-f", "--force"},
			description = "Flag to enable force emitting output, even if some method bodies are invalid. " +
					"Invalid methods will be replaced with no-op behavior.")
	private boolean replaceInvalid;

	@Override
	public Void call() throws Exception {
		Inputs inputs = new Inputs();
		for (File inputFile : inputFiles)
			inputs.addDex(inputFile.toPath());

		Options options = new Options()
				.setReplaceInvalidMethodBodies(replaceInvalid)
				.setLenient(lenient)
				.setJvmArchiveOutput(outputFile.toPath(), true);

		new Converter()
				.setInputs(inputs)
				.setOptions(options)
				.run()
				.whenComplete((result, error) -> {
					if (result != null) {
						List<ProgramMethod> invalidMethods = result.getInvalidMethods();
						if (!invalidMethods.isEmpty()) {
							System.out.println("Conversion process finished with " + invalidMethods.size() + " invalid methods:");
							for (ProgramMethod invalidMethod : invalidMethods) {
								System.out.println(" - " + invalidMethod.getHolder().getTypeName() + "." +
										invalidMethod.getName() + invalidMethod.getDefinition().descriptor());
							}
						} else {
							System.out.println("Conversion process finished successfully");
						}
					} else if (error != null) {
						System.out.println("An error occurred in the conversion process:");
						error.printStackTrace();
					}
				});
		return null;
	}
}
