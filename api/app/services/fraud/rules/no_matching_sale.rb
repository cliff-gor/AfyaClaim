module Fraud
  module Rules
    class NoMatchingSale < Rule
      self.code = "no_matching_sale"
      self.severity = "high"

      def call
        sale = claim.sale
        return finding("Claim has no matching sale") if sale.nil?

        sold = sale.quantity_of(claim.product)
        return if sold >= claim.quantity

        finding("Sale #{sale.client_uuid.first(8)} has #{sold} × #{claim.product.name}, claim asks for #{claim.quantity}")
      end
    end
  end
end
