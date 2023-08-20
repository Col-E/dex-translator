package sample;

import java.io.IOException;

public class UsedCatchEx3Block {
	public static void foo(int i) {
		try {
			if (i > 2)
				throw new IllegalArgumentException();
			if (i > 1)
				throw new UnsupportedOperationException();
			if (i > 0)
				throw new IOException();
		} catch (IllegalArgumentException e) {
			System.out.println("fail 1" + e.getMessage());
		} catch (UnsupportedOperationException e) {
			System.out.println("fail 2" + e.getMessage());
		} catch (IOException e) {
			System.out.println("fail 3" + e.getMessage());
		}
	}
}
