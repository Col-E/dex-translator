package sample;

import java.io.IOException;

public class UnusedTryInUsedTry {
	public static void foo(int i) {
		try {
			try {
				Class.forName("");
				if (i > 0)
					throw new IOException();
			} catch (ClassNotFoundException e) {
				System.out.println("fail class");
			}
		} catch (IOException e) {
			System.out.println("fail io " + e.getMessage());
		}
	}
}
