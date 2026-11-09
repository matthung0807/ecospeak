# Implementation Compared with the Product Proposal

## Basis and Scope

This comparison uses `docs/product_proposal.md`, `openspec/config.yaml`, and the current application source. Source code is the authority for implemented behavior. The baseline capability specifications under `openspec/specs/` record the behavior before `add-scenario-input` until its deltas are synchronized. This document includes the implemented change and records differences and limitations, not additional baseline requirements.

## MVP Requirements and User Stories

| Proposal reference | Actual implementation | Assessment |
|---|---|---|
| FR-01, US-01: enter a desired scenario or need; reject empty input | The page has a labeled scenario input and rejects blank input before sending. The backend independently rejects missing, null, empty, and whitespace-only scenarios with HTTP 400 before invoking generation. Both normalize boundary whitespace using ECMAScript trim semantics. | Implemented; US-01 is complete. |
| FR-02: submit a generation request | A button sends the current normalized scenario as POST JSON to `/api/dialogs/generate`; it is disabled while pending. The former GET returns 405. | Implemented for scenario-driven generation. |
| FR-03, US-02: generate a short dialogue based on user input | The page sends the entered scenario; the backend normalizes and validates it, and includes it as input separate from the coaching instructions in the OpenAI Responses API request using `gpt-5-mini` and structured output. | The missing input-dependent portion of FR-03 is implemented end to end. Conversation quality and length remain prompt-level constraints; this does not establish all US-02 quality criteria. |
| FR-04: natural, authentic, context-appropriate spoken English | The prompt requests realistic everyday conversation, natural language, and avoidance of complex or textbook wording. | Prompt-level support; no automatic quality validation or evidence of learning effectiveness. |
| FR-05, US-02: at most five sentences; A/B dialogue | The prompt requests 4–5 dialogue lines with A/B speakers. DTOs use unrestricted strings and a list, without application checks for length or speaker values. | Requested through the prompt, not enforced. Lines are not necessarily equivalent to sentences. |
| FR-06, US-03: Topic, Target Phrase, Dialogue on one page | Structured DTO fields are rendered on the page; another successful generation replaces them. | Implemented. |
| US-02 and AI quality principles: one target phrase appearing in the dialogue | The prompt asks the AI to choose exactly one phrase and include it naturally. There is no separate phrase input or occurrence check. | Prompt-level support. The proposal's open question about phrase selection is resolved by the current code in favor of AI selection. |
| FR-07, US-04: errors, invalid output handling, and retry | The browser checks HTTP status and catches request, parsing, and rendering failures; it shows a generic message and re-enables generation. The service throws if no parsed output exists. | Basic failure handling implemented. Structurally parsed content is not semantically validated; rendering errors may leave partial updates. |
| User flow: read and practice, then generate again | Users can read results, edit the retained scenario, and generate again. Failed requests retain input and prior results for another attempt. | Scenario entry, reading, and repeat generation implemented; speaking practice is self-directed and unmeasured. |

## Scope and Operational Observations

- The application is a Spring Boot web application with a static HTML/JavaScript interface. AI calls occur in the backend, as proposed.
- Structured output uses the Java DTO type rather than relying solely on a prompt asking for JSON. This supports parsing, but does not establish content quality or enforce the requested conversational constraints.
- The implementation contains no account system, dialogue history storage, scheduled deletion, reminders, TTS, separate character voices, speech recognition, pronunciation scoring, speaking correction, free-form AI chat, progress tracking, personalized recommendations, gamification, billing, or complete testing system. These absences align with the proposal's first-round MVP exclusions.
- The controller prints successful dialogue responses to standard output. Absence of application history storage is not a guarantee that generated content is absent from operational logs; log retention is not defined by this source.
- The service initializes its OpenAI client from the environment. `application.properties` also declares `openai.api-key=${OPENAI_API_KEY}`, but the service does not inject that property. The port uses `PORT`, defaulting to 8080.
- The application does not implement its own cost accounting, generation quotas, or latency measurement. The source does not establish actual API cost or response time.

## Product Validation and Evidence Limits

The proposal calls for a one-month study with 5–10 target users, pre/post scenario tests, interviews or questionnaires, and thresholds for practice, continued use, willingness to return, and pain-point recognition. No recruitment, study results, learning-outcome measurements, practice-completion events, user identity, retention analytics, or per-user generation metrics are present in the inspected implementation. Manual study collection remains compatible with the proposal; whether that study has occurred cannot be determined from this code.

Focused controller tests cover valid scenario propagation, response shape, invalid input rejection without service invocation, and the retired GET method. Request normalization tests cover ECMAScript trim whitespace and preserve other characters. Service tests capture successive outbound AI requests and cover absent parsed output. The context smoke test mocks the service so it needs no real provider credentials. These tests do not establish generated language quality or learning outcomes.

All 15 backend tests passed with Java 25 and no real API key. A headless Chrome walkthrough exercised the real page, controller, and service against a local stub provider, covering blank input, rendering, edited regeneration, pending state, failures, and retry. Captured provider requests confirmed current scenario propagation and structured output configuration. The walkthrough used synthetic dialogues, so it provides no evidence of live AI content quality. Details are recorded in `openspec/changes/add-scenario-input/verification.md`.

## Source Evidence

- Generation API: `src/main/java/com/matthung/ecospeak/controller/DialogueController.java`
- Prompt, model, client initialization, and output extraction: `src/main/java/com/matthung/ecospeak/service/DialogueService.java`
- Request and response shape: `src/main/java/com/matthung/ecospeak/dto/DialogueRequest.java`, `DialogueResponse.java`, and `DialogueLine.java`
- Page route: `src/main/java/com/matthung/ecospeak/controller/HomeController.java`
- UI behavior: `src/main/resources/static/index.html`
- Runtime configuration and dependencies: `src/main/resources/application.properties` and `pom.xml`
- Tests: `src/test/java/com/matthung/ecospeak/` (context, controller, request normalization, and service tests)
