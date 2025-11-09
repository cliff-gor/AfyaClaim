module Fraud
  module Rules
    class DuplicatePatient < Rule
      self.code = "duplicate_patient"
      self.severity = "medium"
      WINDOW = 7.days

      def call
        return if claim.patient_id.nil?

        prior = Claim.where(patient_id: claim.patient_id, product_id: claim.product_id)
          .where.not(id: claim.id)
          .where(submitted_at: (claim.submitted_at - WINDOW)..claim.submitted_at)
          .where.not(status: "rejected")
          .order(submitted_at: :desc).first
        return unless prior

        finding("Same patient claimed #{claim.product.name} on #{prior.submitted_at.to_date.to_fs(:long)} (#{prior.reference})")
      end
    end
  end
end
