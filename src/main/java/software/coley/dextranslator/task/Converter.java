package software.coley.dextranslator.task;

import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * General conversion process outline.
 *
 * @author Matt Coley
 */
public class Converter {
	private Inputs inputs;
	private Options options;

	/**
	 * @param inputs
	 * 		Inputs to read from.
	 *
	 * @return Self
	 */
	@Nonnull
	public Converter setInputs(@Nonnull Inputs inputs) {
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
	public Converter setOptions(@Nonnull Options options) {
		this.options = options;
		return this;
	}

	/**
	 * Starts a {@link ConverterTask} with the current inputs and options.
	 *
	 * @return Future of the conversion task result.
	 *
	 * @throws IllegalArgumentException
	 * 		When no {@link Inputs} or {@link Options} have been provided.
	 */
	@Nonnull
	public CompletableFuture<ConverterResult> run() {
		if (inputs == null)
			throw new IllegalArgumentException("Inputs not provided");
		if (options == null)
			throw new IllegalArgumentException("Options not provided");
		if (!options.hasConfiguredOutput())
			throw new IllegalArgumentException("Options has not configured an output sink");
		return new Loader()
				.setInputs(inputs)
				.setOptions(options)
				.run()
				.thenCompose(data -> new ConverterTask(() -> data, options).start());
	}
}
