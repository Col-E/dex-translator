package software.coley.dextranslator;

import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AarArchiveResourceProvider;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ArchiveResourceProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Simple wrapper for loading multiple files as inputs to D8 processes.
 *
 * @author Matt Coley
 */
public class Inputs {
	private final List<Input> inputs = new ArrayList<>();

	/**
	 * @param archivePath
	 * 		Path to an AAR archive file to add as an input.
	 */
	public void addAarArchive(@Nonnull Path archivePath) {
		addResource(new Input(
				new PathOrigin(archivePath),
				(origin, builder) -> builder.addProgramResourceProvider(AarArchiveResourceProvider.fromArchive(archivePath)))
		);
	}

	/**
	 * @param archivePath
	 * 		Path to a JAR archive file to add as an input.
	 */
	public void addJarArchive(@Nonnull Path archivePath) {
		addResource(new Input(
				new PathOrigin(archivePath),
				(origin, builder) -> builder.addProgramResourceProvider(ArchiveResourceProvider.fromArchive(archivePath, true)))
		);
	}

	/**
	 * @param provider
	 * 		Generic provider to supply classes and file resources.
	 */
	public void addProgramProvider(@Nonnull ProgramResourceProvider provider) {
		addResource(new Input(
				(origin, builder) -> builder.addProgramResourceProvider(provider))
		);
	}

	/**
	 * @param classFilePath
	 * 		Path to class file to add as an input.
	 *
	 * @throws IOException
	 * 		When the path cannot be read from.
	 */
	public void addJvmClass(@Nonnull Path classFilePath) throws IOException {
		byte[] classBytes = Files.readAllBytes(classFilePath);
		addResource(new Input(
				new PathOrigin(classFilePath),
				(origin, builder) -> builder.addClassProgramData(classBytes, origin))
		);
	}

	/**
	 * @param classBytes
	 * 		Class file to add as an input.
	 */
	public void addJvmClass(@Nonnull byte[] classBytes) {
		addResource(new Input((origin, builder) -> builder.addClassProgramData(classBytes, origin)));
	}

	/**
	 * @param dexFilePath
	 * 		Path to dex file to add as an input.
	 *
	 * @throws IOException
	 * 		When the path cannot be read from.
	 */
	public void addDex(@Nonnull Path dexFilePath) throws IOException {
		byte[] dexBytes = Files.readAllBytes(dexFilePath);
		addResource(new Input(
				new PathOrigin(dexFilePath),
				(origin, builder) -> builder.addDexProgramData(dexBytes, origin))
		);
	}

	/**
	 * @param dexBytes
	 * 		Dex file bytes to add as an input.
	 */
	public void addDex(@Nonnull byte[] dexBytes) {
		addResource(new Input((origin, builder) -> builder.addDexProgramData(dexBytes, origin)));
	}

	/**
	 * @param input
	 * 		Generic input to add.
	 */
	public void addResource(@Nonnull Input input) {
		inputs.add(input);
	}

	/**
	 * Fills the given builder with contents from this input collection.
	 *
	 * @param builder
	 * 		Builder to dump content into.
	 *
	 * @return Passed builder.
	 */
	public AndroidApp.Builder populate(@Nonnull AndroidApp.Builder builder) {
		for (Input input : inputs)
			input.applyTo(builder);
		return builder;
	}

	/**
	 * @return All registered inputs.
	 */
	@Nonnull
	public List<Input> getInputs() {
		return inputs;
	}

	/**
	 * Input wrapper, which will lazily read data from inputs when {@link #populate(AndroidApp.Builder)} is called.
	 */
	public static class Input {
		private static final Origin UNKNOWN = Origin.unknown();
		private final BiConsumer<Origin, AndroidApp.Builder> appBuilderConsumer;
		private final Origin origin;

		private Input(@Nonnull BiConsumer<Origin, AndroidApp.Builder> appBuilderConsumer) {
			this(UNKNOWN, appBuilderConsumer);
		}

		private Input(@Nonnull Origin origin, @Nonnull BiConsumer<Origin, AndroidApp.Builder> appBuilderConsumer) {
			this.appBuilderConsumer = appBuilderConsumer;
			this.origin = origin;
		}

		/**
		 * @return {@code true} when the origin is not known.
		 * Typically, indicates the content was provided as raw-bytes without context.
		 */
		public boolean isUnknownOrigin() {
			return origin == Origin.unknown();
		}

		/**
		 * @return {@code true} when the path of the input is known.
		 *
		 * @see #getOriginPath()
		 */
		public boolean isPathOrigin() {
			return origin instanceof PathOrigin;
		}

		/**
		 * @return The path of the input file, if known.
		 */
		@Nullable
		public Path getOriginPath() {
			if (origin instanceof PathOrigin pathOrigin)
				return pathOrigin.getPath();
			return null;
		}

		/**
		 * @return Origin of the input.
		 * <br>
		 * Generally {@link Origin#unknown()} for virtual content,
		 * and {@link PathOrigin} for content loaded from local files.
		 */
		@Nonnull
		public Origin getOrigin() {
			return origin;
		}

		/**
		 * @param builder
		 * 		Builder to dump content into.
		 */
		public void applyTo(@Nonnull AndroidApp.Builder builder) {
			appBuilderConsumer.accept(origin, builder);
		}
	}
}
