package software.coley.dextranslator.util;

import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/**
 * Unsafe utils.
 */
public class UnsafeUtil {
	private static final Unsafe UNSAFE;

	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			UNSAFE = (Unsafe) f.get(null);
		} catch (ReflectiveOperationException ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}

	/**
	 * @return Unsafe instance for reflection bypasses.
	 */
	@Nonnull
	public static Unsafe getUnsafe() {
		return UNSAFE;
	}

	/**
	 * @param r
	 * 		Operation to run with ignoring exceptions.
	 */
	public static void unchecked(UncheckedRunnable r) {
		try {
			r.run();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param r
	 * 		Operation to run with ignoring exceptions.
	 * @param <T>
	 * 		Operation return type.
	 *
	 * @return Provided value from operation.
	 */
	public static <T> T unchecked(UncheckedSupplier<T> r) {
		try {
			return r.get();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @see #unchecked(UncheckedRunnable)
	 */
	public interface UncheckedRunnable {
		/**
		 * @throws Throwable
		 * 		When the operation encounters any failure.
		 */
		void run() throws Throwable;
	}

	/**
	 * @param <T>
	 * 		Return type.
	 *
	 * @see #unchecked(UncheckedSupplier)
	 */
	public interface UncheckedSupplier<T> {
		/**
		 * @return Some value.
		 *
		 * @throws Throwable
		 * 		When the operation encounters any failure.
		 */
		T get() throws Throwable;
	}
}
