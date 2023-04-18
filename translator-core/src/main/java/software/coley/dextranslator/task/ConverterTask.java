package software.coley.dextranslator.task;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.ir.Conversion;
import software.coley.dextranslator.ir.ConversionException;
import software.coley.dextranslator.ir.ConversionResult;
import software.coley.dextranslator.model.ApplicationData;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Task for converting content within am {@link ApplicationData} into a target platform determined by the {@link Options}.
 * <p/>
 * To convert all content in the {@link ApplicationData} to Java ensure the {@link Options}
 * are configured to write to one of the following:
 * <ul>
 *     <li>{@link Options#setJvmArchiveOutput(Path, boolean)}</li>
 *     <li>{@link Options#setJvmDirectoryOutput(Path, boolean)}</li>
 *     <li>{@link Options#setJvmOutput(ClassFileConsumer)}</li>
 * </ul>
 * <p/>
 * To convert all content in the {@link ApplicationData} to Dalvik ensure the {@link Options}
 * are configured to write to one of the following:
 * <ul>
 *     <li>{@link Options#setDexFileOutput(Path)}</li>
 *     <li>{@link Options#setDexDirectoryOutput(Path)}</li>
 *     <li>{@link Options#setDexOutput(DexIndexedConsumer)}</li>
 * </ul>
 *
 * @author Matt Coley
 */
public class ConverterTask extends AbstractTask<ConversionResult> {
	private final Supplier<ApplicationData> dataSupplier;

	/**
	 * @param dataSupplier
	 * 		Supplier to provide application data. Typically a plug for {@link LoaderTask}.
	 * @param options
	 * 		D8/R8 options wrapper.
	 */
	public ConverterTask(@Nonnull Supplier<ApplicationData> dataSupplier, @Nonnull Options options) {
		super(options);
		this.dataSupplier = dataSupplier;
	}

	@Override
	protected boolean run(@Nonnull CompletableFuture<ConversionResult> future) {
		try {
			ApplicationData data = dataSupplier.get();
			boolean replaceInvalid = options.isReplaceInvalidMethodBodies();
			ConversionResult result = Conversion.convert(data, options.getInternalOptions(), replaceInvalid);
			return future.complete(result);
		} catch (ConversionException ex) {
			return future.completeExceptionally(ex);
		}
	}
}
