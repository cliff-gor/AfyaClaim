package ke.afyaclaim.pos.domain

import ke.afyaclaim.pos.domain.sale.PseudonymHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PseudonymHasherTest {
    private val hasher = PseudonymHasher("test-salt")

    @Test
    fun `hash is lowercase hex sha256 of salt and normalised phone`() {
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("test-salt:254712345678".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, hasher.pseudonymFor("0712345678"))
        assertEquals(64, expected.length)
    }

    @Test
    fun `local, international and spaced formats map to the same pseudonym`() {
        val a = hasher.pseudonymFor("0712 345 678")
        val b = hasher.pseudonymFor("+254 712 345 678")
        val c = hasher.pseudonymFor("254712345678")
        val d = hasher.pseudonymFor("712345678")
        assertEquals(a, b)
        assertEquals(b, c)
        assertEquals(c, d)
    }

    @Test
    fun `new 01xx prefixes are supported`() {
        assertEquals("254110123456", PseudonymHasher.normalise("0110 123 456"))
    }

    @Test
    fun `different salts give different pseudonyms`() {
        assertNotEquals(
            PseudonymHasher("programme-a").pseudonymFor("0712345678"),
            PseudonymHasher("programme-b").pseudonymFor("0712345678"),
        )
    }

    @Test
    fun `different phones give different pseudonyms`() {
        assertNotEquals(hasher.pseudonymFor("0712345678"), hasher.pseudonymFor("0712345679"))
    }

    @Test
    fun `pseudonym never contains the phone digits`() {
        val pseudonym = hasher.pseudonymFor("0712345678")!!
        assertFalse(pseudonym.contains("712345678"))
        assertTrue(pseudonym.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `blank or implausible numbers yield no pseudonym`() {
        assertNull(hasher.pseudonymFor(null))
        assertNull(hasher.pseudonymFor("   "))
        assertNull(hasher.pseudonymFor("12345"))
        assertNull(hasher.pseudonymFor("0812345678"))
        assertNull(hasher.pseudonymFor("255712345678"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank salt is refused`() {
        PseudonymHasher(" ")
    }
}
