package software.coley.dextransformer;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ClassFilter;
import org.junit.jupiter.api.Test;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.model.ApplicationData;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LimitedConversionTests extends TestBase {
	@Test
	void testClassFilterLimitsWhichStuffGetsProcessed() {
		// Inputs
		String resourcePath = "/dx-samples/068-classloader/classes.jar";
		Path jarPath = assertDoesNotThrow(() -> Paths.get(DataModelTests.class.getResource(resourcePath).toURI()));
		Inputs inputs = new Inputs().addJarArchive(jarPath);
		Options options = new Options()
				.setApiLevel(AndroidApiLevel.getAndroidApiLevel(30));

		// Model
		ApplicationData data = assertDoesNotThrow(() -> ApplicationData.from(inputs, options.getInternalOptions()));

		// Export full class map
		//  - 17 classes expected
		Map<String, byte[]> exported = assertDoesNotThrow(() -> data.exportToJvmClassMap());
		assertEquals(17, exported.size());

		// Export with nothing visible
		//  - should be empty
		exported = assertDoesNotThrow(() -> data.exportToJvmClassMap(ClassFilter.PASS_NONE));
		assertEquals(0, exported.size());

		// Export with one class visible
		exported = assertDoesNotThrow(() -> data.exportToJvmClassMap(ClassFilter.forType("Base")));
		assertEquals(1, exported.size());

		// Export with one class visible, but it references another class in the application model
		//  - Should still work
		exported = assertDoesNotThrow(() -> data.exportToJvmClassMap(ClassFilter.forType("BaseOkay")));
		assertEquals(1, exported.size());
	}
}
