package software.coley.dextransformer;

import com.android.tools.r8.D8;

public class TestBase {
	static {
		// There are a number of 'assert' statements through D8's code.
		//
		// A few in the exporting process fail such as 'DexString#getOffset' because
		// the map storing string references uses an identity lookup, not equality.
		//
		// Others like 'IRBuilder#addInvokeRegisters' are really stupid.
		// Any dex --> jar processing fails because it asserts the direction can only be jar --> dex.
		D8.class.getClassLoader().setDefaultAssertionStatus(false);
	}
}
