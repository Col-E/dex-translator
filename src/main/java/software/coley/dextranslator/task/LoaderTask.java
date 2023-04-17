package software.coley.dextranslator.task;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApp;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Task for loading content from configured {@link Inputs}.
 *
 * @author Matt Coley
 */
public class LoaderTask extends AbstractTask<ApplicationData> {
	private final Inputs inputs;

	/**
	 * @param inputs
	 * 		Inputs to read from.
	 * @param options
	 * 		D8/R8 options wrapper.
	 */
	public LoaderTask(@Nonnull Inputs inputs, @Nonnull Options options) {
		super(options);
		this.inputs = inputs;
	}

	protected boolean run(@Nonnull CompletableFuture<ApplicationData> future) {
		// Create input model
		AndroidApp inputApplication = inputs.populate(AndroidApp.builder()).build();
		ApplicationReader applicationReader = new ApplicationReader(inputApplication, options.getOptions(), EMPTY_TIMING);
		LazyLoadedDexApplication application;
		try {
			application = applicationReader.read();
		} catch (IOException ex) {
			return fail(ex, future);
		}

		// Create app-view of dex application
		SyntheticItems.GlobalSyntheticsStrategy syntheticsStrategy = options.getSyntheticsStrategy();
		MainDexInfo mainInfo = applicationReader.readMainDexClasses(application);
		AppInfo applicationInfo = AppInfo.createInitialAppInfo(application, syntheticsStrategy, mainInfo);
		AppView<AppInfo> applicationView = AppView.createForD8(applicationInfo);
		// TODO: createForR8 based on configured options (optimizing)

		// Complete the task
		return future.complete(new ApplicationData(inputApplication, applicationView));
	}
}
