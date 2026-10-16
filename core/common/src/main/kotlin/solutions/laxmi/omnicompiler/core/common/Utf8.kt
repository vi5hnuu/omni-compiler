package solutions.laxmi.omnicompiler.core.common

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * [bytes] as UTF-8 text, or null when they aren't text the app can edit: binary content (NUL bytes) or another
 * encoding. A lenient decode would swap such bytes for U+FFFD, and the next save or push would write that damage back.
 * A byte-order mark is kept, so the text encodes back to exactly the same bytes.
 */
fun decodeUtf8OrNull(bytes: ByteArray): String? {
    if (bytes.any { it == 0.toByte() }) return null
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }
}
