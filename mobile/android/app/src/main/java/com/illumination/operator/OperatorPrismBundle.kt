package com.illumination.operator

import io.noties.prism4j.annotations.PrismBundle

@PrismBundle(
    include = [
        "c",
        "clike",
        "cpp",
        "csharp",
        "css",
        "dart",
        "git",
        "go",
        "groovy",
        "java",
        "javascript",
        "json",
        "kotlin",
        "latex",
        "makefile",
        "markdown",
        "markup",
        "python",
        "sql",
        "swift",
        "yaml",
    ],
    grammarLocatorClassName = ".GeneratedPrismGrammarLocator",
)
internal class OperatorPrismBundle
