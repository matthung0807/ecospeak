# Proposal

## Why

US-04 requires incomplete responses not to appear as successful results. The current page updates topic and target phrase before processing all dialogue entries, so malformed responses can overwrite or partially replace a previous valid result.

## What Changes

- Validate the complete response before updating displayed results: topic and targetPhrase must be nonblank strings, and dialogue must be a nonempty array of entries with nonblank string speaker and text fields.
- Treat invalid responses as failures using the existing error message; preserve the previous result, keep an initially hidden result hidden, and allow retry.
- Preserve the existing successful display, text-only rendering, loading state, and scenario workflow.
- Limit implementation to the browser response handling; no backend, model, prompt, API, dependency, or visual redesign changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dialogue-practice-page`: Validate responses before display and prevent incomplete responses from changing a previous valid result.

## Impact

Implementation targets `src/main/resources/static/index.html`. Existing Java tests do not exercise browser rendering; browser verification will use mocked API responses through the playwright-tester subagent with Playwright MCP and screenshots, as required by AGENTS.md.

The existing baseline explicitly describes partial updates without rollback. This delta intentionally replaces that behavior to satisfy US-04. Baseline specs and application code remain unchanged during proposal creation.
