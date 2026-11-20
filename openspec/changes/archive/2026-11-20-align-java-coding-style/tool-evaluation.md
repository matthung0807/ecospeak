# Java style tooling evaluation

On JDK 25.0.1, google-java-format 1.28.0 was run in `--aosp` mode against `OpenAiSpeechProvider.java` and `AudioServiceTests.java` without writing either file. It parsed both samples and used four-space block indentation, expanded compact blocks and separated method annotations.

The output retained unbraced control statements, wildcard imports, and an inline field annotation; AOSP import ordering also differs from Google Java Style. The formatter therefore cannot by itself enforce the project rules. Its output does not establish support for every Java 25 language feature or every rule. A compatible checker still needs evaluation; any formatter use must be followed by checks and manual review for these gaps.
