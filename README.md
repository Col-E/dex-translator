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

## Building

Currently, Dex Translator assumes the `R8` artifact is available in the local Maven repository.
In order to satisfy this assumption please clone our [R8 fork](https://github.com/Col-E/r8) and
run `gradlew publishToMavenLocal`.