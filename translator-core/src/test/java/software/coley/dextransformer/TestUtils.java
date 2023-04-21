package software.coley.dextransformer;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;

@SuppressWarnings("all")
public class TestUtils {
	public static final ClassFileConsumer EMPTY_JVM_CONSUMER = new ClassFileConsumer() {
		@Override
		public void accept(ByteDataView data, String descriptor, DiagnosticsHandler handler) {
			// no-op
		}

		@Override
		public void finished(DiagnosticsHandler handler) {
			// no-op
		}
	};

	public static final DexIndexedConsumer EMPTY_DEX_CONSUMER = new DexIndexedConsumer() {
		@Override
		public void finished(DiagnosticsHandler handler) {
			// no-op
		}
	};
}
