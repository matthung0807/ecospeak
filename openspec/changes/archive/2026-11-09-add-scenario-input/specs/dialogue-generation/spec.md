# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Generate a dialogue without user input`
- TO: `### Requirement: Generate a dialogue from a scenario`

## MODIFIED Requirements

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
The service SHALL call the OpenAI Responses API with model `gpt-5-mini`, the English coaching instructions and the validated scenario, and `DialogueResponse.class` as the structured output type. The service SHALL construct its client using `OpenAIOkHttpClient.fromEnv()`.

#### Scenario: Generation request
- **WHEN** generation is invoked with a valid scenario
- **THEN** the request instructs the AI to generate the conversation for that scenario
- **AND** it requests a structured response containing a topic string, a target phrase string, and a list of speaker/text entries
- **AND** the target phrase is selected by the AI rather than supplied by the user

#### Scenario: Updated scenario reaches generation
- **WHEN** a subsequent valid request supplies `Making an appointment` after an earlier `Ordering coffee` request
- **THEN** the subsequent AI request includes `Making an appointment` as its scenario rather than reusing the previous scenario
