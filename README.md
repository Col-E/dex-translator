# Dex Translator

A translation library and command-line-interface tool for converting between JVM and Dalvik bytecode.

## Command line tool usage

Top level:
```
Usage: dex-translator [-hV] [COMMAND]
See the sub-commands for available operations
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  d2j  Convert one or more DEX files to a JAR file
  j2d  Convert one or more JAR files to an DEX file

d2j:
  Usage: d2j [-fl] [-o=<outputFile>] <inputFiles>...
  Convert one or more DEX files to a JAR file
        <inputFiles>...      Path to one or more DEX files.
    -f, --force              Flag to enable force emitting output, even if some
                               method bodies are invalid. Invalid methods will be
                               replaced with no-op behavior.
    -l, --lenient            Flag to enable options that allow more leniency in
                               the conversion process. Some input validation will
                               be skipped.
    -o, --out=<outputFile>   Path to JAR file to write to.

jd2:
  Usage: j2d [-fl] [-o=<outputFile>] <inputFiles>...
  Convert one or more JAR files to an DEX file
        <inputFiles>...      Path to one or more JAR files.
    -f, --force              Flag to enable force emitting output, even if some
                               method bodies are invalid. Invalid methods will be
                               replaced with no-op behavior.
    -l, --lenient            Flag to enable options that allow more leniency in
                               the conversion process. Some input validation will
                               be skipped.
    -o, --out=<outputFile>   Path to DEX file to write to.
```

## Library usage

Maven dependency:
```xml
<dependency>
    <groupId>software.coley</groupId>
    <artifactId>dex-translator</artifactId>
    <version>${dexVersion}</version> <!-- See release page for latest version -->
</dependency>
```

Gradle dependency:
```groovy
implementation group: 'software.coley', name: 'dex-translator', version: dexVersion
implementation "software.coley:dex-translator:${dexVersion}"
```

For most basic usage you can use the `Converter` and `Loader` types in types package. Using these looks like this:
```java
Inputs inputs = new Inputs();
for (File inputFile : inputFiles)
	inputs.addJarArchive(inputFile.toPath());
Options options = new Options()
		.setReplaceInvalidMethodBodies(replaceInvalid)
		.setLenient(lenient)
		.setDexFileOutput(outputFile.toPath());
new Converter()
		.setInputs(inputs)
		.setOptions(options)
		.run()
```

For more in-depth usage you will likely want to work directly with the `ApplicationData` class.
You can find example usage of it among the test classes.

## Building

Currently, Dex Translator assumes the `R8` artifact is available in the local Maven repository.
In order to satisfy this assumption please clone our [R8 fork](https://github.com/Col-E/r8) and
run `gradlew publishToMavenLocal`.