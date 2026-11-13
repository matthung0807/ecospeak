# Dialogue Generation

## Purpose

Describe the existing server-side generation of an AI-selected English practice dialogue. This baseline records implemented behavior, including the distinction between prompt instructions and application validation.

## Requirements

### Requirement: Generate a dialogue from a scenario
The system SHALL expose `POST /api/dialogs/generate` accepting a JSON object with a required string `scenario`. It SHALL trim surrounding whitespace and return HTTP 200 with the generated dialogue on success. Missing, null, empty, or whitespace-only scenarios SHALL return HTTP 400 before any AI request. The input-free GET endpoint SHALL no longer generate dialogues.

#### Scenario: Successful generation
- **WHEN** a client posts `{ "scenario": "Ordering coffee" }` and generation succeeds
- **THEN** the response is HTTP 200 with `topic`, `targetPhrase`, and `dialogue` fields
- **AND** each dialogue entry has `speaker` and `text` fields

#### Scenario: Reject invalid scenario
- **WHEN** a client posts an absent body, an object without `scenario`, or a null, empty, or whitespace-only scenario
- **THEN** the response is HTTP 400 and no AI generation request is sent

#### Scenario: Normalize surrounding whitespace
- **WHEN** a client posts `{ "scenario": "  Ordering coffee  " }`
- **THEN** generation uses `Ordering coffee` as the scenario

#### Scenario: Retire input-free generation
- **WHEN** a client requests `GET /api/dialogs/generate`
- **THEN** the response is HTTP 405 and no AI generation request is sent

### Requirement: Request structured AI output
The service SHALL call the OpenAI Responses API with model `gpt-5.6-terra`, the English coaching instructions and the validated scenario, and `DialogueResponse.class` as the structured output type. The service SHALL construct its client using `OpenAIOkHttpClient.fromEnv()`.

#### Scenario: Generation request
- **WHEN** generation is invoked with a valid scenario
- **THEN** the request instructs the AI to generate the conversation for that scenario
- **AND** it requests a structured response containing a topic string, a target phrase string, and a list of speaker/text entries
- **AND** the target phrase is selected by the AI rather than supplied by the user

#### Scenario: Updated scenario reaches generation
- **WHEN** a subsequent valid request supplies `Making an appointment` after an earlier `Ordering coffee` request
- **THEN** the subsequent AI request includes `Making an appointment` as its scenario rather than reusing the previous scenario

### Requirement: Instruct the model to produce short natural conversation
The prompt SHALL request one short, realistic everyday English conversation for upper-intermediate learners, exactly two participants named A and B, 4–5 dialogue lines, one speaker per line, and exactly one natural, useful target phrase appearing naturally in the dialogue. It SHALL request a short descriptive topic, focus on practicing the phrase, conversational English, and avoidance of unnecessarily complicated vocabulary and textbook-style expressions.

#### Scenario: Prompt-based content constraints
- **WHEN** the service builds a generation request
- **THEN** it includes these conversation instructions
- **AND** the application does not perform a subsequent check of line count, speaker values, phrase occurrence, grammar, or naturalness before returning the parsed response

### Requirement: Select the first structured output or propagate failure
The service SHALL return the first available parsed output-text value found by traversing response output items, messages, and content. If none exists, it SHALL throw `IllegalStateException` with message `OpenAI returned no dialogue response`. Service failures SHALL propagate from the controller without an application-defined fallback dialogue or custom error response contract.

#### Scenario: Multiple output values
- **WHEN** the AI response contains multiple parsed output-text values
- **THEN** the service returns the first value

#### Scenario: No dialogue output
- **WHEN** the AI response contains no parsed output-text value
- **THEN** the service throws the specified exception instead of returning a successful dialogue

## Implementation References

- `src/main/java/com/matthung/ecospeak/controller/DialogueController.java`
- `src/main/java/com/matthung/ecospeak/service/DialogueService.java`
- `src/main/java/com/matthung/ecospeak/dto/DialogueResponse.java`
- `src/main/java/com/matthung/ecospeak/dto/DialogueLine.java`
