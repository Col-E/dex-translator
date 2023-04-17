package software.coley.dextranslator;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;

import javax.annotation.Nonnull;
import java.nio.file.Path;

/**
 * Simple wrapper for managing D8/R8 options.
 *
 * @author Matt Coley
 */
public class Options {
	private final InternalOptions options = new InternalOptions();
	private boolean replaceInvalidMethodBodies;

	/**
	 * @param replaceInvalidMethodBodies
	 * 		Flag to enable replacing the {@link Code} of methods which are invalid
	 * 		for writing to the current target output <i>(Either JVM or Android)</i>.
	 * 		<p/>
	 * 		<b>Be wary when using this option</b>. When encountering a bug, please report it
	 * 		as an issue on GitHub. In cases where the fault can be worked around this
	 * 		allows our tool stability to improve.
	 *
	 * @return Self
	 */
	public Options setReplaceInvalidMethodBodies(boolean replaceInvalidMethodBodies) {
		this.replaceInvalidMethodBodies = replaceInvalidMethodBodies;
		return this;
	}

	/**
	 * @param lenient
	 * 		Flag to enable options that allow more leniency in the conversion process.
	 * 		Some input validation will be skipped.
	 *
	 * @return Self
	 */
	public Options setLenient(boolean lenient) {
		options.desugarState = lenient ? InternalOptions.DesugarState.OFF : InternalOptions.DesugarState.ON;
		options.ignoreMissingClasses = lenient;
		options.disableGenericSignatureValidation = lenient;
		return this;
	}

	/**
	 * @param path
	 * 		Path to write JVM files output to, as a JAR.
	 * @param classesOnly
	 * 		Flag to include only classes in the output, skipping any regular files.
	 *
	 * @return Self
	 */
	public Options setJvmArchiveOutput(@Nonnull Path path, boolean classesOnly) {
		options.programConsumer = new ClassFileConsumer.ArchiveConsumer(path, !classesOnly);
		return this;
	}

	/**
	 * @param path
	 * 		Path to write JVM files output to, as a JAR.
	 * @param classesOnly
	 * 		Flag to include only classes in the output, skipping any regular files.
	 *
	 * @return Self
	 */
	public Options setJvmDirectoryOutput(@Nonnull Path path, boolean classesOnly) {
		options.programConsumer = new ClassFileConsumer.DirectoryConsumer(path, !classesOnly);
		return this;
	}

	/**
	 * @param consumer
	 * 		Generic JVM output consumer.
	 *
	 * @return Self
	 */
	public Options setJvmOutput(@Nonnull ClassFileConsumer consumer) {
		options.programConsumer = consumer;
		return this;
	}

	/**
	 * @param path
	 * 		Path to write the Android dex file output to.
	 *
	 * @return Self
	 */
	public Options setDexFileOutput(@Nonnull Path path) {
		options.programConsumer = new DexIndexedConsumer.ArchiveConsumer(path);
		return this;
	}

	/**
	 * @param path
	 * 		Root directory to write the Android dex file and additional resources to.
	 *
	 * @return Self
	 */
	public Options setDexDirectoryOutput(@Nonnull Path path) {
		options.programConsumer = new DexIndexedConsumer.DirectoryConsumer(path);
		return this;
	}

	/**
	 * @param consumer
	 * 		Generic DEX output consumer.
	 *
	 * @return Self
	 */
	public Options setDexOutput(@Nonnull DexIndexedConsumer consumer) {
		options.programConsumer = consumer;
		return this;
	}

	/**
	 * @return {@code true} when the output is configured.
	 */
	public boolean hasConfiguredOutput() {
		return options.programConsumer != null;
	}

	/**
	 * @return {@code true} when the output is for JVM types.
	 */
	public boolean isJvmOutput() {
		return options.isGeneratingClassFiles();
	}

	/**
	 * @return Synthetic item strategy based on the target output type.
	 */
	@Nonnull
	public SyntheticItems.GlobalSyntheticsStrategy getSyntheticsStrategy() {
		return options.isGeneratingDexIndexed() ?
				SyntheticItems.GlobalSyntheticsStrategy.forSingleOutputMode() :
				SyntheticItems.GlobalSyntheticsStrategy.forPerFileMode();
	}

	/**
	 * @return Internal options for D8/R8.
	 */
	@Nonnull
	public InternalOptions getOptions() {
		return options;
	}

	/**
	 * @return Flag to replace invalid method bodies.
	 *
	 * @see #setReplaceInvalidMethodBodies(boolean)
	 */
	public boolean isReplaceInvalidMethodBodies() {
		return replaceInvalidMethodBodies;
	}
}
