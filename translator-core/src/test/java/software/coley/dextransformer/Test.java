package software.coley.dextransformer;

import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Loader;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.task.ConverterTask;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Test {
	public static void main(String[] args) throws Exception {
		jar2apk();
	}

	static void dex2jar_obfuscated() throws Exception {
		/*
		Dex2Jar command = new Dex2Jar();
		command.setInputFiles(new File[]{new File(Test.class.getResource("/challenge.dex").toURI())});
		command.setOutputFile(new File("build/libs/challenge.jar"));
		command.setLenient(true);
		command.setReplaceInvalid(true);
		command.call();
		 */
	}

	static void jar2apk() throws Exception {
		// TODO: Proper test
		Path inputPath = Paths.get(Test.class.getResource("/tower.jar").toURI());
		Inputs inputs = new Inputs().addJarArchive(inputPath);
		Options options = new Options();
		new Loader()
				.setInputs(inputs)
				.setOptions(options)
				.run()
				.whenComplete((result, error) -> {
					if (error != null)
						error.printStackTrace();
					else {
						// TODO: This fails
						/*
						try {
							byte[] dexFile = result.exportToDexFile();
							System.out.println(dexFile);
						} catch (ConversionException ex) {
							ex.printStackTrace();
							throw new RuntimeException(ex);
						}*/

						// TODO: But this works
						options.setDexFileOutput(Paths.get("class-out.dex"));
						new ConverterTask(() -> result, options).start()
								.whenComplete((conversionResult, e2) -> {
									System.out.println(conversionResult + ":" + e2);
								});
					}
				});
	}
}
