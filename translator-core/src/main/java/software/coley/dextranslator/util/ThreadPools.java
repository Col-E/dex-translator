package software.coley.dextranslator.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Basic thread pool utils.
 */
public class ThreadPools {
	private static final AtomicReference<ExecutorService> sharedThreadPool = new AtomicReference<>();

	/**
	 * @return Shared fixed thread pool with max amount of threads recommended for the current system.
	 */
	public static ExecutorService getMaxFixedThreadPool() {
		synchronized (sharedThreadPool) {
			ExecutorService service = sharedThreadPool.get();
			if (service == null) {
				int nThreads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
				service = Executors.newFixedThreadPool(nThreads, r -> {
					Thread t = new Thread(r);
					t.setPriority(Thread.MAX_PRIORITY);
					t.setDaemon(true);
					return t;
				});
				((ThreadPoolExecutor) service).setKeepAliveTime(60L, TimeUnit.SECONDS);
				((ThreadPoolExecutor) service).allowCoreThreadTimeOut(true);
				sharedThreadPool.set(service);
			}
			return service;
		}
	}
}
