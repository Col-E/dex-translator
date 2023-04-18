package software.coley.dextranslator;

import picocli.CommandLine;
import software.coley.dextranslator.command.Commands;

/**
 * Main entry-point to handle invocation of supported commands.
 */
public class Main {
	/**
	 * @param args
	 * 		User input denoting a command.
	 */
	public static void main(String[] args) {
		new CommandLine(new Commands()).execute(args);
	}
}
