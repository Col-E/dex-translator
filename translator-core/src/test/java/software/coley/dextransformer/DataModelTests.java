package software.coley.dextransformer;

import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Test;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.model.ApplicationData;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DataModelTests extends TestBase {
	@Test
	void testExportDoesNotTamperWithDataModel() {
		// Inputs
		String resourcePath = "/dx-samples/001-HelloWorld/classes.jar";
		Path jarPath = assertDoesNotThrow(() -> Paths.get(DataModelTests.class.getResource(resourcePath).toURI()));
		Inputs inputs = new Inputs().addJarArchive(jarPath);
		Options options = new Options()
				.setApiLevel(AndroidApiLevel.getAndroidApiLevel(30));

		// Model
		ApplicationData data = assertDoesNotThrow(() -> ApplicationData.from(inputs, options.getInternalOptions()));

		// Initial state
		DexProgramClass mainClass = data.getClass("Main");
		DexEncodedMethod mainMethod = Iterables.get(mainClass.methods(), 1);
		Code code = mainMethod.getCode();

		// Convert to DEX
		byte[] dexFile = assertDoesNotThrow(data::exportToDexFile);
		assertNotNull(dexFile);

		// Second export should yield same DEX
		//  - Some stuff may be out of order, so length check should suffice
		byte[] dexFileSecondPass = assertDoesNotThrow(data::exportToDexFile);
		assertEquals(dexFile.length, dexFileSecondPass.length);

		// Post-export state
		DexProgramClass mainClassPostExport = data.getClass("Main");
		DexEncodedMethod mainMethodPostExport = Iterables.get(mainClassPostExport.methods(), 1);
		Code codePostExport = mainMethodPostExport.getCode();

		// Exporting should not have affected the state
		assertSame(mainClass, mainClassPostExport);
		assertSame(mainMethod, mainMethodPostExport);
		assertSame(code, codePostExport);
	}

	@Test
	void testDataFromProgramClassesYieldsSameResults() {
		// Inputs
		String resourcePath = "/dx-samples/008-exceptions/classes.jar";
		Path jarPath = assertDoesNotThrow(() -> Paths.get(DataModelTests.class.getResource(resourcePath).toURI()));
		Inputs inputs = new Inputs().addJarArchive(jarPath);
		Options options = new Options()
				.setApiLevel(AndroidApiLevel.getAndroidApiLevel(30));

		// Model
		ApplicationData data = assertDoesNotThrow(() -> ApplicationData.from(inputs, options.getInternalOptions()));

		// Re-create the model to test if pulling from DexProgramClasses works
		ApplicationData dataCopy = assertDoesNotThrow(() ->
				ApplicationData.fromProgramClasses(data.getApplication().classes()));

		// Operations should yield the same results
		Map<String, byte[]> mapCopy = assertDoesNotThrow(() -> dataCopy.exportToJvmClassMap());
		Map<String, byte[]> mapOrig = assertDoesNotThrow(() -> data.exportToJvmClassMap());
		assertEquals(mapOrig.keySet(), mapCopy.keySet());
	}

	@Test
	void mergeClassesIntoNewModelFromJar() {
		String resourcePathCL = "/dx-samples/068-classloader/classes.jar";
		String resourcePathEX = "/dx-samples/008-exceptions/classes.jar";
		mergeClassesIntoNewModelFromJar(resourcePathCL, resourcePathEX);
	}

	@Test
	void mergeClassesIntoNewModelFromDex() {
		String resourcePathCL = "/dx-samples/068-classloader/classes.dex";
		String resourcePathEX = "/dx-samples/008-exceptions/classes.dex";
		mergeClassesIntoNewModelFromJar(resourcePathCL, resourcePathEX);
	}

	private static void mergeClassesIntoNewModelFromJar(String resourcePathCL, String resourcePathEX) {
		// Inputs
		Path pathCL = assertDoesNotThrow(() -> Paths.get(DataModelTests.class.getResource(resourcePathCL).toURI()));
		Path pathEX = assertDoesNotThrow(() -> Paths.get(DataModelTests.class.getResource(resourcePathEX).toURI()));
		Inputs inputsCL = new Inputs();
		Inputs inputsEX = new Inputs();
		if (resourcePathCL.endsWith(".jar")) inputsCL.addJarArchive(pathCL);
		else assertDoesNotThrow(() -> inputsCL.addDex(pathCL));
		if (resourcePathEX.endsWith(".jar")) inputsEX.addJarArchive(pathEX);
		else assertDoesNotThrow(() -> inputsEX.addDex(pathEX));
		Options options = new Options()
				.setApiLevel(AndroidApiLevel.getAndroidApiLevel(30));

		// Application models
		Set<String> expectedClClasses = Sets.newHashSet(
				"Base",
				"BaseOkay",
				"DoubledExtend",
				"DoubledExtendOkay",
				"DoubledImplement",
				"DoubledImplement2",
				"FancyLoader",
				"ICommon",
				"ICommon2",
				"IDoubledExtendOkay",
				"IGetDoubled",
				"IfaceSuper",
				"InaccessibleBase",
				"InaccessibleInterface",
				"Main",
				"SimpleBase",
				"Useless"
		);
		Set<String> expectedExClasses = Sets.newHashSet(
				"BadError",
				"BadErrorNoStringInit",
				"BadInit",
				"BadInitNoStringInit",
				"BadSuperClass",
				"DerivedFromBadSuperClass",
				"Main",
				"MultiDexBadInit",
				"MultiDexBadInitWrapper1"
		);
		ApplicationData dataCL = assertDoesNotThrow(() -> ApplicationData.from(inputsCL, options.getInternalOptions()));
		ApplicationData dataEX = assertDoesNotThrow(() -> ApplicationData.from(inputsEX, options.getInternalOptions()));
		assertEquals(expectedClClasses, dataCL.getClassNames());
		assertEquals(expectedExClasses, dataEX.getClassNames());

		// Merge classes
		Set<String> expectedCombined = Sets.union(expectedClClasses, expectedExClasses);
		dataCL.updateClasses(dataEX);
		assertEquals(expectedCombined, dataCL.getClassNames());

		// Exported class math should contain both
		Map<String, byte[]> classes = assertDoesNotThrow(() -> dataCL.exportToJvmClassMap());
		assertEquals(classes.keySet(), dataCL.getClassNames());
	}
}
