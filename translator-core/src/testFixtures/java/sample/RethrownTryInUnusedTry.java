package sample;

import java.io.IOException;

public class RethrownTryInUnusedTry {
	public static void foo(int i) {
		try {
			try {
				Class.forName("");
				if (i > 0)
					throw new IOException();
			} catch (ClassNotFoundException e) {
				throw new IOException(e);
			}
		} catch (IOException e) {
			System.out.println("fail io");
		}
	}
}
