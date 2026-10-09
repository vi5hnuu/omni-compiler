package solutions.laxmi.omnicompiler.core.ui

/** Program output past these sizes is cut until the user asks for all of it, so one chatty run can't stall a screen. */
private const val MAX_SHOWN_CHARS = 20_000
private const val MAX_SHOWN_LINES = 400

/** The leading part of [text] worth rendering at once, or null when it is short enough to show whole. */
fun clipForDisplay(text: String): String? {
    var end = minOf(text.length, MAX_SHOWN_CHARS)
    var lines = 0
    for (i in 0 until end) {
        if (text[i] == '\n' && ++lines == MAX_SHOWN_LINES) {
            end = i
            break
        }
    }
    return if (end < text.length) text.substring(0, end) else null
}
