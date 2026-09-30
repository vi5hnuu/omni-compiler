package solutions.laxmi.omnicompiler.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/**
 * Encrypts a whole JSON blob with an AEAD whose key lives in the Android Keystore (session, git tokens).
 * An empty file, or a blob that can no longer be decrypted (e.g. after a Keystore reset), reads as null.
 * [associatedData] binds each file to its purpose so one blob can't be swapped in for another.
 */
internal class EncryptedJsonSerializer<T : Any>(
    private val aead: Lazy<Aead>,
    private val json: Json,
    private val serializer: KSerializer<T>,
    private val associatedData: ByteArray,
) : Serializer<T?> {

    override val defaultValue: T? = null

    override suspend fun readFrom(input: InputStream): T? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return null
        return try {
            val plain = aead.value.decrypt(bytes, associatedData)
            json.decodeFromString(serializer, plain.decodeToString())
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unreadable encrypted store", e)
        }
    }

    override suspend fun writeTo(t: T?, output: OutputStream) {
        if (t == null) return
        val plain = json.encodeToString(serializer, t).encodeToByteArray()
        output.write(aead.value.encrypt(plain, associatedData))
    }
}
