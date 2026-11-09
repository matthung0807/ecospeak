# Design

## Context

See proposal.md for motivation. The static page currently calls an input-free GET endpoint; `DialogueController.generate()` invokes `DialogueService.generate()`, which sends a fixed prompt to the OpenAI Responses API. The page already renders `topic`, `targetPhrase`, and speaker/text entries safely using `textContent`, and supports loading, failures, and repeated generation. The only existing test is a Spring application context smoke test.

The change crosses the page, HTTP boundary, and service, so an explicit request contract and validation strategy are needed. Existing baseline specs intentionally describe current behavior; only change-local deltas introduce the planned behavior.

## Goals / Non-Goals

**Goals:** Carry one current scenario through the existing request pipeline, prevent blank requests from consuming AI calls, and preserve the established response and rendering behavior.

**Non-Goals:** Persistence, target phrase selection controls, new services, model changes, and post-generation language-quality enforcement.

## Decisions

1. **Use POST JSON on the existing generation path.** Add a `DialogueRequest` DTO with a string `scenario`, replace the GET mapping with POST, and update the page in the same release. A body gives the scenario a clear contract without placing free text in the URL. Alternatives were a GET query parameter or retaining an input-free fallback; neither is needed for this first-party page, and the fallback would bypass required input. This is an intentional breaking API change; GET will return 405.

2. **Validate and normalize at the HTTP boundary.** Reject missing bodies through Spring request binding; explicitly reject missing/null/blank scenario values with HTTP 400 before invoking the service. Trim surrounding whitespace and pass the normalized value into `generate(String scenario)`. Use explicit validation rather than adding a validation dependency for one field. The page independently trims and checks input and displays `Please enter a scenario.` without sending a request. Use ECMAScript `String.prototype.trim()` semantics on both sides: remove U+0009–U+000D, U+0020, U+00A0, U+1680, U+2000–U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, and U+FEFF only at the boundaries. Preserve internal whitespace. Java implements this character set explicitly rather than relying on `trim()` or `strip()`. No language detector or arbitrary length limit is introduced.

3. **Add scenario context to the existing generation instructions.** Retain the current model, structured output type, conversation constraints, and first-output extraction. Clearly separate scenario text from the coaching instructions and instruct the model to base the conversation on that scenario. Treat scenario text as user context rather than permission to override the output instructions. The AI continues choosing the target phrase. Do not concatenate input into executable code or HTML.

4. **Reuse the page state and result renderer.** Add a visible label and empty input with an example placeholder. Capture the current trimmed value for each POST. Retain the field value after success or failure, keep the generation button disabled during a request, and restore it in `finally`. Preserve prior-result visibility while pending or on request failure and use the existing generic failure message for backend/network failures. Local validation uses a specific message that is cleared on the next valid submission. Existing safe text rendering and result replacement remain unchanged.

5. **Verify with isolated tests and a page walkthrough.** Add controller tests with a mocked service for valid propagation, invalid inputs without service invocation, and retired GET behavior. Capture the actual outbound structured AI request using a mocked client; a small package-private client-injection constructor can preserve the production no-argument initialization path. Assert that the submitted scenario and existing coaching constraints reach the request, including a different scenario on a second call. This avoids paid or nondeterministic API calls. Mock the service in the existing context smoke test as well, so the full suite needs no real provider credentials. Verify browser input, POST payload, rendering, retry, and regeneration with stub responses where available.

## Risks / Trade-offs

- [Existing GET callers break] → Update the first-party page atomically with the controller and document the POST contract. No other callers were found in the inspected source.
- [Frontend and backend disagree on whitespace] → Use shared acceptance examples in page verification and parameterized backend tests.
- [Model ignores the scenario or embedded input changes instructions] → Clearly separate instructions and context; check request construction deterministically and use an optional manual live example to assess relevance. Prompt construction cannot guarantee semantic quality.
- [Tests accidentally require provider credentials] → Mock the service/client and keep tests independent of network access.

## Migration Plan

Deploy the updated page, DTO, controller, and service together. No data migration is needed. Roll back the same files together to restore the original GET flow. Cached old pages may need a refresh after deployment.
