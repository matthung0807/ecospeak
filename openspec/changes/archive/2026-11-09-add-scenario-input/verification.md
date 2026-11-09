# Verification

Verified on 2026-11-09 with Temurin Java 25.0.5, Maven wrapper 3.10.0, and headless Google Chrome.

## Backend

Command: `env -u OPENAI_API_KEY JAVA_HOME=/private/tmp/ecospeak-jdk25/Contents/Home ./mvnw -q test`.

All 15 tests passed: 10 controller cases, 2 request normalization tests, 2 service tests, and 1 context smoke test. No real API key or provider network access was needed by the tests. Maven's first run downloaded missing build dependencies; provider calls in the tests were mocked.

- Successful requests return the existing response structure and pass normalized input to the service.
- Missing bodies and missing/null/empty/whitespace-only scenarios return 400 without service invocation; GET returns 405.
- Java normalization matches the ECMAScript trim character set, preserves internal spaces, and preserves non-trim characters such as U+0085 and U+200B.
- Captured successive AI requests contain their respective scenarios in input, separate from the unchanged conversation constraints in instructions, with `gpt-5-mini` and the structured `DialogueResponse` type.
- Missing parsed output retains the existing exception behavior.
- The Spring context starts with a mocked service and without provider credentials.

## Browser and HTTP integration

Ran EcoSpeak on port 19192 with the production service initialized through `fromEnv()`, a dummy API key, and `OPENAI_BASE_URL=http://127.0.0.1:19191/v1`. A temporary Node HTTP stub on port 19191 captured actual SDK requests and returned structured synthetic dialogues, delayed responses, or a provider error. A temporary Playwright script exercised headless Chrome against this app. Eight check groups passed:

1. Initial page has a labeled empty input and example; result is hidden.
2. Empty, spaces-only, and ECMAScript-whitespace-only input shows the validation message with no page or provider request, no loading indicator, and an editable field.
3. `  Ordering coffee  ` sends trimmed POST JSON, reaches the real backend and stub, and displays the returned topic, phrase, and four A/B lines. The original input is retained.
4. Blank submission after success preserves the previous result without another request.
5. Editing to `Making an appointment` sends the revised value and replaces the result. A delayed response shows loading, disables the button, prevents duplicate generation, and leaves the earlier result visible while waiting.
6. A provider error shows the generic failure message, preserves input and result, hides loading, and restores the button. Editing and retrying succeeds and clears the error.
7. Four captured provider requests contain, in order, `Ordering coffee`, `Making an appointment`, `Ordering coffee`, and `Ordering coffee again`, with coaching instructions and a JSON schema output format.
8. Direct HTTP requests with invalid scenarios return 400; GET returns 405. The provider request count does not increase.

The resulting page screenshot was visually inspected. No persistence or production service was added. Live AI relevance, naturalness, and learning effectiveness were not assessed by synthetic responses.
