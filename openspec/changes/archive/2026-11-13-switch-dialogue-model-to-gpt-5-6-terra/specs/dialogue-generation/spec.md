# Spec Delta

## MODIFIED Requirements

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
