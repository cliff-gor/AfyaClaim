module Fraud
  module Rules
    # Nightly only: a pharmacy whose malaria positivity is far above its region's
    # is either seeing an outbreak (useful signal) or recording false positives.
    class PositivityOutlier < Rule
      self.code = "positivity_outlier"
      self.severity = "medium"
      self.inline = false
      WINDOW = 14.days
      MIN_TESTS = 10

      def call
        return unless claim.product.malaria_test? && claim.test_result == "positive"

        rates = regional_rates
        mine = rates[claim.pharmacy_id]
        others = rates.except(claim.pharmacy_id).values
        return if mine.nil? || others.size < 2

        mean = others.sum / others.size
        sd = Math.sqrt(others.sum { |r| (r - mean)**2 } / others.size)
        return if mine <= mean + (2 * sd)

        finding("Positivity #{(mine * 100).round}% vs regional #{(mean * 100).round}% ± #{(sd * 100).round}")
      end

      private

      def regional_rates
        Claim.joins(:pharmacy, :product)
          .where(pharmacies: { region_id: claim.pharmacy.region_id }, products: { category: "malaria_test" })
          .where(submitted_at: (claim.submitted_at - WINDOW)..claim.submitted_at)
          .where.not(test_result: nil)
          .group(:pharmacy_id)
          .having("COUNT(*) >= ?", MIN_TESTS)
          .pluck(:pharmacy_id, Arel.sql("AVG(CASE WHEN test_result = 'positive' THEN 1.0 ELSE 0.0 END)"))
          .to_h.transform_values(&:to_f)
      end
    end
  end
end
