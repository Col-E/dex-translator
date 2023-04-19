package software.coley.dextranslator.task;

import software.coley.dextranslator.Options;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Common task outline.
 *
 * @param <T>
 * 		Task result type.
 *
 * @author Matt Coley
 */
public abstract class AbstractTask<T> {
	protected final Options options;

	/**
	 * @param options
	 * 		D8/R8 options wrapper.
	 */
	protected AbstractTask(@Nonnull Options options) {
		this.options = options;
	}

	/**
	 * Starts the task on a separate thread.
	 *
	 * @return Future of task result.
	 */
	@Nonnull
	public CompletableFuture<T> start() {
		CompletableFuture<T> future = new CompletableFuture<>();
		ExecutorService service = Executors.newSingleThreadExecutor();
		service.submit(() -> run(future));
		service.shutdown();
		return future;
	}

	protected abstract boolean run(@Nonnull CompletableFuture<T> future);

	protected boolean fail(@Nonnull Exception ex,
						   @Nonnull CompletableFuture<?> future) {
		return future.completeExceptionally(ex);
	}
}
