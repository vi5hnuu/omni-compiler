package solutions.laxmi.omnicompiler.core.editor

import io.github.rosemoe.sora.text.Content

/** One file's Sora document. Sora keeps the text, undo history and caret on [content]; we add the scroll offset. */
internal class CachedDocument(val content: Content, val revision: Int) {
    var scrollX: Int = 0
    var scrollY: Int = 0
}

/**
 * Recently shown documents, so returning to a tab keeps its undo history, caret and scroll instead of rebuilding
 * the text. Bounded: the least recently shown document is dropped first. Main thread only.
 */
internal class DocumentCache(private val capacity: Int = DEFAULT_CAPACITY) {
    private val entries = object : LinkedHashMap<String, CachedDocument>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDocument>) = size > capacity
    }

    /** The cached document for [id], unless its content was replaced since ([revision] moved on). */
    fun get(id: String, revision: Int): CachedDocument? = entries[id]?.takeIf { it.revision == revision }

    /** The cached document for [id] whatever its revision, without counting as a use. */
    fun peek(id: String): CachedDocument? = entries.entries.firstOrNull { it.key == id }?.value

    fun put(id: String, document: CachedDocument) {
        entries[id] = document
    }

    fun clear() = entries.clear()

    private companion object {
        const val DEFAULT_CAPACITY = 8
        const val LOAD_FACTOR = 0.75f
    }
}
