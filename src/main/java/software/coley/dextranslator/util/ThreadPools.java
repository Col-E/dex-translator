package software.coley.dextranslator.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Basic thread pool utils.
 */
public class ThreadPools {
	/**
	 * @return New fixed thread pool with max amount of threads recommended for the current system.
	 */
	public static ExecutorService newMaxFixedThreadPool() {
		int nThreads = Runtime.getRuntime().availableProcessors() - 1;
		return Executors.newFixedThreadPool(nThreads);
	}
}
