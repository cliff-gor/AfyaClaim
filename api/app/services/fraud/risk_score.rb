module Fraud
  # Risk is a function of flag weight *per claim* over 30 days, so a busy pharmacy is not
  # penalised for volume. 1 - e^(-x) maps it onto 0–100 with diminishing returns:
  # one low flag per 100 claims ≈ 2, one high flag on every claim ≈ 100.
  module RiskScore
    WINDOW = 30.days

    def self.recompute!(pharmacy)
      weights = ClaimFlag.unresolved.joins(:claim)
        .where(claims: { pharmacy_id: pharmacy.id })
        .where(created_at: WINDOW.ago..)
        .group(:severity).count
      weighted = weights.sum { |severity, n| ClaimFlag::WEIGHTS.fetch(severity) * n }
      claims = [ pharmacy.claims.where(submitted_at: WINDOW.ago..).count, 1 ].max
      score = (100 * (1 - Math.exp(-weighted.to_f / claims))).round
      pharmacy.update_column(:risk_score, score.clamp(0, 100))
    end
  end
end
