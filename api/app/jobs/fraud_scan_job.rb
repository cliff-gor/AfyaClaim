class FraudScanJob < ApplicationJob
  queue_as :default

  def perform(since: 14.days.ago)
    flagged = 0
    Claim.where(submitted_at: since..).includes(:product, :pharmacy, :sale).find_each do |claim|
      flagged += 1 if Fraud::Engine.new(claim).run_all!.any?
    end
    Pharmacy.find_each { |p| Fraud::RiskScore.recompute!(p) }
    Rails.logger.info(event: "fraud_scan_completed", claims_flagged: flagged)
    flagged
  end
end
