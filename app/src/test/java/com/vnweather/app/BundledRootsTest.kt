package com.vnweather.app

import com.vnweather.app.util.BundledRoots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Date

class BundledRootsTest {

    /** Published at https://letsencrypt.org/certificates/ */
    private val expected = listOf(
        "96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6", // ISRG Root X1
        "69729B8E15A86EFC177A57AFB7171DFC64ADD28C2FCA8CF1507E34453CCB1470", // ISRG Root X2
        "E57B7E6F150C419102E8D5C055729FF967B9D1A829BF00CEC89CA604EBF4A86F", // Root YR
        "E14FFCAD5B0025731006CAA43A121A22D8E9700F4FB9CF852F02A708AA5D5666"  // Root YE
    )

    @Test
    fun allRootsParseAndMatchPublishedFingerprints() {
        val certs = BundledRoots.certificates()
        assertEquals(4, certs.size)
        val fingerprints = certs.map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString("") { "%02X".format(it) }
        }
        assertEquals(expected, fingerprints)
    }

    @Test
    fun rootsAreSelfSignedAndCurrentlyValid() {
        BundledRoots.certificates().forEach { cert ->
            assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
            cert.verify(cert.publicKey)
            assertTrue(cert.notAfter.after(Date()))
        }
    }

    @Test
    fun bundledTrustManagerExposesTheRoots() {
        assertEquals(4, BundledRoots.bundledTrustManager().acceptedIssuers.size)
    }
}
