package app.sinceus.cloud

import app.sinceus.data.SyncCrypto
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Passwort-Verschlüsselung für Cloud-Sicherungen: Aus dem Passwort wird per PBKDF2
 * ein Schlüssel abgeleitet, die Daten selbst schützt der bewährte [SyncCrypto]-Strom.
 * Falsches Passwort oder veränderte Datei -> Exception beim Entschlüsseln.
 */
object CloudCrypto {
    private const val SALT_BYTES = 16
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            .also { spec.clearPassword() }
    }

    fun encrypt(input: InputStream, output: OutputStream, password: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        output.write(salt)
        output.flush()
        SyncCrypto.encrypt(input, output, derive(password, salt))
    }

    fun decrypt(input: InputStream, output: OutputStream, password: String) {
        val salt = ByteArray(SALT_BYTES)
        var read = 0
        while (read < SALT_BYTES) {
            val n = input.read(salt, read, SALT_BYTES - read)
            if (n < 0) throw java.io.IOException("backup truncated")
            read += n
        }
        SyncCrypto.decrypt(input, output, derive(password, salt))
    }
}
