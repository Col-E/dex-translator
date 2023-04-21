package software.coley.dextransformer;

import com.android.tools.r8.D8;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.model.ApplicationData;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ConversionTests {
	static {
		// TODO: There are a number of 'assert' statements through D8's code.
		//  A few in the exporting process fail such as 'DexString#getOffset' because
		//  the map storing string references uses an identity lookup, not equality.
		D8.class.getClassLoader().setDefaultAssertionStatus(false);
	}

	@ParameterizedTest
	@MethodSource("findDexResources") // TODO: Not all of these inputs are intended to be valid, some of them need to be filtered out
	void testDex2Jar(@Nonnull Path inputPath) {
		// Wrap input
		Inputs inputs = assertDoesNotThrow(() -> new Inputs().addDex(inputPath));

		// Option to target output dex version
		Options options = new Options();

		// Read input
		ApplicationData data = assertDoesNotThrow(() -> ApplicationData.from(inputs, options.getInternalOptions()));

		// Write all the classes
		Map<String, byte[]> classMap = assertDoesNotThrow(data::exportToJvmClassMap);
		assertFalse(classMap.isEmpty(), "No classes in exported output for file: " + inputPath.getFileName());
		for (byte[] classFile : classMap.values()) {
			assertDoesNotThrow(() -> {
				// Should validate the classes are well-formed enough
				ClassWriter cw = new ClassWriter(0);
				new ClassReader(classFile).accept(cw, 0);
			});
		}

		// Close input
		assertDoesNotThrow(data::close);
	}

	@ParameterizedTest
	@MethodSource("findJarResources")
	void testJar2Dex(@Nonnull Path inputPath) {
		// Wrap input
		Inputs inputs = new Inputs()
				.addJarArchive(inputPath);

		// Option to target output dex version
		Options options = new Options()
				.setApiLevel(AndroidApiLevel.getAndroidApiLevel(30));

		// Read input
		ApplicationData data = assertDoesNotThrow(() -> ApplicationData.from(inputs, options.getInternalOptions()));

		// Write to DEX
		byte[] dexFile = assertDoesNotThrow(data::exportToDexFile);
		String header = new String(dexFile, 0, 7);
		assertEquals("dex\n039", header);

		// Close input
		assertDoesNotThrow(data::close);
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

	@Nonnull
	@SuppressWarnings("all")
	private static Stream<Arguments> findResources(@Nonnull String filter) throws IOException {
		return Files.find(Paths.get("src/test/resources/"), 10,
						(p, attributes) -> attributes.isRegularFile() &&
								p.getFileName().toString().endsWith(filter))
				.map(Arguments::of);
	}
}
