module Fraud
  module Rules
    class AmountMismatch < Rule
      self.code = "amount_mismatch"
      self.severity = "high"

      def call
        expected = claim.product.subsidy_cents * claim.quantity
        return if claim.amount_cents == expected

        finding("Claimed KES #{format('%.2f', claim.amount_cents / 100.0)}, " \
                "programme rate is KES #{format('%.2f', expected / 100.0)}")
      end
    end
  end
end
