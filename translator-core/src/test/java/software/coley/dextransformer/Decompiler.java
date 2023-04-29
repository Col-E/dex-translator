package software.coley.dextransformer;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.util.CfrVersionInfo;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Decompiler {
	/**
	 * @param code
	 * 		Class bytecode.
	 *
	 * @return Decompiled code.
	 */
	public static String decompile(byte[] code) {
		CompletableFuture<String> future = new CompletableFuture<>();
		String name = new ClassReader(code).getClassName();
		Map<String, String> optionsMap = new HashMap<>();
		optionsMap.put("comments", "false");
		optionsMap.put("forcetopsort", "true");
		new CfrDriver.Builder()
				.withOptions(optionsMap)
				.withClassFileSource(new ClassFileSourceImpl(name, code))
				.withOutputSink(new OutputSinkFactoryImpl(future))
				.build()
				.analyse(Arrays.asList(name));
		try {
			return future.get(3500, TimeUnit.MILLISECONDS);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @param code
	 * 		Input decompiled code.
	 *
	 * @return Filtered decompiled code.
	 */
	private static String filter(String code) {
		return code.replace("/*\n" +
				" * Decompiled with CFR " + CfrVersionInfo.VERSION + ".\n" +
				" */\n", "");
	}

	private static class ClassFileSourceImpl implements ClassFileSource {
		private final String name;
		private final byte[] code;

		public ClassFileSourceImpl(String name, byte[] code) {
			this.name = name;
			this.code = code;
		}

		@Override
		public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
			// no-op
		}

		@Override
		public Collection<String> addJar(String jarPath) {
			return Collections.emptyList();
		}

		@Override
		public String getPossiblyRenamedPath(String path) {
			return path;
		}

		@Override
		public Pair<byte[], String> getClassFileContent(String path) throws IOException {
			return path.equals(name + ".class") ? new Pair<>(code, name) : null;
		}
	}

	private static class OutputSinkFactoryImpl implements OutputSinkFactory {
		private final CompletableFuture<String> future;

		public OutputSinkFactoryImpl(CompletableFuture<String> future) {
			this.future = future;
		}

		@Override
		public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
			return Arrays.asList(SinkClass.values());
		}

		@Override
		public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
			switch (sinkType) {
				case JAVA:
					return sinkable -> future.complete(filter(sinkable.toString()));
				case EXCEPTION:
					return t -> System.err.println("CFR-Error: " + t);
				case SUMMARY:
				case PROGRESS:
				default:
					return t -> {
					};
			}
		}
	}
}
