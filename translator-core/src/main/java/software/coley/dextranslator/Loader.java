package software.coley.dextranslator;

import software.coley.dextranslator.model.ApplicationData;
import software.coley.dextranslator.task.LoaderTask;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * General content loading process outline.
 *
 * @author Matt Coley
 */
public class Loader {
	private Inputs inputs;
	private Options options;

	/**
	 * @param inputs
	 * 		Inputs to read from.
	 *
	 * @return Self
	 */
	@Nonnull
	public Loader setInputs(@Nonnull Inputs inputs) {
		this.inputs = inputs;
		return this;
	}

	/**
	 * @param options
	 * 		Options to use when handling input.
	 *
	 * @return Self
	 */
	@Nonnull
	public Loader setOptions(@Nonnull Options options) {
		this.options = options;
		return this;
	}

	/**
	 * Starts a {@link LoaderTask} with the current inputs and options.
	 *
	 * @return Future of the loading task result.
	 *
	 * @throws IllegalArgumentException
	 * 		When no {@link Inputs} or {@link Options} have been provided.
	 */
	@Nonnull
	public CompletableFuture<ApplicationData> run() {
		if (inputs == null)
			throw new IllegalArgumentException("Inputs not provided");
		if (options == null)
			throw new IllegalArgumentException("Options not provided");
		return new LoaderTask(inputs, options).start();
	}
}
