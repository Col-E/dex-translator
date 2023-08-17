package software.coley.dextransformer;

import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.model.ApplicationData;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConversionTests extends TestBase {
	@ParameterizedTest
	@MethodSource("findDexResources")
	void testDex2Jar(@Nonnull Path inputPath) {
		if (getKnownFailureType(inputPath, true) != null)
			return;

		// Wrap input
		Inputs inputs = assertDoesNotThrow(() -> new Inputs().addDex(inputPath));

		// Option to target output dex version
		Options options = new Options();

		// Read input
		ApplicationData data = assertDoesNotThrow(() -> ApplicationData.from(inputs, options.getInternalOptions()));

		// Write all the classes
		Map<String, byte[]> classMap = assertDoesNotThrow(() -> data.exportToJvmClassMap());
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
		if (getKnownFailureType(inputPath, false) != null)
			return;

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
}
