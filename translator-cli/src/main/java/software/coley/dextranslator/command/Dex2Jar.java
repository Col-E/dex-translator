package software.coley.dextranslator.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.task.Converter;

import java.io.File;

/**
 * Command to convert one or more DEX files to a JAR file.
 *
 * @author Matt Coley
 */
@SuppressWarnings("unused")
@Command(name = "d2j",
		description = "Convert one or more DEX files to a JAR file")
public class Dex2Jar extends AbstractConversionCommand {
	@Parameters(index = "0",
			description = "Path to one or more DEX files.",
			arity = "1..*")
	private File[] inputFiles;

	@Option(names = {"-o", "--out"},
			description = "Path to JAR file to write to.",
			defaultValue = "out-d2j.jar",
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
	public Void call() throws Exception {
		Inputs inputs = new Inputs();
		for (File inputFile : inputFiles)
			inputs.addDex(inputFile.toPath());

		Options options = new Options()
				.enableLoadStoreOptimization()
				.setReplaceInvalidMethodBodies(replaceInvalid)
				.setLenient(lenient)
				.setJvmArchiveOutput(outputFile.toPath(), true);

		new Converter()
				.setInputs(inputs)
				.setOptions(options)
				.run()
				.whenComplete(this::handle);
		return null;
	}


	@Override
	public File[] getInputFiles() {
		return inputFiles;
	}

	@Override
	public void setInputFiles(File[] inputFiles) {
		this.inputFiles = inputFiles;
	}

	@Override
	public File getOutputFile() {
		return outputFile;
	}

	@Override
	public void setOutputFile(File outputFile) {
		this.outputFile = outputFile;
	}

	@Override
	public boolean isLenient() {
		return lenient;
	}

	@Override
	public void setLenient(boolean lenient) {
		this.lenient = lenient;
	}

	@Override
	public boolean isReplaceInvalid() {
		return replaceInvalid;
	}

	@Override
	public void setReplaceInvalid(boolean replaceInvalid) {
		this.replaceInvalid = replaceInvalid;
	}
}
