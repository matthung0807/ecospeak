# Tasks

## 1. Scenario request and generation

- [x] 1.1 Add the scenario request DTO, replace the controller GET mapping with POST JSON, validate and normalize input before service invocation, and pass it to `generate(String scenario)`. Verify with controller tests that valid input returns the existing response fields, surrounding whitespace is removed, absent bodies and missing/null/empty/whitespace-only values return 400 without invoking the service, and GET returns 405. Include tabs, line breaks, nonbreaking spaces, and ideographic spaces in whitespace cases.
- [x] 1.2 Include the current scenario as clearly separated context in the existing AI request while retaining the model, structured response, coaching constraints, and output extraction. Add the minimal client injection seam needed for isolated service tests; verify captured outbound requests contain the correct scenario on two successive calls and retain existing instructions, with no live AI calls.
- [x] 1.3 Document the POST JSON contract and validation responses in README.md, and update the backend findings in docs/implementation-comparison.md. Verify examples match the controller tests and distinguish prompt instructions from enforced response validation.

## 2. Scenario entry and regeneration

- [x] 2.1 Add a labeled editable scenario field and example to index.html; validate trimmed input before loading or fetch, with `Please enter a scenario.` for blank input. Verify in a browser that empty, spaces-only, and Unicode-whitespace-only submissions send no requests, retain any prior result, and leave the input usable.
- [x] 2.2 Send the captured current scenario using POST JSON and retain the input after every attempt. Reuse loading, button restoration, generic backend failure handling, and safe result rendering. Verify with stub responses that `Ordering coffee` renders through the existing interface, changing to `Making an appointment` sends the revised value and replaces the result, pending requests disable duplicate submission, and failures allow editing and retry.
- [x] 2.3 Update the page findings in docs/implementation-comparison.md and the user flow in README.md. Verify documentation describes the implemented input-to-dialogue flow and marks US-01 and only the missing input-dependent portion of FR-03 as addressed.

## 3. Integration verification

- [x] 3.1 Run `./mvnw test` and resolve any regressions; verify focused request and service tests pass without network access or real provider credentials.
- [x] 3.2 Walk through the page-to-controller-to-service flow with a stubbed provider: valid scenario, blank rejection, returned dialogue display, edited scenario, and failure/retry. Record the observed results and confirm the scenario reaches the outbound generation request without introducing storage or another external service.
