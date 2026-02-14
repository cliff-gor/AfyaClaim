package ke.afyaclaim.pos.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun `formats whole shillings with grouping`() = assertEquals("KES 1,250", Money.format(125_000))
    @Test fun `keeps cents when present`() = assertEquals("KES 12.50", Money.format(1_250))
    @Test fun `zero`() = assertEquals("KES 0", Money.format(0))
    @Test fun `negative`() = assertEquals("KES -3,000", Money.format(-300_000))
    @Test fun `without currency`() = assertEquals("1,000,000", Money.format(100_000_000, withCurrency = false))
}
