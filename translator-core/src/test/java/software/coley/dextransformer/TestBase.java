package software.coley.dextransformer;

import com.android.tools.r8.D8;
import org.junit.jupiter.params.provider.Arguments;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
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
}
