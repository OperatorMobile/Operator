# Android Git patch set

These patches adapt upstream Git for Operator's embedded Android runtime.

`termux-reference/` contains the Termux Git package patches that apply to
Operator's use case. They are vendored from:

https://github.com/termux/termux-packages/tree/master/packages/git

Termux-specific user messaging and hardcoded Termux prefix patches are not
included because Operator must not report Termux package-manager instructions or
compile in Termux's app-private paths.

`operator/` contains Operator-specific Android runtime fixes discovered while
testing the packaged Git binary inside `com.illumination.operator`.
