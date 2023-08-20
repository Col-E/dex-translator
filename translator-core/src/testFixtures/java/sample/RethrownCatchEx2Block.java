package sample;

import java.io.IOException;

public class RethrownCatchEx2Block {
	public static void foo(int i) throws IOException {
		try {
			if (i > 1)
				throw new UnsupportedOperationException();
			if (i > 0)
				throw new IOException();
		} catch (UnsupportedOperationException e) {
			throw e;
		} catch (IOException e) {
			throw e;
		}
	}
}
