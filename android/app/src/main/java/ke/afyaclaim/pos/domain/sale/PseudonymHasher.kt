package ke.afyaclaim.pos.domain.sale

import java.security.MessageDigest

/**
 * Derives a stable, non-reversible patient pseudonym from a phone number.
 *
 * The raw phone number never leaves the device: only SHA-256(salt + ":" + normalised phone)
 * is stored and synced. Kenyan numbers are normalised to the 2547XXXXXXXX / 2541XXXXXXXX form
 * so "0712 345 678", "+254712345678" and "254712345678" map to the same patient.
 */
class PseudonymHasher(private val salt: String) {

    init {
        require(salt.isNotBlank()) { "Pseudonym salt must not be blank" }
    }

    fun pseudonymFor(phone: String?): String? {
        val normalised = normalise(phone) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$normalised".toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        /** Returns the canonical 12-digit form, or null when the input is not a plausible phone number. */
        fun normalise(phone: String?): String? {
            if (phone.isNullOrBlank()) return null
            val digits = phone.filter(Char::isDigit)
            val canonical = when {
                digits.length == 12 && digits.startsWith("254") -> digits
                digits.length == 10 && digits.startsWith("0") -> "254" + digits.drop(1)
                digits.length == 9 && (digits.startsWith("7") || digits.startsWith("1")) -> "254$digits"
                else -> return null
            }
            val subscriber = canonical.drop(3)
            return if (subscriber.first() == '7' || subscriber.first() == '1') canonical else null
        }
    }
}
