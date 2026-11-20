# Tasks

## 1. Establish verification

- [x] 1.1 Evaluate style tooling on representative Java 25 production and test files, including four-space indentation, braces, annotations, and imports; record supported rules and gaps.
- Style checking is assigned to the user; configuring a checker is outside this change's implementation work.

## 2. Align production Java

- [x] 2.1 Apply the initial formatting pass to production Java, including four-space indentation and expanded compact statements.
- [x] 2.2 Align application, controller, DTO, and service Java files and compile the production sources.
- [x] 2.3 Align audio, history, and quota Java files and compile the production sources.

## 3. Align test Java

- [x] 3.1 Apply the initial formatting pass and separate inline field annotations in test Java.
- [x] 3.2 Align application, controller, DTO, and service test Java files and run the affected tests.
- [x] 3.3 Align audio and history test Java files and run the affected tests.

## 4. Integration verification

- [x] 4.1 Run the full Maven test suite after all changes: `mvn -q clean test` passed with 67 tests, no failures or errors, and one environment-gated live audio test skipped.
- [x] 4.2 Accept behavior verification: the user tested the UI and reported unchanged functionality. A complete diff review was not performed; the user requested completion on the basis of their verification.
