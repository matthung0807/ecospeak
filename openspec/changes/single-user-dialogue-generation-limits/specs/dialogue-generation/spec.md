# Spec Delta

## MODIFIED Requirements

### Requirement: Save valid generation with additive status metadata
Each valid new generation SHALL save the normalized scenario and generation-completion time and finalize its reserved usage in one transaction before responding. HTTP 200 SHALL preserve top-level topic, targetPhrase, and dialogue, with historySaved true and the saved historyId. Serialization, database, retention, or commit failures SHALL return non-success without unsaved generated content and release the reservation. Generation failures SHALL NOT create records or committed usage.

#### Scenario: Generation and save succeed
- **WHEN** a valid dialogue and its usage are committed atomically
- **THEN** HTTP 200 contains its existing dialogue fields plus historySaved true and its historyId.

#### Scenario: Saving fails after generation
- **WHEN** generation succeeds but the save transaction or dialogue serialization fails
- **THEN** a non-success response is returned without the newly generated content
- **AND** previous history remains unchanged and the quota reservation is released.

#### Scenario: Generation fails
- **WHEN** the provider request fails or has no parsed output
- **THEN** the existing generation failure propagates without an attempted history save
- **AND** the quota reservation is released.

#### Scenario: Finalizing usage fails
- **WHEN** history insertion succeeds within a transaction but usage finalization fails
- **THEN** the whole transaction rolls back, prior history and audio remain intact, and the reservation is released.
