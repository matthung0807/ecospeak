# Spec Delta

## ADDED Requirements

### Requirement: Open history from the practice page
The practice page SHALL provide a `History` button navigating to `history.html`. Generation SHALL continue to display content on `index.html` without automatic navigation to a saved-dialogue page.

#### Scenario: Open history
- **WHEN** the user selects `History` on the practice page
- **THEN** the browser opens `history.html`

#### Scenario: Generate without leaving home
- **WHEN** a valid generated response arrives
- **THEN** the existing topic, phrase, and dialogue display updates on `index.html` without navigation

### Requirement: Warn when generated content could not be saved
For a valid generation response with `historySaved: false`, the practice page SHALL display `Dialogue generated, but could not be saved to history.` alongside the complete result. This warning SHALL NOT be treated as generation failure or prevent another generation. The warning SHALL initially be hidden and cleared when a subsequent valid generation starts. Existing invalid-response and generation-error handling SHALL remain intact.

#### Scenario: Save failure still displays result
- **WHEN** a valid response reports `historySaved: false`
- **THEN** the complete new dialogue replaces the old result and the save warning appears
- **AND** loading ends, the button is enabled and labeled `Generate Another`, and the scenario is retained

#### Scenario: Successful save after warning
- **GIVEN** a save warning is visible
- **WHEN** the user starts another valid generation and receives `historySaved: true`
- **THEN** the warning is cleared and the new dialogue is displayed normally

#### Scenario: Invalid content remains a generation failure
- **WHEN** a response fails the existing complete-dialogue validation
- **THEN** the existing generic error and preservation of any previous result apply rather than showing a save warning as success
