require "rails_helper"

RSpec.describe "Fraud rules" do
  let(:region) { create(:region) }
  let(:pharmacy) { create(:pharmacy, region:) }
  let(:rdt) { create(:product, name: "Malaria RDT", category: "malaria_test") }
  let!(:inventory) { create(:inventory_item, pharmacy:, product: rdt, quantity_on_hand: 500) }
  let(:now) { Time.zone.local(2026, 5, 4, 14, 0) }

  def claim_with_sale(at: now, quantity: 1, **opts)
    sale = create(:sale, pharmacy:, product: rdt, quantity:, sold_at: at)
    create(:claim, pharmacy:, product: rdt, sale:, quantity:, submitted_at: at, **opts)
  end

  describe Fraud::Rules::Velocity do
    it "fires when one pharmacy claims 50 malaria tests within an hour" do
      claims = Array.new(50) { |i| claim_with_sale(at: now + i.minutes) }
      finding = described_class.new(claims.last).call
      expect(finding.severity).to eq("high")
      expect(finding.message).to start_with("50 Malaria RDT claims in 60 min")
    end

    it "stays quiet for a normal day" do
      claims = Array.new(10) { |i| claim_with_sale(at: now + (i * 20).minutes) }
      expect(described_class.new(claims.last).call).to be_nil
    end
  end

  describe Fraud::Rules::NoMatchingSale do
    it "fires when there is no sale" do
      claim = create(:claim, pharmacy:, product: rdt, submitted_at: now)
      expect(described_class.new(claim).call.message).to eq("Claim has no matching sale")
    end

    it "fires when the sale does not cover the claimed quantity" do
      sale = create(:sale, pharmacy:, product: rdt, quantity: 1)
      claim = create(:claim, pharmacy:, product: rdt, sale:, quantity: 3)
      expect(described_class.new(claim).call.message).to include("claim asks for 3")
    end

    it "passes when the sale matches" do
      expect(described_class.new(claim_with_sale).call).to be_nil
    end
  end

  describe Fraud::Rules::DuplicatePatient do
    let(:patient) { create(:patient) }

    it "fires when the same patient claims the same product within 7 days" do
      create(:claim, pharmacy:, product: rdt, patient:, submitted_at: now - 3.days)
      claim = create(:claim, pharmacy:, product: rdt, patient:, submitted_at: now)
      expect(described_class.new(claim).call).to be_present
    end

    it "ignores claims older than the window and rejected claims" do
      create(:claim, pharmacy:, product: rdt, patient:, submitted_at: now - 9.days)
      create(:claim, pharmacy:, product: rdt, patient:, submitted_at: now - 1.day, status: "rejected", rejection_reason: "dup")
      claim = create(:claim, pharmacy:, product: rdt, patient:, submitted_at: now)
      expect(described_class.new(claim).call).to be_nil
    end
  end

  describe Fraud::Rules::AmountMismatch do
    it "fires when the amount differs from the programme rate" do
      claim = create(:claim, pharmacy:, product: rdt, amount_cents: 99_900)
      expect(described_class.new(claim).call.message).to include("KES 999.00")
    end
  end

  describe Fraud::Rules::StockInconsistency do
    it "fires when the pharmacy does not stock the product" do
      other = create(:product, name: "DMPA-SC", category: "family_planning")
      claim = create(:claim, pharmacy:, product: other)
      expect(described_class.new(claim).call.message).to include("does not stock")
    end

    it "fires when claims exceed the day's opening stock" do
      inventory.update!(quantity_on_hand: 2)
      claim = create(:claim, pharmacy:, product: rdt, quantity: 5, submitted_at: now)
      expect(described_class.new(claim).call.message).to eq("5 units claimed today but opening stock was 2")
    end

    it "passes when sales explain the claims" do
      claim = claim_with_sale(quantity: 3)
      inventory.update!(quantity_on_hand: 0)
      expect(described_class.new(claim).call).to be_nil
    end
  end

  describe Fraud::Rules::OffHours do
    it "fires for a 2am sale" do
      expect(described_class.new(claim_with_sale(at: now.change(hour: 2))).call.severity).to eq("low")
    end

    it "passes during the day" do
      expect(described_class.new(claim_with_sale).call).to be_nil
    end
  end

  describe Fraud::Rules::PositivityOutlier do
    it "fires when a pharmacy's positivity is far above its peers" do
      peers = create_list(:pharmacy, 4, region:)
      peers.each_with_index do |peer, i|
        12.times { |n| create(:claim, pharmacy: peer, product: rdt, submitted_at: now - n.hours, test_result: n < 3 + (i % 2) ? "positive" : "negative") }
      end
      12.times { |n| create(:claim, pharmacy:, product: rdt, submitted_at: now - n.hours, test_result: n < 11 ? "positive" : "negative") }
      claim = create(:claim, pharmacy:, product: rdt, submitted_at: now, test_result: "positive")
      expect(described_class.new(claim).call.message).to match(/Positivity \d+% vs regional/)
    end

    it "is not run inline" do
      expect(described_class.inline).to be(false)
    end
  end

  describe Fraud::RiskScore do
    around { |ex| travel_to(now + 1.hour) { ex.run } }

    it "scales with flags per claim, not raw volume" do
      claims = Array.new(10) { |i| claim_with_sale(at: now - i.hours) }
      create(:claim_flag, claim: claims.first, rule: "off_hours", severity: "low")
      described_class.recompute!(pharmacy)
      expect(pharmacy.reload.risk_score).to eq(18) # 1 - e^(-2/10)

      claims.each { |c| create(:claim_flag, claim: c, rule: "velocity", severity: "high") }
      described_class.recompute!(pharmacy)
      expect(pharmacy.reload.risk_score).to eq(100)
    end
  end

  describe Fraud::Engine do
    it "persists findings once and raises the pharmacy risk score" do
      claim = create(:claim, pharmacy:, product: rdt, submitted_at: now.change(hour: 2), amount_cents: 1)
      engine = described_class.new(claim)
      engine.run_inline!
      engine.run_inline!

      expect(claim.flags.pluck(:rule)).to contain_exactly("no_matching_sale", "amount_mismatch", "off_hours")
      expect(claim.reload.flags_count).to eq(3)
      expect(pharmacy.reload.risk_score).to eq(100) # 32 weight on a single claim saturates
    end
  end
end
