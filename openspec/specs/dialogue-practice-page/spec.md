# Dialogue Practice Page

## Purpose

Describe the existing single-page interface for generating and reading English practice dialogues.

## Requirements

### Requirement: Open the practice page
The system SHALL forward requests for `/` to `/index.html`. The page SHALL display the EcoSpeak heading, `Practice natural English in 3 minutes.` subtitle, a labeled editable scenario input with an example such as `Ordering coffee`, and a `Generate Dialogue` button. Loading, error, and result sections SHALL initially be hidden. The page SHALL provide no target phrase input control.

#### Scenario: Initial page
- **WHEN** a user opens `/`
- **THEN** the practice page displays its heading, subtitle, empty scenario input, example, and generation button
- **AND** no generated dialogue, loading indicator, or error is initially visible

### Requirement: Show generation progress
For a valid scenario, the page SHALL send `POST /api/dialogs/generate` with `Content-Type: application/json` and a JSON `scenario` containing the current input with surrounding whitespace removed. While pending, it SHALL disable the generation button, show `Generating...`, and hide previous errors. It SHALL leave an already visible result displayed while waiting.

#### Scenario: Generate another dialogue
- **GIVEN** a previously generated result is visible and the scenario input is valid
- **WHEN** the user starts another generation
- **THEN** the button is disabled and the loading indicator is visible
- **AND** the previous result remains visible while the request is pending
- **AND** the request contains the trimmed scenario value captured when generation starts

### Requirement: Display generated content
After a successful HTTP response, JSON processing, and complete response validation, the page SHALL display the topic, a section labeled `Target Phrase`, and ordered dialogue entries with the speaker followed by a colon and the entry text. It SHALL replace the previous dialogue entries, reveal the result section, and change the button label to `Generate Another`. Topic, target phrase, speaker, and dialogue text SHALL be inserted using `textContent`.

#### Scenario: Display a successful result
- **WHEN** a request returns a successful, valid dialogue response
- **THEN** the page shows its topic, target phrase, and dialogue entries in response order
- **AND** a subsequent successful generation replaces the displayed content
- **AND** generated text is rendered as text rather than interpreted as HTML

### Requirement: Report failures and allow another attempt
The page SHALL treat non-success HTTP status codes and invalid dialogue responses as failures and SHALL catch fetch, JSON parsing, and rendering exceptions. On failure it SHALL log the exception and show `Something went wrong. Please try again.`. After success or failure it SHALL hide the loading indicator and enable the button. Response validation SHALL precede all result updates so validation failures preserve the previous result.

#### Scenario: Request fails before rendering
- **GIVEN** an earlier result is visible
- **WHEN** the next fetch fails or returns a non-success HTTP status
- **THEN** the generic error is shown and the earlier result remains visible
- **AND** the loading indicator is hidden and the user can click the button again

#### Scenario: Response processing fails
- **WHEN** JSON parsing or rendering throws an exception
- **THEN** the page shows the generic error and enables another attempt
- **AND** a JSON parsing failure leaves any previous result unchanged
- **AND** unexpected rendering exceptions remain caught without requiring rollback of valid-response rendering

#### Scenario: Invalid response preserves previous success
- **GIVEN** a valid result is visible
- **WHEN** a subsequent response fails validation
- **THEN** the existing topic, target phrase, every dialogue entry, result visibility, and generation button label remain unchanged
- **AND** the generic error is shown, loading stops, the button is enabled, and the scenario input is retained

#### Scenario: Invalid first response
- **GIVEN** no successful result has been displayed
- **WHEN** the response fails validation
- **THEN** the result remains hidden and the button retains its initial label
- **AND** the generic error is shown, loading stops, and another attempt is available

#### Scenario: Retry succeeds after invalid response
- **GIVEN** a response failed validation
- **WHEN** the user retries with a valid scenario and receives a valid response
- **THEN** the previous error is hidden and the new complete result is displayed
- **AND** loading stops and the generation button is enabled

### Requirement: Reject blank scenario input
The page SHALL reject empty or whitespace-only scenario input before sending a generation request and SHALL show `Please enter a scenario.`. Invalid submission SHALL leave the input editable and any previous result visible, without showing a pending generation state.

#### Scenario: Empty input
- **WHEN** a user attempts to generate with an empty scenario
- **THEN** the page shows `Please enter a scenario.` and sends no backend request
- **AND** the input remains editable and no loading indicator is shown

#### Scenario: Whitespace-only input
- **GIVEN** a previous result is visible
- **WHEN** a user attempts to generate with only spaces or other whitespace
- **THEN** the same validation message is shown and no backend request is sent
- **AND** the previous result remains visible

### Requirement: Edit the scenario and generate again
The page SHALL retain the scenario after successful or failed generation and allow editing it before the next attempt. A subsequent generation SHALL use the current scenario and display a successful response through the existing topic, target phrase, and dialogue interface, replacing the previous result.

#### Scenario: Generate for a revised scenario
- **GIVEN** a dialogue for `Ordering coffee` is displayed
- **WHEN** the user changes the scenario to `Making an appointment` and generates again successfully
- **THEN** the new request contains `Making an appointment`
- **AND** the returned topic, target phrase, and dialogue replace the previous content
- **AND** the revised scenario remains in the input

#### Scenario: Retry after failure
- **GIVEN** generation failed and the existing generic failure message is shown
- **WHEN** the user edits the retained scenario and submits valid input
- **THEN** the page hides the previous error, sends the revised scenario, and allows another attempt after the request completes

### Requirement: Validate complete dialogue responses before display
Before changing displayed results, the page SHALL require a response object with nonblank string `topic` and `targetPhrase` fields and a nonempty `dialogue` array. Every entry SHALL be an object with nonblank string `speaker` and `text` fields. Missing, null, wrongly typed, empty, or whitespace-only required values SHALL cause failure. Additional fields SHALL be allowed.

#### Scenario: Invalid top-level response or fields
- **WHEN** a successful HTTP response contains JSON that is null, a primitive, an array, or an object with missing, null, wrongly typed, empty, or whitespace-only topic or targetPhrase
- **THEN** the page rejects it before changing any displayed result content
- **AND** it uses the existing failure message and allows retry

#### Scenario: Invalid dialogue collection
- **WHEN** a response has otherwise valid fields but dialogue is missing, null, not an array, or an empty array
- **THEN** the page rejects it without changing any previous result

#### Scenario: Invalid entry after a valid entry
- **GIVEN** a response has valid topic and targetPhrase and its first dialogue entry is valid
- **WHEN** a later entry is null, not an object, or has missing, null, wrongly typed, empty, or whitespace-only speaker or text
- **THEN** the entire response is rejected before any result content is changed

#### Scenario: Complete response with additional fields
- **WHEN** a response meets all required field checks and contains additional fields
- **THEN** it is accepted for display without requiring a specific speaker name, dialogue length beyond nonempty, or target phrase occurrence

## Implementation References

- `src/main/java/com/matthung/ecospeak/controller/HomeController.java`
- `src/main/resources/static/index.html`
