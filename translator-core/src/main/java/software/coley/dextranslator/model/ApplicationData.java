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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Summary of loaded application data.
 *
 * @author Matt Coley
 */
public class ApplicationData {
	private final AndroidApp inputApplication;
	private Supplier<Options> operationOptionsProvider = Options::new;
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
	 * 		When content could not be read from the inputs, or
	 * 		when the supporting {@link JdkClassFileProvider} cannot be provided.
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
	 * @param classes
	 * 		Program classes to wrap.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When the supporting {@link JdkClassFileProvider} cannot be provided.
	 */
	@Nonnull
	public static ApplicationData fromProgramClasses(@Nonnull Collection<DexProgramClass> classes) throws IOException {
		return fromProgramClasses(classes, new Options().setLenient(true));
	}

	/**
	 * @param classes
	 * 		Program classes to wrap.
	 * @param options
	 * 		Simplified input options.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When the supporting {@link JdkClassFileProvider} cannot be provided.
	 */
	@Nonnull
	public static ApplicationData fromProgramClasses(@Nonnull Collection<DexProgramClass> classes, @Nonnull Options options) throws IOException {
		return fromProgramClasses(classes, options.getInternalOptions());
	}

	/**
	 * @param classes
	 * 		Program classes to wrap.
	 * @param options
	 * 		Internal R8 options.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When the supporting {@link JdkClassFileProvider} cannot be provided.
	 */
	@Nonnull
	public static ApplicationData fromProgramClasses(@Nonnull Collection<DexProgramClass> classes, @Nonnull InternalOptions options) throws IOException {
		AndroidApp inputApplication = AndroidApp.builder()
				.addLibraryResourceProvider(systemJdkProvider())
				.build();
		LazyLoadedDexApplication.Builder builder = DexApplication.builder(options, Timing.empty())
				.addProgramClasses(classes);
		builder.setFlags(DexApplicationReadFlags.builder().build());
		LazyLoadedDexApplication application = builder.build();
		return new ApplicationData(inputApplication, application);
	}

	/**
	 * @param dexFile
	 * 		DEX file content to read.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromDex(@Nonnull byte[] dexFile) throws IOException {
		return fromDex(dexFile, new Options().setLenient(true));
	}

	/**
	 * @param dexFile
	 * 		DEX file content to read.
	 * @param options
	 * 		Simplified input options.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromDex(@Nonnull byte[] dexFile, @Nonnull Options options) throws IOException {
		return fromDex(dexFile, options.getInternalOptions());
	}

	/**
	 * @param dexFile
	 * 		DEX file content to read.
	 * @param options
	 * 		Internal R8 options.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromDex(@Nonnull byte[] dexFile, @Nonnull InternalOptions options) throws IOException {
		Inputs inputs = new Inputs().addDex(dexFile);
		return from(inputs, options);
	}

	/**
	 * @param classFile
	 * 		Bytecode of class to read.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromClass(@Nonnull byte[] classFile) throws IOException {
		return fromClass(classFile, new Options().setLenient(true));
	}

	/**
	 * @param classFile
	 * 		Bytecode of class to read.
	 * @param options
	 * 		Simplified input options.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromClass(@Nonnull byte[] classFile, @Nonnull Options options) throws IOException {
		return fromClass(classFile, options.getInternalOptions());
	}

	/**
	 * @param classFile
	 * 		Bytecode of class to read.
	 * @param options
	 * 		Internal R8 options.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromClass(@Nonnull byte[] classFile, @Nonnull InternalOptions options) throws IOException {
		Inputs inputs = new Inputs().addJvmClass(classFile);
		return from(inputs, options);
	}

	/**
	 * @param classFiles
	 * 		Collection of classes to read.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromClasses(@Nonnull Collection<byte[]> classFiles) throws IOException {
		return fromClasses(classFiles, new Options().setLenient(true));
	}

	/**
	 * @param classFiles
	 * 		Collection of classes to read.
	 * @param options
	 * 		Simplified input options.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromClasses(@Nonnull Collection<byte[]> classFiles, @Nonnull Options options) throws IOException {
		return fromClasses(classFiles, options.getInternalOptions());
	}

	/**
	 * @param classFiles
	 * 		Collection of classes to read.
	 * @param options
	 * 		Internal R8 options.
	 *
	 * @return Application data of the content.
	 *
	 * @throws IOException
	 * 		When content could not be read from the inputs.
	 */
	@Nonnull
	public static ApplicationData fromClasses(@Nonnull Collection<byte[]> classFiles, @Nonnull InternalOptions options) throws IOException {
		Inputs inputs = new Inputs().addJvmClasses(classFiles);
		return from(inputs, options);
	}

	/**
	 * @param optionsForView
	 * 		New options to utilize for the created {@link AppView}.
	 * @param filter
	 * 		Class filter to apply, used for limiting the visibility to classes within the view.
	 * 		This can be useful when the view is used in a conversion process where only some classes
	 * 		are to be converted, rather than the whole application.
	 *
	 * @return View of the application.
	 */
	@Nonnull
	public AppView<AppInfo> createView(@Nonnull InternalOptions optionsForView, @Nonnull ClassFilter filter) {
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

		// Assign filter to app-info.
		// This limits the ability of anything operating on the view/info to access classes not matched by the filter.
		// If we only want to convert/export a few classes this filter will allow us to do that.
		info.setFilter(filter);

		// Wrap in view
		return AppView.createForD8(info);
	}

	/**
	 * @param internalName
	 * 		Internal name of class to export.
	 *
	 * @return JVM bytecode of the requested class.
	 * If the class is not found in the application, will be {@code null}.
	 *
	 * @throws ConversionIRReplacementException
	 * 		When replacing {@link CfCode} of input classes fails.
	 * @throws ConversionD8ProcessingException
	 * 		When D8 conversion internals fails.
	 * @throws ConversionExportException
	 * 		When conversion exporting fails.
	 */
	@Nullable
	public byte[] exportToJvmClass(@Nonnull String internalName) throws ConversionIRReplacementException,
			ConversionD8ProcessingException, ConversionExportException {
		return exportToJvmClassMap(ClassFilter.forType(internalName)).get(internalName);
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
		return exportToJvmClassMap(ClassFilter.PASS_ALL);
	}

	/**
	 * @param filter
	 * 		Filter to limit which classes are exported.
	 *
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
	public Map<String, byte[]> exportToJvmClassMap(@Nonnull ClassFilter filter) throws ConversionIRReplacementException,
			ConversionD8ProcessingException, ConversionExportException {
		Map<String, byte[]> result = new HashMap<>();

		// Our temporary options to dictate exporting to JVM class files.
		Options exportOptions = operationOptionsProvider.get();
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
		Conversion.convert(this, exportOptions.getInternalOptions(), filter, false);
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
		Options exportOptions = operationOptionsProvider.get();
		exportOptions.setApiLevel(application.options.getMinApiLevel());
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
		Conversion.convert(this, exportOptions.getInternalOptions(), ClassFilter.PASS_ALL, false);
		byte[] resultUnwrapped = result[0];
		if (resultUnwrapped == null)
			throw new ConversionExportException(new IllegalStateException("No DEX file was observed by consumer"), false);
		return resultUnwrapped;
	}

	/**
	 * Updates the {@link #getApplication() application} to replace a prior entry of the given class,
	 * with the new instance provided.
	 *
	 * @param classFile
	 * 		Class file to update the application with.
	 *
	 * @return Original class instance replaced. May be {@code null} if no class by the name existed previously.
	 *
	 * @throws IOException
	 * 		Propagated from {@link #fromClass(byte[])}
	 */
	@Nullable
	public DexProgramClass updateClass(@Nonnull byte[] classFile) throws IOException {
		Map<String, DexProgramClass> map = updateClasses(fromClass(classFile));
		if (map.isEmpty()) return null;
		return map.values().iterator().next();
	}

	/**
	 * Updates the {@link #getApplication() application} to replace a prior entry of the given class,
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
	 * Updates the {@link #getApplication() application} to replace prior entries of the given classes,
	 * with the new instances provided.
	 *
	 * @param classFiles
	 * 		Collection of class files to update the application with.
	 *
	 * @return Original class instances replaced. Values may be {@code null} if no class by the name existed previously.
	 *
	 * @throws IOException
	 * 		Propagated from {@link #fromClasses(Collection)}
	 * @see #updateClasses(ApplicationData) Delegated call with
	 * {@link ApplicationData#fromClasses(Collection)} on the parameter.
	 */
	@Nonnull
	public Map<String, DexProgramClass> updateClasses(@Nonnull Collection<byte[]> classFiles) throws IOException {
		return updateClasses(fromClasses(classFiles));
	}

	/**
	 * Updates the {@link #getApplication() application} to replace a prior entry of the given class,
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
	 * Takes all the program classes in the given application model, and puts them in our model.
	 *
	 * @param data
	 * 		Application data to pull classes from.
	 *
	 * @return Original class instances replaced. Values may be {@code null} if no class by the name existed previously.
	 *
	 * @see #updateClasses(Map) Delegated call with {@link ApplicationData#toClassMap()} on the parameter.
	 */
	@Nonnull
	public Map<String, DexProgramClass> updateClasses(@Nonnull ApplicationData data) {
		return updateClasses(data.toClassMap());
	}

	/**
	 * Updates the {@link #getApplication() application} to replace prior entries of the given classes,
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
	 * @return Map of internal names, to program classes in the application.
	 */
	@Nonnull
	public Map<String, DexProgramClass> toClassMap() {
		return application.classes().stream()
				.collect(Collectors.toMap(DexClass::getTypeName, c -> c));
	}

	/**
	 * @return Sorted set of class names in the application.
	 */
	@Nonnull
	public SortedSet<String> getClassNames() {
		return application.classes().stream()
				.map(DexClass::getTypeName)
				.collect(Collectors.toCollection(TreeSet::new));
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
	 * @return Provider to supply an {@link Options} instance for export operations.
	 * For exporting to JVM bytecode for instance you may want to supply an
	 * options configured with {@link Options#enableLoadStoreOptimization()}.
	 */
	@Nonnull
	public Supplier<Options> getOperationOptionsProvider() {
		return operationOptionsProvider;
	}

	/**
	 * @param operationOptionsProvider
	 * 		Provider to supply an {@link Options} instance for export operations.
	 * 		For exporting to JVM bytecode for instance you may want to supply an
	 * 		options configured with {@link Options#enableLoadStoreOptimization()}.
	 */
	public void setOperationOptionsProvider(@Nonnull Supplier<Options> operationOptionsProvider) {
		this.operationOptionsProvider = operationOptionsProvider;
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
	 * Closes class and resource providers within the {@link #getInputApplication()}.
	 *
	 * @throws IOException
	 * 		When closing a resource provider encountered issues.
	 */
	public void close() throws IOException {
		inputApplication.signalFinishedToProviders(null);
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
