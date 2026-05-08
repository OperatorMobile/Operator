# Security Policy

Operator embeds a local development runtime and can execute commands inside the
app-owned Android filesystem. Treat reports about command execution, credential
handling, Git authentication, package installation, runtime extraction, and
background processing as security-sensitive.

## Reporting

Do not open public issues for vulnerabilities. Send a private report to the
maintainers with:

- A concise description of the issue.
- Reproduction steps and affected build/profile.
- Device model, Android version, app package name, and relevant logs.
- Whether credentials, files outside the workspace, or network tokens were
  exposed.

## Supported Versions

The public repository is pre-release. Only the current main branch is intended
to receive fixes.

## Scope

In scope:

- Escaping the intended app-owned workspace or command sandbox.
- Unauthorized credential access or persistence.
- Unsafe runtime SDK extraction, symlink handling, or executable replacement.
- Git/GitHub auth leakage.
- Background processing that runs commands after explicit user stop.

Out of scope:

- Issues requiring a rooted device unless they expose a bug that also affects
  normal Android app sandboxes.
- Vulnerabilities solely in unmodified upstream dependencies, unless Operator's
  packaging makes them worse.
