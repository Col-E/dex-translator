package software.coley.dextranslator.util.proguard;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.utils.TraversalContinuation;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Proguard name filter matching all class names.
 *
 * @author Matt Coley
 */
public class AllClassNames extends ProguardClassNameList {
	/**
	 * Singleton instance.
	 */
	public static AllClassNames INSTANCE = new AllClassNames();

	private AllClassNames() {
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public void writeTo(StringBuilder builder) {
		// no-op
	}

	@Override
	public List<DexType> asSpecificDexTypes() {
		return Collections.emptyList();
	}

	@Override
	public boolean matches(DexType type) {
		return true;
	}

	@Override
	public void forEachTypeMatcher(Consumer<ProguardTypeMatcher> consumer) {
		// no-op
	}

	@Override
	public TraversalContinuation<?, ?> traverseTypeMatchers(Function<ProguardTypeMatcher, TraversalContinuation<?, ?>> fn) {
		return TraversalContinuation.doContinue();
	}

	@Override
	public boolean equals(Object o) {
		return o == this;
	}

	@Override
	public int hashCode() {
		// Required implementation, which is dumb as we cannot use the default.
		return -1289247351;
	}
}
