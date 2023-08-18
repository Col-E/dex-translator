package sample;

import java.io.IOException;

public class UnusedCatchEx2Block {
	public static void foo(int i) {
		try {
			if (i > 1)
				throw new UnsupportedOperationException();
			if (i > 0)
				throw new IOException();
		}catch (UnsupportedOperationException e) {
			System.out.println("fail 1");
		} catch (IOException e) {
			System.out.println("fail 2");
		}
	}
}
