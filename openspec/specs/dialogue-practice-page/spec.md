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
After a successful HTTP response and JSON processing, the page SHALL display the topic, a section labeled `Target Phrase`, and ordered dialogue entries with the speaker followed by a colon and the entry text. It SHALL replace the previous dialogue entries, reveal the result section, and change the button label to `Generate Another`. Topic, target phrase, speaker, and dialogue text SHALL be inserted using `textContent`.

#### Scenario: Display a successful result
- **WHEN** a request returns a successful, renderable dialogue response
- **THEN** the page shows its topic, target phrase, and dialogue entries in response order
- **AND** a subsequent successful generation replaces the displayed content
- **AND** generated text is rendered as text rather than interpreted as HTML

### Requirement: Report failures and allow another attempt
The page SHALL treat non-success HTTP status codes as failures and SHALL catch fetch, JSON parsing, and rendering exceptions. On failure it SHALL log the exception to the browser console and show `Something went wrong. Please try again.`. After success or failure it SHALL hide the loading indicator and enable the button. It SHALL not explicitly hide or restore a previous result on failure.

#### Scenario: Request fails before rendering
- **GIVEN** an earlier result is visible
- **WHEN** the next fetch fails or returns a non-success HTTP status
- **THEN** the generic error is shown and the earlier result remains visible
- **AND** the loading indicator is hidden and the user can click the button again

#### Scenario: Response processing fails
- **WHEN** JSON parsing or rendering throws an exception
- **THEN** the page shows the generic error and enables another attempt
- **AND** no rollback of any content already updated during rendering is performed

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

## Implementation References

- `src/main/java/com/matthung/ecospeak/controller/HomeController.java`
- `src/main/resources/static/index.html`
