package ke.afyaclaim.pos.domain

import ke.afyaclaim.pos.domain.model.AgeBand
import ke.afyaclaim.pos.domain.model.Product
import ke.afyaclaim.pos.domain.model.ProductCategory
import ke.afyaclaim.pos.domain.model.Sex
import ke.afyaclaim.pos.domain.model.TestResult
import ke.afyaclaim.pos.domain.sale.CartLine
import ke.afyaclaim.pos.domain.sale.DraftProblem
import ke.afyaclaim.pos.domain.sale.SaleDraft
import ke.afyaclaim.pos.domain.sale.SaleRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleRulesTest {
    private val rdt = Product(1, "RDT-01", "Malaria RDT", ProductCategory.MALARIA_TEST, 15_000, 10_000, true)
    private val act = Product(2, "ACT-AL", "Artemether-Lumefantrine", ProductCategory.ANTIMALARIAL, 30_000, 20_000, true)
    private val para = Product(3, "PARA", "Paracetamol", ProductCategory.OTHER, 5_000, 0, false)

    private fun draft(vararg lines: CartLine, result: TestResult? = TestResult.POSITIVE, claim: Boolean = true) =
        SaleDraft(lines.toList(), AgeBand.ADULT, Sex.FEMALE, null, result, claim)

    @Test
    fun `one claim per subsidised line with amount subsidy times quantity`() {
        val claims = SaleRules.claimsFor(draft(CartLine(rdt, 2), CartLine(act, 3), CartLine(para, 1)))
        assertEquals(2, claims.size)
        assertEquals(20_000L, claims.first { it.productId == 1L }.amountCents)
        assertEquals(60_000L, claims.first { it.productId == 2L }.amountCents)
    }

    @Test
    fun `test result only attaches to malaria test claims`() {
        val claims = SaleRules.claimsFor(draft(CartLine(rdt, 1), CartLine(act, 1), result = TestResult.NEGATIVE))
        assertEquals(TestResult.NEGATIVE, claims.first { it.productId == 1L }.testResult)
        assertNull(claims.first { it.productId == 2L }.testResult)
    }

    @Test
    fun `no claims when toggle is off`() {
        assertTrue(SaleRules.claimsFor(draft(CartLine(rdt, 1), claim = false)).isEmpty())
    }

    @Test
    fun `malaria test requires a result`() {
        assertEquals(DraftProblem.MissingTestResult, SaleRules.validate(draft(CartLine(rdt, 1), result = null), emptyMap()))
    }

    @Test
    fun `claim requires age band and sex`() {
        val d = SaleDraft(listOf(CartLine(act, 1)), null, Sex.MALE, null, null, true)
        assertEquals(DraftProblem.MissingPatientForClaim, SaleRules.validate(d, emptyMap()))
    }

    @Test
    fun `stock is checked against local inventory`() {
        val problem = SaleRules.validate(draft(CartLine(act, 5)), mapOf(2L to 3))
        assertEquals(DraftProblem.InsufficientStock("Artemether-Lumefantrine", 3), problem)
    }

    @Test
    fun `valid draft passes and totals add up`() {
        val d = draft(CartLine(rdt, 1), CartLine(para, 2))
        assertNull(SaleRules.validate(d, mapOf(1L to 10, 3L to 10)))
        assertEquals(25_000L, d.totalCents)
        assertEquals(10_000L, d.claimableCents)
    }

    @Test
    fun `invalid phone is rejected`() {
        val d = SaleDraft(listOf(CartLine(para, 1)), null, null, "12345", null, false)
        assertEquals(DraftProblem.InvalidPhone, SaleRules.validate(d, emptyMap()))
    }

    @Test
    fun `empty cart is rejected`() {
        assertEquals(DraftProblem.EmptyCart, SaleRules.validate(draft(CartLine(para, 0)), emptyMap()))
    }
}
