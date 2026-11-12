package com.mohammad.tvdraw

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * HTTPS identity of this TV. Phone browsers only allow the microphone and the tilt sensors on https,
 * so the app serves the page on https too, with a self-signed certificate.
 * The key pair is created on the device the first time the app runs and never leaves it:
 * every TV has its own key and nothing secret ships inside the app.
 */
object Tls {
    fun context(dir: File): SSLContext {
        val (key, cert) = load(dir) ?: create(dir)
        val pw = CharArray(0)
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()); ks.load(null, null)
        ks.setKeyEntry("tv", key, pw, arrayOf(cert))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); kmf.init(ks, pw)
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
    }

    private fun load(dir: File): Pair<PrivateKey, Certificate>? = try {
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(File(dir, "tls.key").readBytes()))
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(File(dir, "tls.crt").inputStream())
        Pair(key, cert)
    } catch (e: Exception) { null }

    private fun create(dir: File): Pair<PrivateKey, Certificate> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val der = selfSigned(kp.private, kp.public.encoded)
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream())
        try { dir.mkdirs(); File(dir, "tls.key").writeBytes(kp.private.encoded); File(dir, "tls.crt").writeBytes(der) } catch (e: Exception) { }
        return Pair(kp.private, cert)
    }

    // A minimal X.509 v3 certificate written by hand, so the app needs no crypto library.
    private val SHA256_RSA = seq(tlv(0x06, byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)), tlv(0x05, ByteArray(0)))

    private fun selfSigned(key: PrivateKey, publicKeyInfo: ByteArray): ByteArray {
        val name = seq(tlv(0x31, seq(tlv(0x06, byteArrayOf(0x55, 0x04, 0x03)), tlv(0x0C, "TV Draw".toByteArray()))))
        // Fixed dates on purpose: a TV box with a wrong clock must still produce a usable certificate.
        val validity = seq(tlv(0x17, "200101000000Z".toByteArray()), tlv(0x17, "491231235959Z".toByteArray()))
        val serial = tlv(0x02, BigInteger(62, SecureRandom()).add(BigInteger.ONE).toByteArray())
        val version = tlv(0xA0, tlv(0x02, byteArrayOf(2)))
        val tbs = seq(version, serial, SHA256_RSA, name, validity, name, publicKeyInfo)
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(key); update(tbs); sign() }
        return seq(tbs, SHA256_RSA, tlv(0x03, byteArrayOf(0) + sig))
    }

    private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun tlv(tag: Int, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(); o.write(tag)
        val n = body.size
        when { n < 0x80 -> o.write(n); n < 0x100 -> { o.write(0x81); o.write(n) }; else -> { o.write(0x82); o.write(n shr 8); o.write(n and 0xFF) } }
        o.write(body); return o.toByteArray()
    }
}
