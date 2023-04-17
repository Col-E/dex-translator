package software.coley.dextranslator;

import software.coley.dextranslator.task.Converter;

import java.nio.file.Paths;

public class Test {
	public static void main(String[] args) throws Exception {
		Inputs inputs = new Inputs()
				.addDex(Paths.get(Test.class.getResource("/classes.dex").toURI()));

		Options options = new Options()
				.setReplaceInvalidMethodBodies(true)
				.setLenient(true)
				.setJvmArchiveOutput(Paths.get("out.jar"), true);

		new Converter()
				.setInputs(inputs)
				.setOptions(options)
				.run()
				.whenComplete((result, error) -> {
					if (result != null) {
						System.out.println("Conversion complete!");
					} else if (error != null) {
						error.printStackTrace();
					}
				});
	}
}
