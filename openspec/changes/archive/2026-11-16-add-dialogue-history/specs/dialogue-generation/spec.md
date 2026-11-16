# Spec Delta

## ADDED Requirements

### Requirement: Validate structure before saving generated content
After generation and before persistence or successful return, the backend SHALL require nonblank topic and target phrase strings and a nonempty dialogue list whose entries have nonblank speaker and text strings. Invalid content SHALL produce a non-success response without saving. These checks SHALL NOT enforce line count, A/B speaker values, target phrase occurrence, or language quality.

#### Scenario: Structurally invalid output
- **WHEN** generated output lacks any required field or contains a blank or null dialogue entry field
- **THEN** the request fails and history is unchanged

#### Scenario: Structurally valid content
- **WHEN** all required fields are complete
- **THEN** the output is eligible for saving even when a prompt-only content constraint is unmet

### Requirement: Save valid generation with additive status metadata
Each valid generation SHALL attempt one save with the normalized scenario and generation-completion time before responding. HTTP 200 SHALL preserve top-level `topic`, `targetPhrase`, and `dialogue`, adding `historySaved` and `historyId`. Successful saves SHALL return true and the saved UUID; serialization, database, or retention failures SHALL return false and null with the generated content intact. Generation failures SHALL NOT create records.

#### Scenario: Generation and save succeed
- **WHEN** a valid dialogue is generated and committed to history
- **THEN** HTTP 200 contains its existing dialogue fields plus `historySaved: true` and its `historyId`

#### Scenario: Saving fails after generation
- **WHEN** generation succeeds but the save transaction or dialogue serialization fails
- **THEN** HTTP 200 contains the valid generated dialogue, `historySaved: false`, and `historyId: null`
- **AND** previous history remains unchanged

#### Scenario: Generation fails
- **WHEN** the provider request fails or has no parsed output
- **THEN** the existing generation failure propagates without an attempted history save
