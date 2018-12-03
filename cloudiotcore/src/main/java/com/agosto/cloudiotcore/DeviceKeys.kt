package com.agosto.cloudiotcore

import android.annotation.TargetApi
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import javax.security.auth.x500.X500Principal
import android.security.KeyPairGeneratorSpec
import java.math.BigInteger
import java.util.*


class DeviceKeys(context: Context) {

    var privateKey: PrivateKey? = null
    var publicKey: PublicKey? = null
    var certificate: Certificate? = null

    init {
        if(!loadStoredKey()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                generateKeys()
            } else {
                generateKeysPreM(context)
            }
            loadStoredKey()
        }
    }

    @TargetApi(23)
    private fun generateKeys() {
        try {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            kpg!!.initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setUserAuthenticationRequired(false)
                    .setCertificateSubject(X500Principal("CN=unused"))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build())
            val kp = kpg.generateKeyPair()
            publicKey = kp.public
            privateKey = kp.private

        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: NoSuchProviderException) {
            e.printStackTrace()
        } catch (e: InvalidAlgorithmParameterException) {
            e.printStackTrace()
        }
    }

    private fun generateKeysPreM(context: Context) {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance()
        end.add(Calendar.YEAR, 30)
        val spec = KeyPairGeneratorSpec.Builder(context)
                .setKeySize(2048)
                .setAlias(ALIAS)
                .setSubject(X500Principal("CN=unused"))
                .setSerialNumber(BigInteger.TEN)
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .build()
        val kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
        kpg.initialize(spec)
        val kp = kpg.generateKeyPair()
        publicKey = kp.public
        privateKey = kp.private
    }


    private fun loadStoredKey(): Boolean {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val entry = ks.getEntry(ALIAS, null)
            if (entry !is KeyStore.PrivateKeyEntry) {
                Log.w(TAG, "Not an instance of a PrivateKeyEntry")
            } else {
                publicKey = entry.certificate.publicKey
                privateKey = entry.privateKey
                certificate = entry.certificate
                Log.d(TAG,"Loading stored keys successful")
                return true
            }
        } catch (e: KeyStoreException) {
            e.printStackTrace()
        } catch (e: CertificateException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: UnrecoverableEntryException) {
            e.printStackTrace()
        }
        Log.d(TAG,"Loading stored keys failed")
        return false
    }

    fun encodedCertificate(): String {
        return try {
            Base64.encodeToString(certificate?.encoded, Base64.DEFAULT)
        } catch (e: CertificateEncodingException) {
            e.printStackTrace()
            ""
        }
    }

    companion object {
        const val TAG = "DeviceKeys"
        const val ALIAS = "cloudiotcore"

        fun deleteKeys() {
            try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                ks.deleteEntry(ALIAS)
            } catch (e: KeyStoreException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            } catch (e: CertificateException) {
                e.printStackTrace()
            }
        }

    }

}


