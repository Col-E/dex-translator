package sample;

import java.io.IOException;

public class UnusedCatchEx {
	public static void foo() {
		try {
			throw new IOException();
		} catch (IOException e) {
			foo();
		}
	}
}
