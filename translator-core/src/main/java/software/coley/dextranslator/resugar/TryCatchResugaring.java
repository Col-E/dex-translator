package software.coley.dextranslator.resugar;

import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Utilities for re-sugaring {@link CfTryCatch} in converted outputs.
 *
 * @author Matt Coley
 */
public class TryCatchResugaring {
	/**
	 * Converting from Dalvik's register system back to our JVM stack results in some odd duplicate ranges all being
	 * adjacent to one another. These try ranges should be merged if they utilize the same exception handler.
	 *
	 * @param cf
	 * 		Code to update ranges of.
	 * @param labels
	 * 		List of labels in the code.
	 */
	public static int mergeTryCatchBlocks(@Nonnull CfCode cf, @Nonnull List<CfLabel> labels) {
		int rangeMerges = 0;
		List<CfTryCatch> ranges = cf.getTryCatchRanges();
		for (int i = ranges.size() - 2; i >= 0; i--) {
			CfTryCatch try1 = ranges.get(i);
			CfTryCatch try2 = ranges.get(i + 1);
			if (try1.guards.equals(try2.guards) && try1.targets.equals(try2.targets)) {
				int end1 = labels.indexOf(try1.end);
				int start2 = labels.indexOf(try2.start);
				if (end1 == start2 - 1) {
					rangeMerges++;
					ranges.set(i, new CfTryCatch(try1.start, try2.end, try1.guards, try1.targets));
					ranges.remove(i + 1);
				}
			}
		}
		return rangeMerges;
	}
}
