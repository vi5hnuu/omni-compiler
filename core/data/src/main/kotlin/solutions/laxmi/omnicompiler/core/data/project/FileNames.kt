package solutions.laxmi.omnicompiler.core.data.project

/** Characters no common file system or storage provider accepts in a name, plus control characters. */
private val UNSAFE_NAME_CHARS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

/**
 * [value] as a folder or file name people recognise: case, spaces and any script are kept; only characters storage
 * can't hold are replaced, runs of whitespace collapse, and leading dots (hidden files) are dropped.
 * Empty when nothing usable is left.
 */
internal fun safeFileName(value: String, maxLength: Int): String = value
    .replace(UNSAFE_NAME_CHARS, "-")
    .replace(Regex("\\s+"), " ")
    .trim()
    .trimStart('.')
    .take(maxLength)
    .trim()
