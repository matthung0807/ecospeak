# Spec Delta

## Purpose

Allow the localhost user to revisit the ten newest saved practice dialogues during the current application lifetime without regenerating content.

## ADDED Requirements

### Requirement: Store complete dialogue records in memory
The system SHALL store UUID, UTC generation-completion timestamp, topic, target phrase, normalized user scenario, and ordered speaker/text lines for each saved generation in in-memory H2. Generated dialogue history SHALL NOT be written to files. History SHALL be discarded on application shutdown. History SHALL have no user partitioning or authentication requirement.

#### Scenario: Save complete content
- **WHEN** a valid dialogue is saved for a normalized scenario
- **THEN** its record contains all specified fields and preserves every line and its position

#### Scenario: Restart clears history
- **GIVEN** saved records exist
- **WHEN** the application shuts down and starts again
- **THEN** history is empty and earlier record UUIDs are not found
- **AND** no generated dialogue storage files were created

### Requirement: Retain the newest ten records atomically
The system SHALL retain at most ten committed records. Each save SHALL atomically insert the record and remove records outside the newest ten. Ordering SHALL use creation time descending, followed by canonical UUID string descending for equal timestamps. Reads and retention SHALL use the same ordering. Concurrent saves SHALL preserve this limit and rollback SHALL preserve previous history.

#### Scenario: Eleventh record evicts oldest
- **GIVEN** ten saved records exist
- **WHEN** another record is successfully saved
- **THEN** exactly the newest ten records remain and the removed UUID is no longer retrievable

#### Scenario: Concurrent saves and tied timestamps
- **WHEN** concurrent saves produce more than ten records, including equal timestamps
- **THEN** committed history contains the newest ten under the defined timestamp and UUID ordering
- **AND** repeated reads return the same order and no read observes an intermediate eleventh record

#### Scenario: Save transaction fails
- **WHEN** insertion or retention cleanup fails
- **THEN** both operations are rolled back and the previous records remain unchanged

#### Scenario: Duplicate topics
- **WHEN** two successful saves have identical topics
- **THEN** they have distinct UUIDs and can each be retrieved while retained

### Requirement: Retrieve saved history without generation
The system SHALL expose `GET /api/dialogs/history` returning HTTP 200 with an array of retained summaries containing `id`, `createdTime`, and `topic` in newest-first order. `GET /api/dialogs/history/{id}` SHALL return HTTP 200 with the saved metadata, scenario, target phrase, and ordered dialogue. Unknown UUIDs SHALL return 404 and malformed UUIDs SHALL return 400. Read failures SHALL return non-success status. Reads SHALL NOT invoke AI generation.

#### Scenario: Empty and populated list
- **WHEN** history is requested
- **THEN** an empty database returns `[]` and a populated database returns at most ten ordered summaries

#### Scenario: Retrieve by UUID
- **WHEN** a retained UUID is requested
- **THEN** its original content is returned without generating another conversation

#### Scenario: Missing or malformed record
- **WHEN** a detail request uses an unknown UUID or a malformed UUID
- **THEN** it returns 404 or 400 respectively without an AI request

#### Scenario: Database read fails
- **WHEN** a history read cannot complete because storage is unavailable
- **THEN** a non-success response is returned rather than an empty successful history

### Requirement: Browse history topics
`history.html` SHALL load the history list and display clickable topics in returned order without pagination. Each item SHALL open `dialogue.html?id=<uuid>`. A `Go back` button SHALL navigate to `index.html`. The page SHALL distinguish loading, empty history, and retrieval failure, render topics as text, and keep navigation available in every state.

#### Scenario: Select a topic
- **WHEN** the history list loads and the user selects a topic
- **THEN** the page opens the selected record UUID even if other topics match

#### Scenario: History states and navigation
- **WHEN** the list is pending, returns empty, or fails
- **THEN** the page displays the corresponding loading, empty, or error state
- **AND** `Go back` navigates to `index.html`

### Requirement: Read saved dialogue content
`dialogue.html` SHALL retrieve its UUID from the `id` query parameter and show the saved topic, target phrase, and ordered speaker/text lines as text. It SHALL provide `Go back` to `history.html` and `Home` to `index.html`. It SHALL distinguish loading, missing/invalid selection, record not found, and retrieval failure without showing partial content or initiating generation. Navigation SHALL remain available in every state.

#### Scenario: Read and navigate
- **WHEN** a retained record opens
- **THEN** its topic, phrase, and dialogue lines appear in saved order with speaker labels
- **AND** `Go back` opens history and `Home` opens the index page

#### Scenario: Expired link
- **WHEN** a record has been evicted or disappeared after shutdown
- **THEN** its page shows a record-not-found message and both navigation buttons

#### Scenario: Pending or failed detail request
- **WHEN** a detail request is pending or fails for a reason other than 404
- **THEN** the page shows a loading or retrieval-error state respectively

#### Scenario: Invalid selection or response
- **WHEN** the URL lacks a valid UUID or the response lacks complete dialogue content
- **THEN** the page shows an invalid-selection or retrieval-error state respectively without displaying partial dialogue
