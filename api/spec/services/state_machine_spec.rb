require "rails_helper"

RSpec.describe Claims::StateMachine do
  let(:reviewer) { create(:user) }
  let(:claim) { create(:claim).tap { |c| described_class.record_submission!(c) } }
  let(:machine) { described_class.new(claim, actor: reviewer) }

  it "moves a claim through review to approval with an audit event per step" do
    machine.start_review!
    machine.approve!(note: "Looks good")

    expect(claim.reload.status).to eq("approved")
    expect(claim.reviewed_by).to eq(reviewer)
    expect(claim.events.pluck(:from_status, :to_status)).to eq(
      [ [ nil, "submitted" ], %w[submitted under_review], %w[under_review approved] ]
    )
    expect(ClaimEvent.chain_intact?(claim.events)).to be(true)
  end

  it "requires a reason to reject" do
    expect { machine.reject!(reason: "") }.to raise_error(ArgumentError)
    machine.reject!(reason: "No sale on record")
    expect(claim.reload).to have_attributes(status: "rejected", rejection_reason: "No sale on record")
  end

  it "refuses invalid transitions" do
    machine.reject!(reason: "Duplicate")
    expect { machine.approve! }.to raise_error(described_class::InvalidTransition, /rejected/)
  end

  it "blocks approval while a high-severity flag is unresolved" do
    create(:claim_flag, claim:, severity: "high")
    expect { machine.approve! }.to raise_error(described_class::BlockedByFlags)
  end

  it "allows an explicit override, resolving flags and recording it in the audit trail" do
    create(:claim_flag, claim:, severity: "high", rule: "velocity")
    machine.approve!(note: "Outbreak confirmed by CHV", override: true)

    expect(claim.reload).to be_approved
    expect(claim.flags_count).to eq(0)
    expect(claim.events.last.metadata).to include("override" => true, "overridden_flags" => [ "velocity" ])
  end

  it "only pays approved claims" do
    reimbursement = create(:pharmacy).reimbursements.create!(amount_cents: 100)
    expect { machine.pay!(reimbursement:) }.to raise_error(described_class::InvalidTransition)
  end
end
