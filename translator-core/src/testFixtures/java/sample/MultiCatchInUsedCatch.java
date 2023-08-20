package sample;

public class MultiCatchInUsedCatch {
	public static void foo() {
		try {
			try {
				throw new Exception();
			} catch (Exception ex) {
				System.out.println("fail ex");
			} catch (Throwable t) {
				System.out.println("fail t");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
