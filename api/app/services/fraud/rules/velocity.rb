module Fraud
  module Rules
    # Too many claims for the same product from one pharmacy in a short window,
    # e.g. 50 malaria tests in an hour.
    class Velocity < Rule
      self.code = "velocity"
      self.severity = "high"

      def call
        window_start = claim.submitted_at - config.velocity_window
        count = Claim.where(pharmacy_id: claim.pharmacy_id, product_id: claim.product_id)
          .where(submitted_at: window_start..claim.submitted_at)
          .sum(:quantity)
        return if count <= config.velocity_limit

        finding("#{count} #{claim.product.name} claims in #{config.velocity_window.in_minutes.to_i} min " \
                "(limit #{config.velocity_limit})")
      end
    end
  end
end
