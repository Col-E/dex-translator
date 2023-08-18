package sample;

import java.io.IOException;

public class UsedCatchEx1Block {
	public static void foo(int i) {
		try {
			if (i > 0)
				throw new IOException();
		} catch (IOException e) {
			System.out.println("fail " + e.getMessage());
		}
	}
}
