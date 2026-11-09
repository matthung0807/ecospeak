# Dialogue Generation

## Purpose

Describe the existing server-side generation of an AI-selected English practice dialogue. This baseline records implemented behavior, including the distinction between prompt instructions and application validation.

## Requirements

### Requirement: Generate a dialogue without user input
The system SHALL expose `GET /api/dialogs/generate` without requiring a request body, scenario, or target phrase, and SHALL return the generated dialogue with HTTP 200 when generation succeeds. The system SHALL use its fixed prompt rather than incorporating user-supplied scenario or phrase parameters.

#### Scenario: Successful generation
- **WHEN** a client requests `GET /api/dialogs/generate` and the generation service returns a dialogue
- **THEN** the response is HTTP 200 with `topic`, `targetPhrase`, and `dialogue` fields
- **AND** each dialogue entry has `speaker` and `text` fields

### Requirement: Request structured AI output
The service SHALL call the OpenAI Responses API with model `gpt-5-mini`, the fixed English coaching prompt, and `DialogueResponse.class` as the structured output type. The service SHALL construct its client using `OpenAIOkHttpClient.fromEnv()`.

#### Scenario: Generation request
- **WHEN** generation is invoked
- **THEN** the service requests a structured response containing a topic string, a target phrase string, and a list of speaker/text entries
- **AND** no user-selected topic or target phrase is included in the prompt

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
