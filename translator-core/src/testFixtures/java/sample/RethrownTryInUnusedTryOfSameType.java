package sample;

import java.io.IOException;

public class RethrownTryInUnusedTryOfSameType {
	public static void foo() {
		try {
			try {
				throw new IOException();
			} catch (IOException e) {
				throw new IOException(e);
			}
		} catch (IOException e) {
			foo();
		}
	}
}
