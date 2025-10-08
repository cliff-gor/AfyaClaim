require "rails_helper"

RSpec.describe ClaimEvent do
  let(:claim) { create(:claim) }

  it "chains each event to the previous digest" do
    first = claim.events.create!(to_status: "submitted")
    second = claim.events.create!(from_status: "submitted", to_status: "under_review")
    expect(first.prev_digest).to be_nil
    expect(second.prev_digest).to eq(first.digest)
    expect(described_class.chain_intact?(claim.events.reload)).to be(true)
  end

  it "detects tampering in the middle of the chain" do
    claim.events.create!(to_status: "submitted")
    event = claim.events.create!(from_status: "submitted", to_status: "approved", note: "ok")
    claim.events.create!(from_status: "approved", to_status: "paid")
    described_class.where(id: event.id).update_all(note: "edited later")
    expect(described_class.chain_intact?(claim.events.reload)).to be(false)
  end

  it "is read-only once persisted" do
    event = claim.events.create!(to_status: "submitted")
    expect { event.update!(note: "x") }.to raise_error(ActiveRecord::ReadOnlyRecord)
  end
end
