package software.coley.dextranslator.task;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.AndroidApp;

import javax.annotation.Nonnull;

/**
 * Summary of loaded application data.
 *
 * @author Matt Coley
 * @see LoaderTask
 */
public class ApplicationData {
	private final AndroidApp inputApplication;
	private final AppView<AppInfo> applicationView;

	/**
	 * @param inputApplication
	 * 		Wrapper of program files for processing.
	 * @param applicationView
	 * 		View of the loaded application and supporting services.
	 */
	public ApplicationData(@Nonnull AndroidApp inputApplication,
						   @Nonnull AppView<AppInfo> applicationView) {
		this.inputApplication = inputApplication;
		this.applicationView = applicationView;
	}

	/**
	 * @return Wrapper of program files for processing.
	 */
	@Nonnull
	public AndroidApp getInputApplication() {
		return inputApplication;
	}

	/**
	 * @return View of the loaded application and supporting services.
	 */
	@Nonnull
	public AppView<AppInfo> getApplicationView() {
		return applicationView;
	}

	/**
	 * @return Application content container, used for looking up definitions within the application.
	 */
	@Nonnull
	public DexApplication getApplication() {
		return applicationView.appInfo().app();
	}
}
