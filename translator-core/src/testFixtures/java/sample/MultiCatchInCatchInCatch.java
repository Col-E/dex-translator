package sample;

public class MultiCatchInCatchInCatch {
	public static void foo(int i) {
		try {
			try {
				 try {
					 if (i > 0)
						 throw new Exception();
				 } catch (Exception ex) {
					 System.out.println("fail ex");
				 } catch (Throwable t) {
					 System.out.println("fail t");
				 }
			} catch (Exception ignored) {
				// empty
			}
		} catch (Throwable t) {
			System.out.println("fail outer");
		}
	}
}
