# Append-only audit trail. Each event is chained to the previous one with a
# SHA-256 digest so that any edit or deletion in the middle of the chain is detectable.
class ClaimEvent < ApplicationRecord
  belongs_to :claim
  belongs_to :actor, class_name: "User", optional: true

  validates :to_status, inclusion: { in: Claim::STATUSES }
  validates :digest, presence: true

  before_validation :chain_digest, on: :create

  def readonly?
    persisted?
  end

  def payload
    {
      claim_id:, from_status:, to_status:, actor_id:, note:,
      metadata: metadata.to_h.sort.to_h,
      created_at: created_at&.utc&.iso8601(6)
    }.to_json
  end

  def self.chain_intact?(events)
    prev = nil
    events.all? do |event|
      ok = event.prev_digest == prev && event.digest == compute_digest(prev, event.payload)
      prev = event.digest
      ok
    end
  end

  def self.compute_digest(prev, payload)
    Digest::SHA256.hexdigest("#{prev}|#{payload}")
  end

  private

  def chain_digest
    self.created_at ||= Time.current
    self.prev_digest = ClaimEvent.where(claim_id:).order(:id).last&.digest
    self.digest = self.class.compute_digest(prev_digest, payload)
  end
end
