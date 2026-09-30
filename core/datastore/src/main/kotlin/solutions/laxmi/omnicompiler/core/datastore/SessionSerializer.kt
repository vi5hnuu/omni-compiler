package solutions.laxmi.omnicompiler.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/**
 * Encrypts the whole session blob with an AEAD whose key lives in the Android Keystore.
 * An empty file (or a blob we can no longer decrypt, e.g. after a Keystore reset) reads as "signed out".
 */
internal class SessionSerializer(
    private val aead: Lazy<Aead>,
    private val json: Json,
) : Serializer<StoredSession?> {

    override val defaultValue: StoredSession? = null

    override suspend fun readFrom(input: InputStream): StoredSession? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return null
        return try {
            val plain = aead.value.decrypt(bytes, ASSOCIATED_DATA)
            json.decodeFromString(StoredSession.serializer(), plain.decodeToString())
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unreadable session", e)
        }
    }

    override suspend fun writeTo(t: StoredSession?, output: OutputStream) {
        if (t == null) return
        val plain = json.encodeToString(StoredSession.serializer(), t).encodeToByteArray()
        output.write(aead.value.encrypt(plain, ASSOCIATED_DATA))
    }

    private companion object {
        val ASSOCIATED_DATA = "omni.session.v1".encodeToByteArray()
    }
}
