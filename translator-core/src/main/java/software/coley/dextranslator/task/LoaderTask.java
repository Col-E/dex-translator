package software.coley.dextranslator.task;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import software.coley.dextranslator.model.ApplicationData;
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
		InternalOptions internalOptions = options.getInternalOptions();
		ApplicationReader applicationReader = new ApplicationReader(inputApplication, internalOptions, EMPTY_TIMING);
		DexApplication application;
		try {
			application = applicationReader.read().toDirect();
		} catch (IOException ex) {
			return fail(ex, future);
		}

		// Create app-view of dex application
		AppView<AppInfo> applicationView;
		MainDexInfo mainDexInfo = applicationReader.readMainDexClasses(application);
		SyntheticItems.GlobalSyntheticsStrategy syntheticsStrategy = options.getSyntheticsStrategy();
		if (options.isUseR8()) {
			// TODO: Paste from R8 and cleanup after.
			//  - Pay attention to which view instance is used when because it really matters
			//  - Some will take views with Liveness, others with just the hierarchy
			//  - Using the wrong one will cause problems down the road in IR conversion/optimization
			AppInfo info = AppInfo.createInitialAppInfo(application, syntheticsStrategy, mainDexInfo);
			applicationView = AppView.createForSimulatingD8InR8(info);
		} else {
			AppInfo info = AppInfo.createInitialAppInfo(application, syntheticsStrategy, mainDexInfo);
			applicationView = AppView.createForD8(info);
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
