package software.coley.dextranslator.command;

import picocli.CommandLine;
import software.coley.dextranslator.DexTranslatorBuildConfig;

import java.util.concurrent.Callable;

/**
 * Wrapper to provide access to other commands.
 *
 * @author Matt Coley
 */
@CommandLine.Command(name = "dex-translator",
		description = "See the sub-commands for available operations",
		version = DexTranslatorBuildConfig.VERSION,
		mixinStandardHelpOptions = true,
		subcommands = Dex2Jar.class)
public class Commands implements Callable<Void> {
	@Override
	public Void call() throws Exception {
		System.out.println("No command provided. Re-run with '-h' or '--help' for more information.");
		return null;
	}
}
