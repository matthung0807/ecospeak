# Design

## Context

See proposal.md for motivation. The inline script in `src/main/resources/static/index.html` parses JSON, immediately updates topic and target phrase, clears the dialogue, and then iterates entries. Its catch/finally blocks already provide the error message, stop loading, and enable retry. Existing Java tests cover the server, not this browser behavior.

## Goals / Non-Goals

**Goals:** Put a complete validation boundary before any result mutation, keeping the change local to existing browser response handling.

**Non-Goals:** Backend validation, API/schema changes, model or prompt changes, automatic retries, new error copy, dependencies, visual redesign, and general rendering rollback. No checks for A/B speaker values, maximum line count, phrase occurrence, grammar, or naturalness.

## Decisions

- Validate immediately after `response.json()` and before setting topic, target phrase, clearing dialogue, revealing results, or changing the button label. Reuse the existing catch/finally path by throwing on invalid data. Field-by-field validation during rendering is rejected because a later invalid entry could leave partial content displayed.
- Use plain JavaScript object/type checks, `Array.isArray`, and full-array entry validation. Required strings must have `trim().length > 0`; trimming is a validity check only, and accepted text is displayed unchanged. Top-level arrays, entry arrays, nulls, and non-string required fields are invalid; extra fields are ignored.
- Preserve the previous result by avoiding mutations until validation succeeds, rather than snapshotting/restoring DOM content. On an invalid first response, the existing hidden result stays hidden. Existing request lifecycle, scenario retention, textContent rendering, and success behavior remain intact.
- Keep unexpected rendering exceptions caught under the existing behavior; arbitrary DOM failure rollback is outside this small change. The preservation guarantee concerns invalid response data, which must be rejected before rendering.
- Verify with mocked browser responses, avoiding live model calls. AGENTS.md requires delegation to the playwright-tester subagent using Playwright MCP, screenshots, and repeat testing after fixes.

## Risks / Trade-offs

- [Validating only the first entry would miss later corruption] → Exercise an invalid later entry after a valid first entry and compare the entire previous result.
- [Whitespace and wrong types could look superficially usable] → Include empty/whitespace strings, missing/null fields, non-string values, wrong collections, and invalid entries in the browser test matrix.
- [Structural checks cannot ensure learning quality] → Keep validation structural and do not claim semantic quality or model compatibility is verified.

## Migration Plan

No data migration or dependency changes. Apply the local script change, verify with mocked browser responses, and revert that script change if necessary. No baseline sync or archive is part of proposal creation.
