require "rails_helper"

RSpec.describe Claim do
  it { is_expected.to validate_inclusion_of(:status).in_array(described_class::STATUSES) }
  it { is_expected.to belong_to(:pharmacy) }
  it { is_expected.to have_many(:events) }

  it "assigns a human-friendly reference derived from its id" do
    claim = create(:claim, submitted_at: Time.zone.local(2026, 3, 4, 10))
    expect(claim.reload.reference).to eq(format("AC-2026-%06d", claim.id))
  end

  it "requires a rejection reason once rejected" do
    claim = build(:claim, status: "rejected")
    expect(claim).not_to be_valid
    expect(claim.errors[:rejection_reason]).to be_present
  end

  it "rejects a non-positive amount" do
    expect(build(:claim, amount_cents: 0)).not_to be_valid
  end

  describe "#blocking_flags?" do
    it "is true only for unresolved high-severity flags" do
      claim = create(:claim)
      create(:claim_flag, claim:, severity: "medium")
      expect(claim.blocking_flags?).to be(false)
      create(:claim_flag, claim:, rule: "amount_mismatch", severity: "high")
      expect(claim.blocking_flags?).to be(true)
    end
  end

  it "keeps flags_count in sync with unresolved flags" do
    claim = create(:claim)
    flag = create(:claim_flag, claim:)
    expect(claim.reload.flags_count).to eq(1)
    flag.update!(resolved: true)
    expect(claim.reload.flags_count).to eq(0)
  end
end
