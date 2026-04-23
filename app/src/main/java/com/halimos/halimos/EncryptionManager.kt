package com.halimos.halimos

import android.util.Base64
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionManager {

    private val gson = Gson()
    private val encryptionKey = BuildConfig.EncryptionKey

    fun <T> encrypt(data: T, clazz: Class<T>): String {
        val plainText = gson.toJson(data)
        
        val keyBytes = encryptionKey.padEnd(32, ' ').substring(0, 32).toByteArray(StandardCharsets.UTF_8)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
        val cipherBytes = cipher.doFinal(plainBytes)
        
        val combined = iv + cipherBytes
        var cipherText = Base64.encodeToString(combined, Base64.NO_WRAP)

        if (clazz.simpleName == "UriParameter") {
            cipherText = cipherText.replace("/", "%2F")
                .replace("+", "%2B")
                .replace("=", "%3D")
        }

        return cipherText
    }

    fun <T> decrypt(cipherText: String, clazz: Class<T>): T {
        return decrypt(cipherText, clazz as java.lang.reflect.Type)
    }

    fun <T> decrypt(cipherText: String, type: java.lang.reflect.Type): T {
        var data = cipherText
        if (type.toString().contains("UriParameter")) {
            data = data.replace("%2F", "/")
                .replace("%2B", "+")
                .replace("%3D", "=")
        }

        val fullCipher = Base64.decode(data, Base64.NO_WRAP)
        val iv = fullCipher.sliceArray(0 until 16)
        val cipherBytes = fullCipher.sliceArray(16 until fullCipher.size)
        
        val keyBytes = encryptionKey.padEnd(32, ' ').substring(0, 32).toByteArray(StandardCharsets.UTF_8)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        
        val plainBytes = cipher.doFinal(cipherBytes)
        val json = String(plainBytes, StandardCharsets.UTF_8)

        return gson.fromJson(json, type)
    }
}
