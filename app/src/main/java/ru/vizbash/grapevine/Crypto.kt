package ru.vizbash.grapevine

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val SYM_ALGORITHM = "AES/CBC/PKCS5Padding"
private const val SECRET_ITERATIONS_COUNT = 2000
private const val SECRET_KEY_SIZE = 256
private val AES_SALT = byteArrayOf(0x15, 0x52, 0x10, 0x63)
private val AES_IV = byteArrayOf(
    0x53, 0x01, 0x13, 0x19, 0x56, 0x21, 0x64, 0x27,
    0x35, 0x74, 0x06, 0x65, 0x50, 0x16, 0x43, 0x44,
)

fun decodePublicKey(bytes: ByteArray): PublicKey = KeyFactory
    .getInstance("RSA")
    .generatePublic(X509EncodedKeySpec(bytes))

fun decodePrivateKey(bytes: ByteArray): PrivateKey = KeyFactory
    .getInstance("RSA")
    .generatePrivate(PKCS8EncodedKeySpec(bytes))

fun encryptPrivateKey(privateKey: PrivateKey, password: String): ByteArray {
    val cipher = Cipher.getInstance(SYM_ALGORITHM).apply {
        init(Cipher.ENCRYPT_MODE, createSecret(password), IvParameterSpec(AES_IV))
    }
    return cipher.doFinal(privateKey.encoded)
}

fun decryptPrivateKey(bytes: ByteArray, password: String): PrivateKey? {
    val cipher = Cipher.getInstance(SYM_ALGORITHM).apply {
        init(Cipher.DECRYPT_MODE, createSecret(password), IvParameterSpec(AES_IV))
    }
    try {
        return decodePrivateKey(cipher.doFinal(bytes))
    } catch (e: BadPaddingException) {
        return null
    }
}

private fun createSecret(password: String): SecretKey {
    val factory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA1")
    val spec = PBEKeySpec(
        password.toCharArray(),
        AES_SALT,
        SECRET_ITERATIONS_COUNT,
        SECRET_KEY_SIZE,
    )
    return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
}