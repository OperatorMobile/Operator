# User Guide

## Chat And Sessions

Operator starts on the active Codex conversation. The drawer contains project
and session navigation. The composer sends user input, and during active work
the primary action can stop or steer the current turn.

## Terminal

The terminal uses Operator's app-private shell environment. Available commands
depend on the build profile and configured runtime artifacts.

## Diff Review

Use `/diff` or tap a file-change activity item to open the diff inspector. Git
controls appear only when the active project is inside a Git repository.

Available actions may include:

- stage
- unstage
- revert with confirmation
- send file context back to Codex

## Settings

Settings contains account state, runtime status, appearance, GitHub CLI auth,
archived sessions, and available capability controls.
