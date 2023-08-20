package sample;

import java.io.IOException;

public class UsedTryInUnusedTry {
	public static void foo(int i) {
		try {
			try {
				Class.forName("");
				if (i > 0)
					throw new IOException();
			} catch (ClassNotFoundException e) {
				System.out.println("fail class " + e.getMessage());
			}
		} catch (IOException e) {
			System.out.println("fail io");
		}
	}
}
