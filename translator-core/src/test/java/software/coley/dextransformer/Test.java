package software.coley.dextransformer;

public class Test {
	public static void main(String[] args) throws Exception {
		dex2jar_obfuscated();
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
		/*
		Jar2Apk command = new Jar2Apk();
		command.setInputFiles(new File[]{new File(Test.class.getResource("/calc.jar").toURI())});
		command.setOutputFile(new File("build/libs/calc.apk"));
		command.setR8(true);
		command.call();
		 */
	}
}
