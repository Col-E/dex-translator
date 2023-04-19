package software.coley.dextranslator.task;

import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.model.ApplicationData;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Task for loading content from configured {@link Inputs}.
 *
 * @author Matt Coley
 */
public class LoaderTask extends AbstractTask<ApplicationData> {
	private final Inputs inputs;

	/**
	 * @param inputs
	 * 		Inputs to read from.
	 * @param options
	 * 		D8/R8 options wrapper.
	 */
	public LoaderTask(@Nonnull Inputs inputs, @Nonnull Options options) {
		super(options);
		this.inputs = inputs;
	}

	@Override
	protected boolean run(@Nonnull CompletableFuture<ApplicationData> future) {
		try {
			return future.complete(ApplicationData.from(inputs, options.getInternalOptions()));
		} catch (IOException ex) {
			return fail(ex, future);
		}
	}
}
