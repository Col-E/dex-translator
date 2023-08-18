package software.coley.dextransformer;

import com.android.tools.r8.D8;
import org.junit.jupiter.params.provider.Arguments;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class TestBase {
	static {
		// There are a number of 'assert' statements through D8's code.
		//
		// A few in the exporting process fail such as 'DexString#getOffset' because
		// the map storing string references uses an identity lookup, not equality.
		//
		// Others like 'IRBuilder#addInvokeRegisters' are really stupid.
		// Any dex --> jar processing fails because it asserts the direction can only be jar --> dex.
		D8.class.getClassLoader().setDefaultAssertionStatus(false);
	}

	@Nonnull
	public static byte[] filterDebug(@Nonnull byte[] classBytes) {
		ClassWriter writer = new ClassWriter(0);
		ClassReader reader = new ClassReader(classBytes);
		reader.accept(writer, ClassReader.SKIP_DEBUG);
		return writer.toByteArray();
	}

	/**
	 * @param cls
	 * 		Class to load.
	 *
	 * @return Bytes of class.
	 */
	@Nonnull
	public static byte[] getRuntimeClassBytes(@Nonnull Class<?> cls) {
		try {
			return ClassLoader.getSystemResourceAsStream(cls.getName().replace('.', '/') + ".class").readAllBytes();
		} catch (IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	/**
	 * @return DEX test resources.
	 */
	@Nonnull
	public static Stream<Arguments> findDexResources() throws IOException {
		return findResources(".dex");
	}

	/**
	 * @return JAR test resources.
	 */
	@Nonnull
	public static Stream<Arguments> findJarResources() throws IOException {
		return findResources(".jar");
	}

	/**
	 * @param filter
	 * 		File name filter.
	 *
	 * @return Resources matching the filter.
	 */
	@Nonnull
	@SuppressWarnings("all")
	public static Stream<Arguments> findResources(@Nonnull String filter) throws IOException {
		return Files.find(Paths.get("src/test/resources/"), 10,
						(p, attributes) -> attributes.isRegularFile() &&
								p.getFileName().toString().endsWith(filter))
				.map(Arguments::of);
	}

	/**
	 * Used to check if an input is known to fail in the conversion process.
	 *
	 * @param path
	 * 		Input path.
	 * @param isDex2Jar
	 * 		Flag indicating direction of conversion.
	 *
	 * @return Failure type instance when the path points to an input that is known to fail.
	 * Can be an intentional failure by design of the input, or a bug.
	 * <p>
	 * For inputs expected to pass, returns {@code null}.
	 */
	@Nullable
	public static FailureType getKnownFailureType(@Nonnull Path path, boolean isDex2Jar) {
		String fullPath = path.toAbsolutePath().toString();
		String dxSamplesDir = File.separator + "dx-samples" + File.separator;
		if (fullPath.contains(dxSamplesDir)) {
			int start = fullPath.indexOf(dxSamplesDir) + dxSamplesDir.length();
			int caseId = Integer.parseInt(fullPath.substring(start, start + 3));
			if (isDex2Jar) {
				switch (caseId) {
					case 3: // Method LGoto;bigGoto(Z)I too large for class file. Code size was 83730.
					case 142: // Invalid use of register 1
					case 457: // Cannot constrain type: BOTTOM (empty) for value: v3 by constraint: INT
					case 459: // Cannot constrain type: INT for value: v1(0) by constraint: FLOAT
					case 471: // Undefined value encountered during compilation ... invalid dex input uses a register that is not defined on all control-flow paths
					case 506: // Cannot constrain type: @Nullable Main {} for value: v0 by constraint: INT_OR_FLOAT
					case 510: // Cannot constrain type: FLOAT for value: v5(1090519040) by constraint: INT
					case 518: // Cannot constrain type: BOTTOM (empty) for value: v2 by constraint: INT
					case 535: // Cannot constrain type: FLOAT for value: v6 by constraint: INT
					case 552: // Cannot constrain type: INT for value: v4(0) by constraint: FLOAT
					case 557: // Cannot constrain type: INT for value: v1 by constraint: OBJECT
					case 600: // Invalid invoke instruction. Expected use of 2 argument registers, found actual use of 1
					case 606: // Generic NPE, cannot find fallthrough 'BasicBlock'
					case 706: // Undefined value encountered during compilation ... invalid dex input uses a register that is not defined on all control-flow paths
					case 804: // Class B28685551 cannot extend itself
						// TODO: Sort these
						return FailureType.UNCATEGORIZED;
				}
			} else {
				switch (caseId) {
					case 9: // Interface methods must not be protected or package private: void Iface1.<clinit>()
					case 11: // Interface methods must not be protected or package private: void Iface1.<clinit>()
					case 22: // Interface methods must not be protected or package private: void Iface1.<clinit>()
					case 26: // Interface methods must not be protected or package private: void Iface1.<clinit>()
					case 37: // Interface methods must not be protected or package private: void IMagic.<clinit>()
						// There is no reason why a static initialize cannot exist in an interface.
						// However, in Android-land, I'm not sure if that's the case.
						return FailureType.BUG;
					case 918: // String-index overflow.
					case 948: // Class content provided for type descriptor java.lang.invoke.LambdaMetafactory actually defines class java.lang.invoke.LambdaMetafactory
						return FailureType.UNCATEGORIZED;
				}
			}

		}
		return null;
	}

	private enum FailureType {
		/**
		 * Not sure if bug in D8 handling, or the input is crafted specifically to fail.
		 */
		UNCATEGORIZED,
		/**
		 * Input is crafted specifically to fail.
		 */
		INTENTIONAL,
		/**
		 * Failure is recognized, and is a bug.
		 */
		BUG
	}
}
