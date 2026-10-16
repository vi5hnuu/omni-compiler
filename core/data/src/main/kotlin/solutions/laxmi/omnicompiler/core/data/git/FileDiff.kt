package solutions.laxmi.omnicompiler.core.data.git

import com.github.difflib.DiffUtils

/** One row of a line diff; [oldLine]/[newLine] are 1-based and null on the side the line doesn't exist. */
data class DiffLine(val kind: Kind, val text: String, val oldLine: Int?, val newLine: Int?) {
    enum class Kind { Context, Added, Removed, Gap }
}

/**
 * How a file changed from [old] (the remote) to [new] (this device), as unified-diff rows: changed lines with a few
 * unchanged ones around them, and a [DiffLine.Kind.Gap] row where unchanged lines are left out. Computed with
 * java-diff-utils (Myers).
 */
data class FileDiff(val lines: List<DiffLine>, val added: Int, val removed: Int) {
    companion object {
        private const val CONTEXT = 3

        fun of(old: String?, new: String?): FileDiff {
            val before = old?.lines().orEmpty()
            val after = new?.lines().orEmpty()
            val deltas = DiffUtils.diff(before, after).deltas
            val rows = mutableListOf<DiffLine>()
            var added = 0
            var removed = 0
            // Next line of each side not yet emitted (0-based).
            var oldPos = 0
            var newPos = 0
            fun context(untilOld: Int) {
                while (oldPos < untilOld) rows += DiffLine(DiffLine.Kind.Context, before[oldPos], ++oldPos, ++newPos)
            }
            deltas.forEach { delta ->
                val start = delta.source.position
                // Unchanged lines before this change: trailing context of the previous hunk, a gap, leading context.
                val gapStart = if (rows.isEmpty()) start else minOf(start, oldPos + CONTEXT)
                if (rows.isNotEmpty()) context(gapStart)
                val leadStart = maxOf(oldPos, start - CONTEXT)
                if (leadStart > oldPos) {
                    rows += DiffLine(DiffLine.Kind.Gap, "", null, null)
                    newPos += leadStart - oldPos
                    oldPos = leadStart
                }
                context(start)
                delta.source.lines.forEach { rows += DiffLine(DiffLine.Kind.Removed, it, ++oldPos, null) }
                delta.target.lines.forEach { rows += DiffLine(DiffLine.Kind.Added, it, null, ++newPos) }
                removed += delta.source.lines.size
                added += delta.target.lines.size
            }
            if (rows.isNotEmpty()) {
                context(minOf(before.size, oldPos + CONTEXT))
                if (oldPos < before.size) rows += DiffLine(DiffLine.Kind.Gap, "", null, null)
            }
            return FileDiff(rows, added, removed)
        }
    }
}
