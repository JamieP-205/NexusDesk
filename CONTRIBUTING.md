# Contributing

I’m keeping NexusDesk small enough to understand. I’m happy with focused improvements, especially clearer errors, useful tests and accessibility fixes.

I use Java 17 or 21 and the Maven wrapper. I run `./mvnw verify` before proposing a change. On Windows I use `.\mvnw.cmd verify`.

For a change I create a branch, keep the commits about one piece of work, and open a pull request explaining the problem and the test result. I don’t merge unfinished features behind working-looking buttons.

I keep SQL in the service that owns that behaviour. User-entered values always use bound parameters. A schema change gets a new numbered Flyway migration; I don't edit migrations that have already been released.

I keep role and ownership checks on the server even when a button is hidden. Changes to permissions, assignment or ticket states need a regression test. I use the real H2 database in integration tests rather than mocking JDBC.

I don't include production data, credentials or personal contact details in issues, screenshots or commits. I use `example.test` accounts in fixtures.

If I use AI on a change, I explain its actual contribution in the PR. I still need to check the result and understand any code I accept.
