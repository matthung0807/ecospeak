# Dialogue Practice Page

## Purpose

Describe the existing single-page interface for generating and reading English practice dialogues.

## Requirements

### Requirement: Open the practice page
The system SHALL forward requests for `/` to `/index.html`. The page SHALL display the EcoSpeak heading, `Practice natural English in 3 minutes.` subtitle, and a `Generate Dialogue` button. Loading, error, and result sections SHALL initially be hidden. The page SHALL provide no scenario or target phrase input controls.

#### Scenario: Initial page
- **WHEN** a user opens `/`
- **THEN** the practice page displays its heading, subtitle, and generation button
- **AND** no generated dialogue, loading indicator, or error is initially visible

### Requirement: Show generation progress
The page SHALL issue a fetch request to `/api/dialogs/generate` using the default GET method when its generation button is clicked. While the request is pending, it SHALL disable the button, show `Generating...`, and hide any previous error. It SHALL leave an already visible result displayed while waiting.

#### Scenario: Generate another dialogue
- **GIVEN** a previously generated result is visible
- **WHEN** the user starts another generation
- **THEN** the button is disabled and the loading indicator is visible
- **AND** the previous result remains visible while the request is pending

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

## Implementation References

- `src/main/java/com/matthung/ecospeak/controller/HomeController.java`
- `src/main/resources/static/index.html`
