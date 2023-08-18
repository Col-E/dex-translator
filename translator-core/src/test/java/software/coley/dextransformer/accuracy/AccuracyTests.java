package software.coley.dextransformer.accuracy;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ClassFilter;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Opcodes;
import sample.UnusedCatchEx1Block;
import software.coley.dextransformer.TestBase;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.model.ApplicationData;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.fail;

@Disabled
public class AccuracyTests extends TestBase implements Opcodes {
	private static final int MAX_ITER = 5;
	private static final int DIFF_COLUMN_WIDTH = 80;
	private static final boolean ALWAYS_LOG = false;

	@Test
	void testSingle() {
		Class<?> c = UnusedCatchEx1Block.class;

		Map<String, byte[]> classMap = Map.of(c.getName().replace('.', '/'), filterDebug(getRuntimeClassBytes(c)));
		AbstractConversionStep step = AbstractConversionStep.initJvmStep(ClassFilter.PASS_ALL, classMap);
		iterate(step);
	}

	@ParameterizedTest
	@MethodSource("findDexResources")
	void test(@Nonnull Path inputPath) {
		if (getKnownFailureType(inputPath, true) != null)
			return;

		// Load inputs
		ApplicationData initialData = initialDex(inputPath);

		// Begin conversion iterations
		AbstractConversionStep step = AbstractConversionStep.initDexStep(ClassFilter.PASS_ALL, initialData);
		iterate(step);
	}

	private void iterate(@Nonnull AbstractConversionStep step) {
		while (step.getIndex() < MAX_ITER) {
			try {
				step = step.next();
				if (ALWAYS_LOG)
					dumpDiffFromPrior(step);
			} catch (Exception ex) {
				dumpDiffFromPrior(step);
				fail("Failed at step " + step.getIndex(), ex);
			}
		}
	}

	private static void dumpDiffFromPrior(@Nonnull AbstractConversionStep step) {
		try {
			int index = step.getIndex();
			if (index >= 2) {
				// If this step outlines DEX code, the prior step is JVM code.
				// For a comparison we need the step before the prior one for the same kind of code.
				SortedMap<String, String> disassembledCurrent = step.disassemble();
				SortedMap<String, String> disassembledPrevious = step.previous().previous().disassemble();

				// Sanity, just making sure classes we check in both maps are indeed in both.
				ImmutableSet<String> keys = Sets.union(disassembledCurrent.keySet(), disassembledPrevious.keySet()).immutableCopy();
				for (String key : keys) {
					System.out.println("\n\n\n");
					String current = disassembledCurrent.get(key);
					String previous = disassembledPrevious.get(key);

					// Get patch/diff from prior --> current
					Patch<String> diff = DiffUtils.diff(previous, current, null);

					// Print the diff to console in row format.
					DiffRowGenerator generator = DiffRowGenerator.create().build();
					List<DiffRow> diffRows = generator.generateDiffRows(List.of(previous.split("\n")), diff);
					for (DiffRow row : diffRows) {
						String oldLine = row.getOldLine();
						String newLine = row.getNewLine();
						DiffRow.Tag tag = row.getTag();
						switch (tag) {
							case INSERT:
								System.out.format("%-" + DIFF_COLUMN_WIDTH + "s --> %-" + DIFF_COLUMN_WIDTH + "s\n", "<empty>", newLine);
								break;
							case DELETE:
								System.out.format("%-" + DIFF_COLUMN_WIDTH + "s --> DELETED\n", oldLine);
								break;
							case CHANGE:
								System.out.format("%-" + DIFF_COLUMN_WIDTH + "s --> %-" + DIFF_COLUMN_WIDTH + "s\n", oldLine, newLine);
								break;
							case EQUAL:
								System.out.println(row.getOldLine());
								break;
						}
					}
				}
			}
		} catch (IOException ex) {
			fail("Failed to disassemble", ex);
		}
	}

	@Nonnull
	private static ApplicationData initialDex(@Nonnull Path inputPath) {
		try {
			Inputs inputs = new Inputs().addDex(inputPath);
			Options options = new Options()
					.enableLoadStoreOptimization()
					.setApiLevel(AndroidApiLevel.getAndroidApiLevel(30));
			return ApplicationData.from(inputs, options.getInternalOptions());
		} catch (Exception ex) {
			fail(ex);
			throw new IllegalStateException();
		}
	}
}
