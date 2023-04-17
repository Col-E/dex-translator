package software.coley.dextranslator.task;

import com.android.tools.r8.utils.Timing;
import software.coley.dextranslator.Options;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
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
	protected static final Timing EMPTY_TIMING = Timing.empty();
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
		Executors.newSingleThreadExecutor().submit(() -> run(future));
		return future;
	}

	protected abstract boolean run(@Nonnull CompletableFuture<T> future);

	protected static boolean fail(@Nonnull Exception ex,
								  @Nonnull CompletableFuture<?> future) {
		return future.completeExceptionally(ex);
	}
}
