package software.coley.dextranslator.task;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
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

	@Override
	protected boolean run(@Nonnull CompletableFuture<ApplicationData> future) {
		// Create input model
		AndroidApp.Builder builder = AndroidApp.builder();
		try {
			// Allow D8/R8 to access classes from the runtime.
			// Required for de-sugaring and some optimization passes.
			builder.addLibraryResourceProvider(JdkClassFileProvider.fromSystemJdk());
		} catch (IOException ex) {
			return fail(ex, future);
		}
		AndroidApp inputApplication = inputs.populate(builder).build();
		ApplicationReader applicationReader = new ApplicationReader(inputApplication, options.getInternalOptions(), EMPTY_TIMING);
		DexApplication application;
		try {
			application = applicationReader.read().toDirect();
		} catch (IOException ex) {
			return fail(ex, future);
		}

		// Create app-view of dex application
		AppView<? extends AppInfo> applicationView;
		MainDexInfo mainInfo = applicationReader.readMainDexClasses(application);
		SyntheticItems.GlobalSyntheticsStrategy syntheticsStrategy = options.getSyntheticsStrategy();
		AppInfo applicationInfo = AppInfo.createInitialAppInfo(application, syntheticsStrategy, mainInfo);
		if (options.isUseR8()) {
			// TODO: Look at R8 in google's code and figure out why calling 'AppView.createForR8(application)' causes
			//        ClassCastExceptions down the road.
			applicationView = AppView.createForSimulatingD8InR8(applicationInfo);
		} else {
			applicationView = AppView.createForD8(applicationInfo);
		}

		// Close any internal archive providers now the application is fully processed.
		try {
			inputApplication.closeInternalArchiveProviders();
		} catch (IOException ex) {
			return fail(ex, future);
		}

		// Complete the task
		return future.complete(new ApplicationData(inputApplication, applicationView));
	}
}
