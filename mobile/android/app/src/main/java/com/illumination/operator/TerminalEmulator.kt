package com.illumination.operator

private const val TERMINAL_SCROLLBACK_LIMIT = 1_500

internal data class TerminalSnapshot(
    val rows: Int,
    val cols: Int,
    val displayText: String,
    val copyText: String,
    val isBlank: Boolean,
) {
    companion object {
        fun empty(rows: Int, cols: Int): TerminalSnapshot =
            TerminalEmulator(rows, cols).snapshot()
    }
}

internal class TerminalEmulator(
    rows: Int,
    cols: Int,
) {
    private var rows = rows.coerceAtLeast(1)
    private var cols = cols.coerceAtLeast(1)
    private val mainPage = TerminalPage(this.rows, this.cols)
    private val alternatePage = TerminalPage(this.rows, this.cols)
    private var useAlternatePage = false
    private var cursorRow = 0
    private var cursorCol = 0
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var cursorVisible = true
    private var scrollTop = 0
    private var scrollBottom = this.rows - 1
    private var parserState = ParserState.Ground
    private var csiBuffer = StringBuilder()
    private var pendingOscEscape = false

    private val page: TerminalPage
        get() = if (useAlternatePage) alternatePage else mainPage

    fun feed(delta: String): TerminalSnapshot {
        delta.forEach(::feedChar)
        clampCursor()
        return snapshot()
    }

    fun resize(rows: Int, cols: Int): TerminalSnapshot {
        val nextRows = rows.coerceAtLeast(1)
        val nextCols = cols.coerceAtLeast(1)
        if (nextRows == this.rows && nextCols == this.cols) {
            return snapshot()
        }
        this.rows = nextRows
        this.cols = nextCols
        mainPage.resize(nextRows, nextCols)
        alternatePage.resize(nextRows, nextCols)
        scrollTop = 0
        scrollBottom = nextRows - 1
        clampCursor()
        return snapshot()
    }

    fun clear(): TerminalSnapshot {
        page.clear()
        cursorRow = 0
        cursorCol = 0
        return snapshot()
    }

    fun reset(rows: Int = this.rows, cols: Int = this.cols): TerminalSnapshot {
        this.rows = rows.coerceAtLeast(1)
        this.cols = cols.coerceAtLeast(1)
        mainPage.resize(this.rows, this.cols)
        alternatePage.resize(this.rows, this.cols)
        mainPage.clear(includeScrollback = true)
        alternatePage.clear(includeScrollback = true)
        useAlternatePage = false
        cursorRow = 0
        cursorCol = 0
        savedCursorRow = 0
        savedCursorCol = 0
        cursorVisible = true
        scrollTop = 0
        scrollBottom = this.rows - 1
        parserState = ParserState.Ground
        csiBuffer = StringBuilder()
        pendingOscEscape = false
        return snapshot()
    }

    fun snapshot(): TerminalSnapshot {
        val display = page.displayLines(
            cursorRow = cursorRow,
            cursorCol = cursorCol.coerceIn(0, cols - 1),
            cursorVisible = cursorVisible,
        ).joinToString("\n")
        val copy = page.copyLines().joinToString("\n").trimEnd()
        return TerminalSnapshot(
            rows = rows,
            cols = cols,
            displayText = display,
            copyText = copy,
            isBlank = copy.isBlank(),
        )
    }

    private fun feedChar(char: Char) {
        when (parserState) {
            ParserState.Ground -> feedGround(char)
            ParserState.Escape -> feedEscape(char)
            ParserState.Csi -> feedCsi(char)
            ParserState.Osc -> feedOsc(char)
            ParserState.Charset -> parserState = ParserState.Ground
        }
    }

    private fun feedGround(char: Char) {
        when (char) {
            '\u001B' -> parserState = ParserState.Escape
            '\u0007' -> Unit
            '\b', '\u007F' -> cursorCol = (cursorCol - 1).coerceAtLeast(0)
            '\t' -> tab()
            '\n', '\u000B', '\u000C' -> lineFeed()
            '\r' -> cursorCol = 0
            in '\u0000'..'\u001F' -> Unit
            else -> putChar(char)
        }
    }

    private fun feedEscape(char: Char) {
        when (char) {
            '[' -> {
                csiBuffer = StringBuilder()
                parserState = ParserState.Csi
            }
            ']' -> {
                pendingOscEscape = false
                parserState = ParserState.Osc
            }
            '(', ')', '*', '+' -> parserState = ParserState.Charset
            '7' -> {
                saveCursor()
                parserState = ParserState.Ground
            }
            '8' -> {
                restoreCursor()
                parserState = ParserState.Ground
            }
            'D' -> {
                lineFeed()
                parserState = ParserState.Ground
            }
            'E' -> {
                cursorCol = 0
                lineFeed()
                parserState = ParserState.Ground
            }
            'M' -> {
                reverseIndex()
                parserState = ParserState.Ground
            }
            'c' -> {
                reset(rows, cols)
                parserState = ParserState.Ground
            }
            else -> parserState = ParserState.Ground
        }
    }

    private fun feedCsi(char: Char) {
        if (char in '@'..'~') {
            processCsi(csiBuffer.toString(), char)
            parserState = ParserState.Ground
        } else {
            csiBuffer.append(char)
        }
    }

    private fun feedOsc(char: Char) {
        when {
            pendingOscEscape && char == '\\' -> {
                pendingOscEscape = false
                parserState = ParserState.Ground
            }
            char == '\u001B' -> pendingOscEscape = true
            char == '\u0007' -> {
                pendingOscEscape = false
                parserState = ParserState.Ground
            }
            else -> pendingOscEscape = false
        }
    }

    private fun processCsi(buffer: String, final: Char) {
        val privateMode = buffer.startsWith("?")
        val normalized = buffer
            .dropWhile { it == '?' || it == '>' || it == '!' }
            .filter { it.isDigit() || it == ';' }
        val params = if (normalized.isEmpty()) {
            emptyList()
        } else {
            normalized.split(';').map { part -> part.toIntOrNull() }
        }
        fun param(index: Int, default: Int): Int =
            params.getOrNull(index)?.takeIf { it > 0 } ?: default

        when (final) {
            'A' -> cursorRow = (cursorRow - param(0, 1)).coerceAtLeast(scrollTop)
            'B' -> cursorRow = (cursorRow + param(0, 1)).coerceAtMost(scrollBottom)
            'C' -> cursorCol = (cursorCol + param(0, 1)).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - param(0, 1)).coerceAtLeast(0)
            'E' -> {
                cursorRow = (cursorRow + param(0, 1)).coerceAtMost(rows - 1)
                cursorCol = 0
            }
            'F' -> {
                cursorRow = (cursorRow - param(0, 1)).coerceAtLeast(0)
                cursorCol = 0
            }
            'G' -> cursorCol = (param(0, 1) - 1).coerceIn(0, cols - 1)
            'H', 'f' -> {
                cursorRow = (param(0, 1) - 1).coerceIn(0, rows - 1)
                cursorCol = (param(1, 1) - 1).coerceIn(0, cols - 1)
            }
            'd' -> cursorRow = (param(0, 1) - 1).coerceIn(0, rows - 1)
            'J' -> eraseDisplay(params.getOrNull(0) ?: 0)
            'K' -> eraseLine(params.getOrNull(0) ?: 0)
            'L' -> insertLines(param(0, 1))
            'M' -> deleteLines(param(0, 1))
            '@' -> insertChars(param(0, 1))
            'P' -> deleteChars(param(0, 1))
            'X' -> eraseChars(param(0, 1))
            'S' -> page.scrollUp(scrollTop, scrollBottom, param(0, 1), useAlternatePage)
            'T' -> page.scrollDown(scrollTop, scrollBottom, param(0, 1))
            'm' -> Unit
            'r' -> setScrollRegion(params)
            's' -> saveCursor()
            'u' -> restoreCursor()
            'h' -> setMode(privateMode, params, enabled = true)
            'l' -> setMode(privateMode, params, enabled = false)
        }
        clampCursor()
    }

    private fun setMode(privateMode: Boolean, params: List<Int?>, enabled: Boolean) {
        if (!privateMode) {
            return
        }
        params.filterNotNull().forEach { mode ->
            when (mode) {
                25 -> cursorVisible = enabled
                47, 1047, 1049 -> {
                    if (enabled) {
                        saveCursor()
                        useAlternatePage = true
                        alternatePage.clear()
                        cursorRow = 0
                        cursorCol = 0
                    } else {
                        useAlternatePage = false
                        restoreCursor()
                    }
                    scrollTop = 0
                    scrollBottom = rows - 1
                }
            }
        }
    }

    private fun setScrollRegion(params: List<Int?>) {
        val top = (params.getOrNull(0)?.takeIf { it > 0 } ?: 1) - 1
        val bottom = (params.getOrNull(1)?.takeIf { it > 0 } ?: rows) - 1
        if (top in 0 until rows && bottom in 0 until rows && top < bottom) {
            scrollTop = top
            scrollBottom = bottom
        } else {
            scrollTop = 0
            scrollBottom = rows - 1
        }
        cursorRow = 0
        cursorCol = 0
    }

    private fun tab() {
        val nextStop = (((cursorCol / 8) + 1) * 8).coerceAtMost(cols - 1)
        while (cursorCol < nextStop) {
            putChar(' ')
        }
    }

    private fun putChar(char: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        page.lines[cursorRow][cursorCol] = char
        if (cursorCol == cols - 1) {
            cursorCol = cols
        } else {
            cursorCol += 1
        }
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            page.scrollUp(scrollTop, scrollBottom, 1, useAlternatePage)
        } else {
            cursorRow = (cursorRow + 1).coerceAtMost(rows - 1)
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) {
            page.scrollDown(scrollTop, scrollBottom, 1)
        } else {
            cursorRow = (cursorRow - 1).coerceAtLeast(0)
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                page.clearRowFrom(cursorRow, cursorCol.coerceIn(0, cols - 1))
                for (row in cursorRow + 1 until rows) {
                    page.clearRow(row)
                }
            }
            1 -> {
                for (row in 0 until cursorRow) {
                    page.clearRow(row)
                }
                page.clearRowUntil(cursorRow, cursorCol.coerceIn(0, cols - 1))
            }
            2 -> page.clear()
            3 -> page.clear(includeScrollback = true)
        }
    }

    private fun eraseLine(mode: Int) {
        val col = cursorCol.coerceIn(0, cols - 1)
        when (mode) {
            0 -> page.clearRowFrom(cursorRow, col)
            1 -> page.clearRowUntil(cursorRow, col)
            2 -> page.clearRow(cursorRow)
        }
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) {
            return
        }
        page.insertLines(cursorRow, scrollBottom, count)
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) {
            return
        }
        page.deleteLines(cursorRow, scrollBottom, count)
    }

    private fun insertChars(count: Int) {
        val line = page.lines[cursorRow]
        val col = cursorCol.coerceIn(0, cols - 1)
        for (index in cols - 1 downTo col + count) {
            line[index] = line[index - count]
        }
        for (index in col until (col + count).coerceAtMost(cols)) {
            line[index] = ' '
        }
    }

    private fun deleteChars(count: Int) {
        val line = page.lines[cursorRow]
        val col = cursorCol.coerceIn(0, cols - 1)
        for (index in col until cols) {
            val source = index + count
            line[index] = if (source < cols) line[source] else ' '
        }
    }

    private fun eraseChars(count: Int) {
        val line = page.lines[cursorRow]
        val col = cursorCol.coerceIn(0, cols - 1)
        for (index in col until (col + count).coerceAtMost(cols)) {
            line[index] = ' '
        }
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    private fun restoreCursor() {
        cursorRow = savedCursorRow
        cursorCol = savedCursorCol
        clampCursor()
    }

    private fun clampCursor() {
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols)
    }

    private enum class ParserState {
        Ground,
        Escape,
        Csi,
        Osc,
        Charset,
    }
}

private class TerminalPage(
    rows: Int,
    cols: Int,
) {
    var rows = rows.coerceAtLeast(1)
        private set
    var cols = cols.coerceAtLeast(1)
        private set
    var lines: MutableList<CharArray> = MutableList(this.rows) { blankLine() }
        private set
    private val scrollback = ArrayDeque<String>()

    fun resize(rows: Int, cols: Int) {
        val nextRows = rows.coerceAtLeast(1)
        val nextCols = cols.coerceAtLeast(1)
        if (nextRows == this.rows && nextCols == this.cols) {
            return
        }
        val nextLines = MutableList(nextRows) { CharArray(nextCols) { ' ' } }
        val rowCount = minOf(this.rows, nextRows)
        val colCount = minOf(this.cols, nextCols)
        for (row in 0 until rowCount) {
            for (col in 0 until colCount) {
                nextLines[row][col] = lines[row][col]
            }
        }
        this.rows = nextRows
        this.cols = nextCols
        lines = nextLines
    }

    fun clear(includeScrollback: Boolean = false) {
        for (row in 0 until rows) {
            clearRow(row)
        }
        if (includeScrollback) {
            scrollback.clear()
        }
    }

    fun clearRow(row: Int) {
        if (row !in 0 until rows) {
            return
        }
        lines[row].fill(' ')
    }

    fun clearRowFrom(row: Int, col: Int) {
        if (row !in 0 until rows) {
            return
        }
        for (index in col.coerceIn(0, cols - 1) until cols) {
            lines[row][index] = ' '
        }
    }

    fun clearRowUntil(row: Int, col: Int) {
        if (row !in 0 until rows) {
            return
        }
        for (index in 0..col.coerceIn(0, cols - 1)) {
            lines[row][index] = ' '
        }
    }

    fun scrollUp(top: Int, bottom: Int, count: Int, alternate: Boolean) {
        val safeTop = top.coerceIn(0, rows - 1)
        val safeBottom = bottom.coerceIn(safeTop, rows - 1)
        repeat(count.coerceAtLeast(0)) {
            if (!alternate && safeTop == 0 && safeBottom == rows - 1) {
                pushScrollback(lines[safeTop])
            }
            for (row in safeTop until safeBottom) {
                lines[row] = lines[row + 1]
            }
            lines[safeBottom] = blankLine()
        }
    }

    fun scrollDown(top: Int, bottom: Int, count: Int) {
        val safeTop = top.coerceIn(0, rows - 1)
        val safeBottom = bottom.coerceIn(safeTop, rows - 1)
        repeat(count.coerceAtLeast(0)) {
            for (row in safeBottom downTo safeTop + 1) {
                lines[row] = lines[row - 1]
            }
            lines[safeTop] = blankLine()
        }
    }

    fun insertLines(start: Int, bottom: Int, count: Int) {
        val safeStart = start.coerceIn(0, rows - 1)
        val safeBottom = bottom.coerceIn(safeStart, rows - 1)
        repeat(count.coerceAtLeast(0)) {
            for (row in safeBottom downTo safeStart + 1) {
                lines[row] = lines[row - 1]
            }
            lines[safeStart] = blankLine()
        }
    }

    fun deleteLines(start: Int, bottom: Int, count: Int) {
        val safeStart = start.coerceIn(0, rows - 1)
        val safeBottom = bottom.coerceIn(safeStart, rows - 1)
        repeat(count.coerceAtLeast(0)) {
            for (row in safeStart until safeBottom) {
                lines[row] = lines[row + 1]
            }
            lines[safeBottom] = blankLine()
        }
    }

    fun displayLines(
        cursorRow: Int,
        cursorCol: Int,
        cursorVisible: Boolean,
    ): List<String> =
        lines.mapIndexed { row, line ->
            val rendered = line.copyOf()
            if (cursorVisible && row == cursorRow && cursorCol in rendered.indices) {
                rendered[cursorCol] = if (rendered[cursorCol] == ' ') '█' else '▓'
            }
            String(rendered)
        }

    fun copyLines(): List<String> =
        scrollback.toList() + lines.map { line -> line.trimmedString() }

    private fun pushScrollback(line: CharArray) {
        scrollback.addLast(line.trimmedString())
        while (scrollback.size > TERMINAL_SCROLLBACK_LIMIT) {
            scrollback.removeFirst()
        }
    }

    private fun blankLine(): CharArray = CharArray(cols) { ' ' }
}

private fun CharArray.trimmedString(): String {
    var end = size
    while (end > 0 && this[end - 1] == ' ') {
        end -= 1
    }
    return concatToString(0, end)
}
