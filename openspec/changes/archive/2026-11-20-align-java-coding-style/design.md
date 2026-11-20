# Design

## Context

See the proposal for motivation. Production and test sources contain compact control flow, multiple statements per line, wildcard imports and inline annotations; some files use tabs. The Maven project targets Java 25 and has no configured style checker. AGENTS.md adopts Google Java Style with project-specific overrides, including four-space indentation.

## Goals / Non-Goals

**Goals:**

- Make the style rules applicable consistently across maintained Java source and test files, while preserving behavior.
- Make the resulting broad diff reviewable and establish a reproducible verification approach.

**Non-Goals:**

- Change product behavior, public APIs, dependencies for runtime features, or non-Java formatting.
- Perform unrelated refactoring or rewrite tests.

## Decisions

### Apply the project's rules over the upstream guide

Use Google Java Style as the baseline, with AGENTS.md taking precedence where its rules differ. Apply the rules to both `src/main/java` and `src/test/java`, including imports, indentation, braces, annotations, and line wrapping. Preserve strings, comments' meaning, statement ordering, and control flow. Add braces where required without changing branch association. This is preferable to formatting only the most obvious violations, which would leave inconsistent rules across the repository.

### Formatting and style checking

A first pass used google-java-format in AOSP mode on production and test Java, followed by separating field annotations. The formatter leaves unbraced control statements, wildcard imports, and some single-line empty blocks, and its output alone does not establish compliance with all project rules. Complete the remaining alignment with care for control flow. The user will perform style checking; configuring a checker is outside the requested work.

### Keep the refactor reviewable

Inspect the diff in logical source groups for non-formatting changes, especially nested conditionals, lambdas, exception handling, imports, and test synchronization. The Maven test suite passed after the initial formatter pass, but before the field annotation edits; rerun relevant tests after remaining changes. Record environmental or external-service limitations rather than treating an unrun test as a pass.

## Risks / Trade-offs

- [Large diff obscures changes] → Review by package and separate unrelated work; avoid changing behavior.
- [Adding braces or changing imports alters semantics] → Inspect control flow and compile and run tests.
- [Tool defaults conflict with project rules or Java 25] → Test representative files first and document any manual checks and limitations.
- [Concurrent work creates merge conflicts] → Coordinate the formatting change and keep its scope isolated.

## Migration Plan

Apply the style changes in reviewable groups, run verification, and review the final diff before merging. No data migration or deployment change is needed; revert the formatting change if regression evidence requires rollback.
