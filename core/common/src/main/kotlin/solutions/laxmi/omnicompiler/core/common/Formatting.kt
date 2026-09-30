package solutions.laxmi.omnicompiler.core.common

/** Shortened job id for dense lists: `7f3a…c21`. Ids aren't translated, so this lives outside `core/ui`. */
fun shortJobId(id: String): String =
    if (id.length <= 10) id else "${id.take(4)}…${id.takeLast(3)}"
