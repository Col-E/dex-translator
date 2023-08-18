package software.coley.dextransformer.accuracy;

import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.ClassFilter;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import software.coley.dextranslator.ir.ConversionException;
import software.coley.dextranslator.model.ApplicationData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Outline of a conversion step.
 *
 * @see AccuracyTests Use in conversion regression tests.
 */
public abstract class AbstractConversionStep {
	private final AbstractConversionStep previous;
	private final ClassFilter filter;
	private final int index;

	private AbstractConversionStep(int index, @Nonnull ClassFilter filter, @Nullable AbstractConversionStep previous) {
		this.index = index;
		this.filter = filter;
		this.previous = previous;
	}

	@Nonnull
	public static JvmConversionStep initJvmStep(@Nonnull ClassFilter filter, @Nonnull Map<String, byte[]> classMap) {
		return new JvmConversionStep(0, filter, null, classMap);
	}

	@Nonnull
	public static DexConversionStep initDexStep(@Nonnull ClassFilter filter, @Nonnull ApplicationData application) {
		return new DexConversionStep(0, filter, null, application);
	}

	/**
	 * @return Map of class names to disassembly.
	 *
	 * @throws IOException
	 * 		When IO operations for disassembling fail.
	 */
	@Nonnull
	public SortedMap<String, String> disassemble() throws IOException {
		SortedMap<String, String> map = new TreeMap<>();
		Collection<DexProgramClass> classes = getApplicationModel().getApplication().classes();
		for (DexProgramClass cls : classes) {
			StringBuilder sb = new StringBuilder();
			sb.append(cls.getTypeName()).append('\n').append("==========================================");
			for (DexEncodedMethod method : cls.methods(DexEncodedMethod::hasCode)) {
				Code code = method.getCode();
				sb.append(code.toString(method, RetracerForCodePrinting.empty()));
				sb.append("==========================================");
			}

			map.put(cls.getTypeName(), sb.toString());
		}
		return map;
	}

	/**
	 * @return Conversion step index.
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @return Filter to limit which classes to convert.
	 */
	@Nonnull
	public ClassFilter getFilter() {
		return filter;
	}

	/**
	 * @return Model of application in the current step.
	 *
	 * @throws IOException
	 * 		When IO operations for creating the model fail.
	 */
	@Nonnull
	public abstract ApplicationData getApplicationModel() throws IOException;

	/**
	 * @return Initial step in the chain.
	 */
	@Nonnull
	public AbstractConversionStep initial() {
		return previous != null ? previous.initial() : this;
	}

	/**
	 * @return Previous step.
	 */
	@Nullable
	public AbstractConversionStep previous() {
		return previous;
	}

	/**
	 * @return Next conversion step.
	 *
	 * @throws ConversionException
	 * 		When conversion fails.
	 * @throws IOException
	 * 		When IO operations required for conversion fail.
	 */
	@Nonnull
	public abstract AbstractConversionStep next() throws ConversionException, IOException;

	@Nonnull
	protected abstract String prefix();

	@Override
	public String toString() {
		return prefix() + "[" + index + "]";
	}

	/**
	 * Step outlining current JVM code.
	 */
	public static class JvmConversionStep extends AbstractConversionStep {
		private final Map<String, byte[]> classMap;
		private ApplicationData jvmData;

		private JvmConversionStep(int index, @Nonnull ClassFilter filter, @Nullable AbstractConversionStep previous,
								  @Nonnull Map<String, byte[]> classMap) {
			super(index, filter, previous);
			this.classMap = classMap;
		}

		@Nonnull
		@Override
		public ApplicationData getApplicationModel() throws IOException {
			if (jvmData == null)
				jvmData = ApplicationData.fromClasses(classMap.values());
			return jvmData;
		}

		@Nonnull
		@Override
		public AbstractConversionStep next() throws ConversionException, IOException {
			ApplicationData jvmData = getApplicationModel();
			ApplicationData dexData = ApplicationData.fromDex(jvmData.exportToDexFile());
			return new DexConversionStep(getIndex() + 1, getFilter(), this, dexData);
		}

		@Nonnull
		@Override
		protected String prefix() {
			return "JVM";
		}
	}

	/**
	 * Step outlining current DEX code.
	 */
	public static class DexConversionStep extends AbstractConversionStep {
		private final ApplicationData dexData;

		private DexConversionStep(int index, @Nonnull ClassFilter filter, @Nullable AbstractConversionStep previous,
								  @Nonnull ApplicationData dexData) {
			super(index, filter, previous);
			this.dexData = dexData;
		}

		@Nonnull
		@Override
		public ApplicationData getApplicationModel() {
			return dexData;
		}

		@Nonnull
		@Override
		public AbstractConversionStep next() throws ConversionException {
			Map<String, byte[]> classMap = dexData.exportToJvmClassMap(getFilter());
			return new JvmConversionStep(getIndex() + 1, getFilter(), this, classMap);
		}

		@Nonnull
		@Override
		protected String prefix() {
			return "DEX";
		}
	}
}
