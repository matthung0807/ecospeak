# Proposal

## Why

EcoSpeak currently generates an AI-selected dialogue from a fixed prompt, so learners cannot practice a scenario they choose. This change completes US-01 and the missing input-dependent portion of FR-03 by connecting scenario entry to the existing generation and display flow.

## What Changes

- Add a labeled scenario input with an example such as “Ordering coffee.”
- Reject empty and whitespace-only input in the page and backend before generation.
- Send the current scenario to the backend and include it in the AI generation request.
- Reuse the existing topic, target phrase, dialogue, loading, and failure interface.
- Preserve the editable scenario after each attempt so users can generate again with revised input.
- **BREAKING**: Replace the input-free `GET /api/dialogs/generate` contract with `POST /api/dialogs/generate` accepting JSON `{ "scenario": "Ordering coffee" }`. Missing, null, empty, or whitespace-only scenarios return HTTP 400 without calling AI.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dialogue-generation`: Require a valid scenario and incorporate it into structured dialogue generation.
- `dialogue-practice-page`: Accept and validate a scenario, submit it, and allow editing and regeneration using the existing result display.

## Impact

- Affects `src/main/resources/static/index.html`, `DialogueController`, `DialogueService`, and a new scenario request DTO, with focused backend tests and page verification.
- Response fields and existing AI model, provider, and conversation constraints remain applicable.
- No storage, new external service, target phrase input, or broader AI quality validation is included.
- Existing baseline specs remain unchanged during planning; delta specs describe the proposed behavior. Update the implementation comparison when implementation is complete.
