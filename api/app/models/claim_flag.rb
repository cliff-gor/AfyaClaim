class ClaimFlag < ApplicationRecord
  SEVERITIES = %w[low medium high].freeze
  WEIGHTS = { "low" => 2, "medium" => 6, "high" => 15 }.freeze

  belongs_to :claim

  validates :rule, presence: true, uniqueness: { scope: :claim_id }
  validates :severity, inclusion: { in: SEVERITIES }
  validates :message, presence: true

  scope :unresolved, -> { where(resolved: false) }

  after_commit :sync_claim_counter

  def weight
    WEIGHTS.fetch(severity)
  end

  private

  def sync_claim_counter
    claim.refresh_flags_count! unless claim.destroyed?
  end
end
