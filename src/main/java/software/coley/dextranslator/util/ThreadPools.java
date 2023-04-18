package software.coley.dextranslator.util;

import javax.annotation.Nonnull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Basic thread pool utils.
 */
public class ThreadPools {
	/**
	 * @return New fixed thread pool with max amount of threads recommended for the current system.
	 */
	public static ExecutorService newMaxFixedThreadPool() {
		int nThreads = Runtime.getRuntime().availableProcessors() - 1;
		return Executors.newFixedThreadPool(nThreads, r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});
	}
}
