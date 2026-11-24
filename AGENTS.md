# Agent guidelines

Conventions for AI agents (and humans) contributing to Wiresham. They come from code reviews.
Code has to be easy to maintain by a person, not only by an agent.

## Required

### Clean code

The baseline is the book Clean Code. In particular:

- Code has to explain itself. Add comments and Javadoc only when the code is not what a reader
  would expect, when it is complex and can't be split to make it clearer, or when it is public
  API used by other programmers. Do not document API that only this project uses.
- Use names that reveal the intention. When a method is not clear, rename it instead of
  documenting it (for example `setVariableValue` instead of `capture`).
- Do not duplicate logic. When two classes share steps or error messages, move them to a
  common place.
- Look for what the project already solves before writing something new.

### Every line has to earn its place

- Do not add logic that gives little value over what Java or the existing dependencies already
  provide. When in doubt, leave it out.
- Do not keep two methods that do the same, nor production methods that only tests use.
- Inline constants and private methods that are used only once and do not add clarity.

### Design

- Behavior belongs to the object that has the data. Prefer polymorphism, with default
  implementations in the base class, over `instanceof` checks from the outside.
- Avoid classes with only static members. Prefer a base class or an instance.
- Validate input strictly. Do not use `Character.digit` or `Character.isDigit` to parse, since
  they accept non-ASCII digits; check the exact ranges.

### Features

- Think of every feature for both sides: client and server steps, capturing and replacing,
  service and client mode. A flow should stay usable in both directions. When that is not
  possible yet, reject it with a clear message and note it in the README.
- Use known syntax (regular expressions, `${name}`) instead of inventing a new one, and support
  a subset that can grow.
- Reject unsupported constructs when the flow is loaded, with a message that includes the
  position and the supported syntax.

### Tests

- A test has one purpose, evident from its name. A reviewer should not need to ask what it is
  for.
- A test checks what its name says, and nothing else. Every assertion has to be necessary.
- Prefer one assertion against an expected object over several assertions on its parts.
- Test behavior that matters to users. Do not assert on incidental details, like the formatting
  chosen by a library.
- Do not add a test for something another test already covers.

## Preferred

- Keep performance tests out of the unit tests.
- Tests have to be deterministic: no dependency on time or network. A randomized test needs a
  fixed seed and a comment explaining why it is worth having.
- Do not add dependencies without discussing them first. The library is shaded into a
  standalone jar, so every dependency adds weight and maintenance. When writing by hand
  something a library solves, explain the trade-off in the pull request.
- Prefer streams for simple transformations of collections.

## Before opening a pull request

- Run `mvn clean package`. It runs checkstyle (100 columns) and all the tests. The code has to
  compile and run on Java 8.
- Review your own diff against this file, looking for duplication, permissive validation,
  comments that repeat the code and tests without a clear purpose.
- Update the README in the same pull request.
- Write code, comments, commits and pull requests in English.
- Do not add AI attribution lines to commits or pull request descriptions.
- Keep each pull request to one topic. Propose follow-ups as separate pull requests.
- List breaking changes for users of the library in the pull request description.
