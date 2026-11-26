package org.najwa.app.platform

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional password protection for the backup file: AES-256-GCM with a key from PBKDF2-HMAC-SHA256 (a random salt
 * and 210,000 rounds). Done entirely on the phone with the platform's own crypto; nothing is stored or sent.
 */
object BackupCrypto {
    private const val ROUNDS=210_000
    fun isEncrypted(text: String)=runCatching {JSONObject(text).optInt("najwaEncrypted",0)==1}.getOrDefault(false)

    private fun key(password: CharArray,salt: ByteArray,rounds: Int): SecretKeySpec {
        val spec=PBEKeySpec(password,salt,rounds,256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")
        } finally {spec.clearPassword()}
    }

    fun encrypt(plain: String,password: CharArray): String {
        val random=SecureRandom()
        val salt=ByteArray(16).also(random::nextBytes)
        val iv=ByteArray(12).also(random::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(password,salt,ROUNDS),GCMParameterSpec(128,iv))
        val data=cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        fun b64(b: ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP)
        return JSONObject().put("najwaEncrypted",1).put("kdf","PBKDF2WithHmacSHA256").put("rounds",ROUNDS)
            .put("salt",b64(salt)).put("iv",b64(iv)).put("data",b64(data)).toString(2)
    }

    /** The plain backup text, or null when the password is wrong or the file is damaged. */
    fun decrypt(text: String,password: CharArray): String? = runCatching {
        val j=JSONObject(text)
        fun bytes(k: String)=Base64.decode(j.getString(k),Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(password,bytes("salt"),j.optInt("rounds",ROUNDS)),GCMParameterSpec(128,bytes("iv")))
        String(cipher.doFinal(bytes("data")),Charsets.UTF_8)
    }.getOrNull()
}

/** What a backup file holds, shown before it replaces anything. */
data class BackupPreview(val city: String,val page: Int,val khatmas: Int,val logDays: Int,val mosques: Int) {
    companion object {
        fun of(text: String): BackupPreview? = runCatching {
            val j=JSONObject(text)
            require(j.optInt("najwaBackup",0)==1)
            val data=j.optJSONObject("data")
            val quran=data?.optJSONObject("preferences")?.optJSONObject("quran")
            fun int(o: JSONObject?,k: String)=o?.optJSONObject(k)?.optInt("v",0) ?: 0
            val files=data?.optJSONObject("files")
            BackupPreview(j.optString("city"),int(quran,"page"),int(quran,"khatmas"),
                files?.optJSONObject("prayer-log.json")?.optJSONObject("days")?.length() ?: 0,
                files?.optJSONObject("mosques.json")?.optJSONArray("mosques")?.length() ?: 0)
        }.getOrNull()
    }
}
