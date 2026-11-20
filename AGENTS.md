## Java Coding Style

Follow the Google Java Style Guide:
https://google.github.io/styleguide/javaguide.html

Additional rules:
- Use 4 spaces for indentation.
- Never write single-line blocks, such as `if (condition) { return value; }`.
- Always use multi-line formatting for blocks.
- Place annotations such as `@Override`, `@Transactional`, and `@GetMapping` on separate lines above declarations.
- Use standard Java brace placement (K&R style).
- Prefer readability over compactness.
- Do not compress multiple statements into a single line.
- Do not reformat unrelated code.

# Development Workflow

After implementing a web UI feature:

1. Delegate browser testing to the playwright-tester subagent.
2. The subagent must use Playwright MCP.
3. Verify the implemented feature against requirements.
4. Capture screenshots and report test results.
5. If tests fail, the main agent fixes the issues.
6. Repeat testing until all required tests pass.