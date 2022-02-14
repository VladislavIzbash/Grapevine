package ru.vizbash.grapevine

import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val SECRET_ITERATIONS_COUNT = 2000
private const val SECRET_KEY_SIZE = 256
private val AES_SALT = byteArrayOf(0x15, 0x52, 0x10, 0x63)
private val AES_IV = byteArrayOf(
    0x53, 0x01, 0x13, 0x19, 0x56, 0x21, 0x64, 0x27,
    0x35, 0x74, 0x06, 0x65, 0x50, 0x16, 0x43, 0x44,
)

fun createRsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").run {
    initialize(2048)
    genKeyPair()
}

fun createDhKeyPair(): KeyPair = KeyPairGenerator.getInstance("DH").run {
    initialize(1024)
    genKeyPair()
}

fun decodeDhPublicKey(bytes: ByteArray): PublicKey = KeyFactory
    .getInstance("DH")
    .generatePublic(X509EncodedKeySpec(bytes))

fun decodeRsaPublicKey(bytes: ByteArray): PublicKey = KeyFactory
    .getInstance("RSA")
    .generatePublic(X509EncodedKeySpec(bytes))

fun decodeRsaPrivateKey(bytes: ByteArray): PrivateKey = KeyFactory
    .getInstance("RSA")
    .generatePrivate(PKCS8EncodedKeySpec(bytes))

fun aesEncrypt(
    plain: ByteArray,
    key: SecretKey,
): ByteArray = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
    init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(AES_IV))
    doFinal(plain)
}

fun aesDecrypt(
    bytes: ByteArray,
    key: SecretKey,
): ByteArray? = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
    init(Cipher.DECRYPT_MODE, key, IvParameterSpec(AES_IV))
    try {
        doFinal(bytes)
    } catch (e: BadPaddingException) {
        null
    }
}

fun generatePasswordSecret(password: String): SecretKey {
    val factory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA1")
    val spec = PBEKeySpec(
        password.toCharArray(),
        AES_SALT,
        SECRET_ITERATIONS_COUNT,
        SECRET_KEY_SIZE,
    )
    return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
}

fun generateSharedSecret(
    dhPrivKey: PrivateKey,
    remoteDhPubKey: PublicKey,
): SecretKey {
    val secret = KeyAgreement.getInstance("DH").run {
        init(dhPrivKey)
        doPhase(remoteDhPubKey, true)
        generateSecret()
    }
    return SecretKeySpec(secret, 0, 16, "AES")
}

fun signMessage(
    msg: ByteArray,
    privateKey: PrivateKey,
): ByteArray = Signature.getInstance("SHA256withRSA").run {
    initSign(privateKey)
    update(msg)
    sign()
}

fun verifyMessage(
    msg: ByteArray,
    sign: ByteArray,
    publicKey: PublicKey,
) = Signature.getInstance("SHA256withRSA").run {
    initVerify(publicKey)
    update(msg)
    verify(sign)
}

