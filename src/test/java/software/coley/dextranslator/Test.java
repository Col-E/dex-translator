package software.coley.dextranslator;

import software.coley.dextranslator.task.ConverterResult;
import software.coley.dextranslator.task.Converter;

import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class Test {
	public static void main(String[] args) throws Exception {
		System.out.println("hi");

		Inputs inputs = new Inputs();
		inputs.addDex(Paths.get(Test.class.getResource("/classes.dex").toURI()));

		Options options = new Options();
		options.setReplaceInvalidMethodBodies(true);
		options.setLenient(true);
		options.setJvmArchiveOutput(Paths.get("out.jar"), true);

		CompletableFuture<ConverterResult> conversionFuture =
				new Converter()
						.setInputs(inputs)
						.setOptions(options)
						.run();

		conversionFuture.whenComplete((result, error) -> {
			if (result != null) {
				System.out.println("Conversion complete!");
			} else if (error != null) {
				error.printStackTrace();
			}
		});
	}
}
