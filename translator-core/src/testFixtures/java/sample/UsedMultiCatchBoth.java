package sample;

public class UsedMultiCatchBoth {
	public static void foo(int i) {
		try {
			if (i > 0)
				throw new Exception();
		} catch (Exception ex) {
			System.out.println("fail ex " + ex.getMessage());
		} catch (Throwable t) {
			System.out.println("fail t" + t.getMessage());
		}
	}
}
