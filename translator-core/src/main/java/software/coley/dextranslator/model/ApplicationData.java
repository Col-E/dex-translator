package software.coley.dextranslator.model;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.ir.Conversion;
import software.coley.dextranslator.ir.ConversionD8ProcessingException;
import software.coley.dextranslator.ir.ConversionExportException;
import software.coley.dextranslator.ir.ConversionIRReplacementException;
import software.coley.dextranslator.task.LoaderTask;
import software.coley.dextranslator.util.UnsafeUtil;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Set;

/**
 * Summary of loaded application data.
 *
 * @param inputApplication
 * 		Wrapper of program files for processing.
 * @param applicationView
 * 		View of the loaded application and supporting services.
 *
 * @author Matt Coley
 * @see LoaderTask
 */
public record ApplicationData(@Nonnull AndroidApp inputApplication,
							  @Nonnull AppView<AppInfo> applicationView) implements AutoCloseable {
	/**
	 * Copies the backing {@link #applicationView()} content so operations executed on the copy do not
	 * get reflected in the current instance.
	 *
	 * @return Application data copy.
	 */
	public ApplicationData copy() {
		return copyWith(applicationView.options());
	}

	public ApplicationData copyWith(@Nonnull InternalOptions options) {
		DexApplication appCopy = applicationView.app().builder().build();
		if (options != appCopy.options) {
			// Replace options if instances differ
			Unsafe unsafe = UnsafeUtil.getUnsafe();
			UnsafeUtil.unchecked(() -> {
				long optionsOffset = unsafe
						.objectFieldOffset(DexApplication.class
								.getDeclaredField("options"));
				unsafe.getAndSetObject(appCopy, optionsOffset, options);
			});
		}
		AppInfo infoCopy = AppInfo.createInitialAppInfo(appCopy, SyntheticItems.GlobalSyntheticsStrategy.forSingleOutputMode());
		AppView<AppInfo> viewCopy = AppView.createForD8(infoCopy);
		return new ApplicationData(inputApplication, viewCopy);
	}

	public byte[] exportToDexFile() throws ConversionIRReplacementException,
			ConversionD8ProcessingException, ConversionExportException {
		byte[][] result = {null};
		Options options = new Options();
		options.setLenient(true);
		options.setDexOutput(new DexIndexedConsumer() {
			@Override
			public void accept(int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
				result[0] = data.copyByteData();
			}

			@Override
			public void finished(DiagnosticsHandler diagnosticsHandler) {
				// no-op
			}
		});

		InternalOptions internalOptions = options.getInternalOptions();
		Conversion.convert(copyWith(internalOptions), internalOptions, false);
		return result[0];
	}

	/**
	 * @return Application content container, used for looking up definitions within the application.
	 */
	@Nonnull
	public DexApplication getApplication() {
		return applicationView.appInfo().app();
	}

	@Override
	public void close() throws IOException {
		inputApplication.signalFinishedToProviders(null);
	}
}
