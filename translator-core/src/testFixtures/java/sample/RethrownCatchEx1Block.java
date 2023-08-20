package sample;

import java.io.IOException;

public class RethrownCatchEx1Block {
	public static void foo(int i) throws IOException {
		try {
			if (i > 0)
				throw new IOException();
		} catch (IOException e) {
			throw e;
		}
	}
}
