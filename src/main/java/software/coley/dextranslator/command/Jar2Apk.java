package software.coley.dextranslator.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.task.Converter;

import java.io.File;

/**
 * Command to convert one or more JAR files to an APK file.
 *
 * @author Matt Coley
 */
@SuppressWarnings("unused")
@Command(name = "j2apk",
		description = "one or more JAR files to an APK file")
public class Jar2Apk extends AbstractConversionCommand {
	@Parameters(index = "0",
			description = "Path to one or more JAR files.",
			arity = "1..*")
	private File[] inputFiles;

	@Option(names = {"-o", "--out"},
			description = "Path to APK file to write to.",
			defaultValue = "output.apk",
			required = true)
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
	public Void call() {
		Inputs inputs = new Inputs();
		for (File inputFile : inputFiles)
			inputs.addJarArchive(inputFile.toPath());

		Options options = new Options()
				.setReplaceInvalidMethodBodies(replaceInvalid)
				.setLenient(lenient)
				.setDexFileOutput(outputFile.toPath());

		new Converter()
				.setInputs(inputs)
				.setOptions(options)
				.run()
				.whenComplete(this::handle);
		return null;
	}
}
