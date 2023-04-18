package software.coley.dextranslator.command;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.utils.Reporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.proguard.AllClassNames;
import software.coley.dextranslator.task.Converter;

import java.io.File;
import java.util.Collections;
import java.util.List;

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

	@Option(names = {"-r8"},
			description = "Flag to enable usage of R8 over D8.")
	private boolean r8;

	@Override
	public Void call() {
		Inputs inputs = new Inputs();
		for (File inputFile : inputFiles)
			inputs.addJarArchive(inputFile.toPath());

		Options options = new Options()
				.setReplaceInvalidMethodBodies(replaceInvalid)
				.setLenient(lenient)
				.setUseR8(r8)
				.setProguardConfiguration(r8 ? newProguardConfig() : null)
				.setDexFileOutput(outputFile.toPath());

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

	/**
	 * @return Flag to use R8 over D8.
	 */
	public boolean isR8() {
		return r8;
	}

	/**
	 * @param r8
	 * 		Flag to use R8 over D8.
	 */
	public void setR8(boolean r8) {
		this.r8 = r8;
	}

	private static ProguardConfiguration newProguardConfig() {
		ProguardConfiguration.Builder builder = ProguardConfiguration.builder(new DexItemFactory(), new Reporter());
		builder.disableObfuscation();
		builder.addDontNotePattern(AllClassNames.INSTANCE);
		builder.addDontWarnPattern(AllClassNames.INSTANCE);
		builder.addKeepAttributePatterns(Collections.singletonList("*"));
		builder.setVerbose(true);
		return builder.build();
	}
}
