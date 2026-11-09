# Development Workflow

After implementing a web UI feature:

1. Delegate browser testing to the playwright-tester subagent.
2. The subagent must use Playwright MCP.
3. Verify the implemented feature against requirements.
4. Capture screenshots and report test results.
5. If tests fail, the main agent fixes the issues.
6. Repeat testing until all required tests pass.