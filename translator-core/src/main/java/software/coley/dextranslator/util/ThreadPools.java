package software.coley.dextranslator.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Basic thread pool utils.
 */
public class ThreadPools {
	private static ExecutorService sharedThreadPool;

	/**
	 * @return Shared fixed thread pool with max amount of threads recommended for the current system.
	 */
	public static ExecutorService getMaxFixedThreadPool() {
		if (sharedThreadPool == null) {
			int nThreads = Runtime.getRuntime().availableProcessors() - 1;
			sharedThreadPool = Executors.newFixedThreadPool(nThreads, r -> {
				Thread t = new Thread(r);
				t.setDaemon(true);
				return t;
			});
		}
		return sharedThreadPool;
	}
}
