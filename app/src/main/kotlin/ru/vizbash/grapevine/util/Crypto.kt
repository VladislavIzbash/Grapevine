package ru.vizbash.grapevine.util

import java.math.BigInteger
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.*
import javax.crypto.interfaces.DHPrivateKey
import javax.crypto.interfaces.DHPublicKey
import javax.crypto.spec.DHParameterSpec
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

private val DH_PARAMS = DHParameterSpec(
    BigInteger("178011905478542266528237562450159990145232156369120674273274450314442865788737020770612695252123463079567156784778466449970650770920727857050009668388144034129745221171818506047231150039301079959358067395348717066319802262019714966524135060945913707594956514672855690606794135837542707371727429551343320695239"),
    BigInteger("174068207532402095185811980123523436538604490794561350978495831040599953488455823147851597408940950725307797094915759492368300574252438761037084473467180148876118103083043754985190983472601550494691329488083395492313850000361646482644608492304078721818959999056496097769368017749273708962006689187956744210730"),
    0,
)

fun generateRsaKeys(): KeyPair = KeyPairGenerator.getInstance("RSA").run {
    initialize(2048)
    genKeyPair()
}

fun generateSessionKeys(): KeyPair = KeyPairGenerator.getInstance("DH").run {
    initialize(DH_PARAMS)
    genKeyPair()
}

fun decodeDhPublicKey(bytes: ByteArray): DHPublicKey = KeyFactory
    .getInstance("DH")
    .generatePublic(X509EncodedKeySpec(bytes)) as DHPublicKey

fun decodeRsaPublicKey(bytes: ByteArray): RSAPublicKey = KeyFactory
    .getInstance("RSA")
    .generatePublic(X509EncodedKeySpec(bytes)) as RSAPublicKey

fun decodeRsaPrivateKey(bytes: ByteArray): PrivateKey = KeyFactory
    .getInstance("RSA")
    .generatePrivate(PKCS8EncodedKeySpec(bytes))

fun decodeSecretKey(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

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
    privKey: DHPrivateKey,
    remotePubKey: DHPublicKey,
): SecretKey {
    val secret = KeyAgreement.getInstance("DH").run {
        init(privKey)
        doPhase(remotePubKey, true)
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

