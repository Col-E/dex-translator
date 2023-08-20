package sample;

public class EmptyCatchBlock {
	public static void foo(int i) {
		try {
			if (i > 0)
				throw new Exception();
		} catch (Exception ignored) {
			// empty, no code at all
		}
	}
}
