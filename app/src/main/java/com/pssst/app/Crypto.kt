package com.pssst.app

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement de bout en bout :
 * - chaque téléphone a une paire de clés EC P-256 (la clé privée ne quitte jamais le téléphone) ;
 * - deux amis calculent une clé commune par ECDH, puis chiffrent en AES-256-GCM ;
 * - un groupe a sa propre clé AES, envoyée chiffrée à chaque membre.
 * Les téléphones relais ne voient donc que du texte chiffré.
 */
object Crypto {
    private val rnd = SecureRandom()

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    /** Renvoie (clé privée, clé publique) encodées en Base64. */
    fun newKeyPair(): Pair<String, String> {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"))
        val kp = g.generateKeyPair()
        return b64(kp.private.encoded) to b64(kp.public.encoded)
    }

    fun randomKey(): String = b64(ByteArray(32).also { rnd.nextBytes(it) })

    /** Clé AES commune entre ma clé privée et la clé publique de l'autre. */
    fun sharedKey(myPriv: String, theirPub: String): ByteArray? = try {
        val kf = KeyFactory.getInstance("EC")
        val priv = kf.generatePrivate(PKCS8EncodedKeySpec(unb64(myPriv)))
        val pub = kf.generatePublic(X509EncodedKeySpec(unb64(theirPub)))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        MessageDigest.getInstance("SHA-256").digest(ka.generateSecret() + "pssst-v1".toByteArray())
    } catch (e: Exception) {
        null
    }

    /** `aad` lie le texte chiffré à son contexte (id, expéditeur, destinataire) : impossible de le déplacer. */
    fun encrypt(key: ByteArray, plain: String, aad: String): String {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return b64(iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(key: ByteArray, data: String, aad: String): String? = try {
        val raw = unb64(data)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        c.updateAAD(aad.toByteArray(Charsets.UTF_8))
        String(c.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
