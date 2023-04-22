package software.coley.dextranslator.model;

import com.android.tools.r8.*;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.*;
import software.coley.dextranslator.Inputs;
import software.coley.dextranslator.Options;
import software.coley.dextranslator.ir.Conversion;
import software.coley.dextranslator.ir.ConversionD8ProcessingException;
import software.coley.dextranslator.ir.ConversionExportException;
import software.coley.dextranslator.ir.ConversionIRReplacementException;
import software.coley.dextranslator.util.ThreadPools;
import software.coley.dextranslator.util.UnsafeUtil;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Summary of loaded application data.
 *
 * @author Matt Coley
 */
public class ApplicationData {
	private final AndroidApp inputApplication;
	private DexApplication application;

	/**
	 * @param inputApplication
	 * 		Container holding input sources of program data and file resources.
	 * 		This container is used to create the application model, and support the export process.
	 * @param application
	 * 		Container holding information about what is in the program.
	 * 		This is the model of the {@code inputApplication}.
	 */
	public ApplicationData(@Nonnull AndroidApp inputApplication,
						   @Nonnull DexApplication application) {
		this.inputApplication = inputApplication;
		this.application = application;
	}

	/**
	 * @param inputs
	 * 		Inputs to load from.
	 * @param options
	 * 		Internal D8 options to use.
	 * 		Can be easily constructed with the {@link Options} wrapper type for a simpler approach.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData from(@Nonnull Inputs inputs, @Nonnull InternalOptions options) throws IOException {
		// Create input model
		AndroidApp.Builder builder = AndroidApp.builder();

		// Allow D8 to access classes from the runtime.
		// Required for de-sugaring and some optimization passes.
		builder.addLibraryResourceProvider(systemJdkProvider());

		// Load content from the inputs.
		AndroidApp inputApplication = inputs.populate(builder).build();

		// Read the application data from the loaded content.
		try {
			ApplicationReader applicationReader = new ApplicationReader(inputApplication, options, Timing.empty());
			DexApplication application = applicationReader.read(ThreadPools.getMaxFixedThreadPool());
			return new ApplicationData(inputApplication, application);
		} finally {
			// Close any internal archive providers now the application is fully processed.
			inputApplication.closeInternalArchiveProviders();
		}
	}

	/**
	 * @param optionsForView
	 * 		New options to utilize for the created {@link AppView}.
	 *
	 * @return View of the application.
	 */
	@Nonnull
	public AppView<AppInfo> createView(@Nonnull InternalOptions optionsForView) {
		// Create a shallow-copy of the operation modifications do not affect the original copy held by our class.
		DexApplication applicationCopy = copyApplication(optionsForView);

		// Synthesis strategy will almost always be single-output mode.
		SyntheticItems.GlobalSyntheticsStrategy syntheticsStrategy =
				optionsForView.isGeneratingDexIndexed() ?
						SyntheticItems.GlobalSyntheticsStrategy.forSingleOutputMode() :
						SyntheticItems.GlobalSyntheticsStrategy.forPerFileMode();

		// Read main dex info, then wrap into info-model and finally the view.
		MainDexInfo mainDexInfo = readMainDexInfoFrom(inputApplication, applicationCopy);
		AppInfo info = AppInfo.createInitialAppInfo(applicationCopy, syntheticsStrategy, mainDexInfo);
		return AppView.createForD8(info);
	}

	/**
	 * @return Map of internal class names to JVM bytecode of classes.
	 *
	 * @throws ConversionIRReplacementException
	 * 		When replacing {@link CfCode} of input classes fails.
	 * @throws ConversionD8ProcessingException
	 * 		When D8 conversion internals fails.
	 * @throws ConversionExportException
	 * 		When conversion exporting fails.
	 */
	@Nonnull
	public Map<String, byte[]> exportToJvmClassMap() throws ConversionIRReplacementException,
			ConversionD8ProcessingException, ConversionExportException {
		Map<String, byte[]> result = new HashMap<>();

		// Our temporary options to dictate exporting to JVM class files.
		Options exportOptions = new Options();
		exportOptions.setLenient(true);
		exportOptions.setJvmOutput(new ClassFileConsumer() {
			@Override
			public void accept(ByteDataView data, String descriptor, DiagnosticsHandler handler) {
				String internalName = descriptor.substring(1, descriptor.length() - 1);
				result.put(internalName, data.copyByteData());
			}

			@Override
			public void finished(DiagnosticsHandler diagnosticsHandler) {
				// no-op
			}
		});

		// Run conversion process, then yield results.
		Conversion.convert(this, exportOptions.getInternalOptions(), false);
		return result;
	}

	/**
	 * @return Bytes of the generated DEX file.
	 *
	 * @throws ConversionIRReplacementException
	 * 		When replacing {@link CfCode} of input classes fails.
	 * @throws ConversionD8ProcessingException
	 * 		When D8 conversion internals fails.
	 * @throws ConversionExportException
	 * 		When conversion exporting fails.
	 */
	@Nonnull
	public byte[] exportToDexFile() throws ConversionIRReplacementException,
			ConversionD8ProcessingException, ConversionExportException {
		// Hack to allow passing the 'byte[]' output in the dex-output consumer to this local.
		byte[][] result = {null};

		// Our temporary options to dictate exporting to a DEX file.
		Options exportOptions = new Options();
		exportOptions.setApiLevel(application.options.getMinApiLevel());
		exportOptions.setLenient(true);
		exportOptions.setDexOutput(new DexIndexedConsumer() {
			@Override
			public void accept(int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
				result[0] = data.copyByteData();
			}

			@Override
			public void finished(DiagnosticsHandler diagnosticsHandler) {
				// no-op
			}
		});

		// Run conversion process, then yield results.
		Conversion.convert(this, exportOptions.getInternalOptions(), false);
		byte[] resultUnwrapped = result[0];
		if (resultUnwrapped == null)
			throw new ConversionExportException(new IllegalStateException("No DEX file was observed by consumer"), false);
		return resultUnwrapped;
	}

	/**
	 * Updates the {@link #getApplication()} to replace a prior entry of the given class,
	 * with the new instance provided.
	 *
	 * @param updated
	 * 		Updated class instance.
	 *
	 * @return Original class instance replaced. May be {@code null} if no class by the name existed previously.
	 */
	@Nullable
	public DexProgramClass updateClass(@Nonnull DexProgramClass updated) {
		return updateClass(updated.getTypeName(), updated);
	}

	/**
	 * Updates the {@link #getApplication()} to replace a prior entry of the given class,
	 * with the new instance provided.
	 *
	 * @param internalName
	 * 		Internal name of type to update.
	 * @param updatedClass
	 * 		Updated class instance.
	 *
	 * @return Original class instance replaced. May be {@code null} if no class by the name existed previously.
	 */
	@Nullable
	public DexProgramClass updateClass(@Nonnull String internalName, @Nonnull DexProgramClass updatedClass) {
		// Track old class instance.
		DexProgramClass originalClass = getClass(internalName);

		// Update application reference to include the updated class.
		application = application.builder()
				.removeProgramClass(internalName)
				.addProgramClass(updatedClass)
				.build();

		return originalClass;
	}

	/**
	 * Updates the {@link #getApplication()} to replace prior entries of the given classes,
	 * with the new instances provided.
	 *
	 * @param updatedClasses
	 * 		Map of class names, to updated class instances.
	 *
	 * @return Original class instances replaced. Values may be {@code null} if no class by the name existed previously.
	 */
	@Nonnull
	public Map<String, DexProgramClass> updateClasses(@Nonnull Map<String, DexProgramClass> updatedClasses) {
		// Track old class instances.
		Map<String, DexProgramClass> originalClasses = new HashMap<>();
		for (String typeName : updatedClasses.keySet())
			originalClasses.put(typeName, getClass(typeName));

		// Update application reference to include the updated class.
		application = application.builder()
				.removeProgramClasses(updatedClasses.keySet())
				.addProgramClasses(updatedClasses.values())
				.build();

		return originalClasses;
	}

	/**
	 * @throws IOException
	 * 		Closes class and resource providers within the {@link #getInputApplication()}.
	 */
	public void close() throws IOException {
		inputApplication.signalFinishedToProviders(null);
	}

	/**
	 * @return Container holding information about what is in the program.
	 * This is the model of the {@link #getInputApplication()}.
	 */
	@Nonnull
	public DexApplication getApplication() {
		return application;
	}

	/**
	 * @return Container holding input sources of program data and file resources.
	 * This container is used to create the application model, and support the export process.
	 */
	@Nonnull
	public AndroidApp getInputApplication() {
		return inputApplication;
	}

	/**
	 * @param newOptions
	 * 		Options providing context for the copy operation.
	 *
	 * @return Copy of application instance.
	 */
	@Nonnull
	private DexApplication copyApplication(@Nonnull InternalOptions newOptions) {
		DexApplication applicationCopy = application.copy();
		if (newOptions != applicationCopy.options)
			applicationCopy.options = newOptions;
		return applicationCopy;
	}

	/**
	 * @param internalName
	 * 		Internal class name. For example {@code java/lang/String}.
	 *
	 * @return Program definition in the {@link #getApplication() application}.
	 */
	@Nullable
	public DexProgramClass getClass(@Nonnull String internalName) {
		DexType type = application.dexItemFactory().createType("L" + internalName + ";");
		return application.programDefinitionFor(type);
	}

	/**
	 * @param inputApplication
	 * 		Container holding input sources of program data and file resources.
	 * @param application
	 * 		Container holding information about what is in the program.
	 *
	 * @return Main content of the DEX file from inputs.
	 */
	@Nonnull
	private static MainDexInfo readMainDexInfoFrom(@Nonnull AndroidApp inputApplication,
												   @Nonnull DexApplication application) {
		MainDexInfo.Builder builder = MainDexInfo.none().builder();
		if (inputApplication.hasMainDexList()) {
			DexItemFactory itemFactory = application.options.dexItemFactory();
			for (StringResource resource : inputApplication.getMainDexListResources()) {
				addToMainDexClasses(application, builder, MainDexListParser.parseList(resource, itemFactory));
			}
			if (!inputApplication.getMainDexClasses().isEmpty()) {
				addToMainDexClasses(
						application,
						builder,
						inputApplication.getMainDexClasses().stream()
								.map(clazz -> itemFactory.createType(DescriptorUtils.javaTypeToDescriptor(clazz)))
								.collect(Collectors.toList()));
			}
		}
		return builder.buildList();
	}

	/**
	 * @param application
	 * 		Container holding information about what is in the program.
	 * @param mainDexInfoBuilder
	 * 		Builder to append data to.
	 * @param types
	 * 		Types to iterate over.
	 */
	private static void addToMainDexClasses(@Nonnull DexApplication application,
											@Nonnull MainDexInfo.Builder mainDexInfoBuilder,
											@Nonnull Iterable<DexType> types) {
		InternalOptions options = application.options;
		for (DexType type : types) {
			DexProgramClass clazz = application.programDefinitionFor(type);
			if (clazz != null) {
				mainDexInfoBuilder.addList(clazz);
			} else if (!options.ignoreMainDexMissingClasses) {
				options.reporter.warning(new StringDiagnostic("Application does not contain '" +
						type.toSourceString() + "' as referenced in main-dex-list."));
			}
		}
	}

	private static volatile ClassFileResourceProvider systemJdkProvider;

	private static ClassFileResourceProvider systemJdkProvider() throws IOException {
		ClassFileResourceProvider systemJdkProvider = ApplicationData.systemJdkProvider;
		if (systemJdkProvider == null) {
			synchronized (ApplicationData.class) {
				systemJdkProvider = ApplicationData.systemJdkProvider;
				if (systemJdkProvider == null) {
					systemJdkProvider = JdkClassFileProvider.fromSystemJdk();
					ApplicationData.systemJdkProvider = systemJdkProvider;
				}
			}
		}
		return systemJdkProvider;
	}
}
