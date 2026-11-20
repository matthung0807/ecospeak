# Proposal

## Why

The Java sources use inconsistent formatting, including compact blocks, inline annotations, and tabs, that conflicts with the coding style now documented in AGENTS.md. Aligning existing code will make it easier to read and maintain consistently.

## What Changes

- Review and align production and test Java sources with the Google Java Style Guide and the project's additional rules, using four spaces for indentation where they differ.
- Expand single-line blocks and statements, place declaration annotations on separate lines, and use the prescribed brace placement and readable formatting.
- Preserve behavior and public interfaces; limit edits to style compliance and avoid unrelated changes.
- The user will perform style checking; no style checker needs to be configured as part of this change.
- Verify the changes with the existing test suite and review the diff for unintended behavioral changes.

## Capabilities

### New Capabilities

None. This is a style-only maintenance change.

### Modified Capabilities

None. Existing product requirements and behavior remain unchanged; this change opts out of spec deltas using `skip_specs: true`.

## Impact

- Java files under `src/main/java` and `src/test/java` may be affected, resulting in a potentially broad formatting diff.
- A formatting pass has been applied across production and test Java; further alignment and review remain.
- No API, data model, or runtime behavior change is intended.
