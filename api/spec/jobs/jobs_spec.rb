require "rails_helper"

RSpec.describe "Background jobs" do
  let(:pharmacy) { create(:pharmacy) }
  let(:product) { create(:product) }

  describe FraudScanJob do
    it "flags recent claims and recomputes risk scores" do
      create(:inventory_item, pharmacy:, product:)
      create(:claim, pharmacy:, product:, submitted_at: 1.day.ago)
      expect(described_class.perform_now).to eq(1)
      expect(pharmacy.reload.risk_score).to be_positive
    end
  end

  describe NightlyReconciliationJob do
    it "reports nothing when the books balance" do
      create(:claim, pharmacy:, product:, status: "approved")
      Payouts::CreateBatch.call
      expect(described_class.perform_now).to eq([])
    end

    it "detects a paid reimbursement without an M-Pesa receipt and re-queues stuck payouts" do
      pharmacy.reimbursements.create!(amount_cents: 100, status: "paid")
      stuck = pharmacy.reimbursements.create!(amount_cents: 100, status: "processing", updated_at: 3.hours.ago)
      issues = nil
      expect { issues = described_class.perform_now }.to have_enqueued_job(MpesaPayoutJob).with(stuck.id)
      expect(issues.map { |i| i[:kind] }).to include("paid_without_receipt", "stuck_processing", "amount_mismatch")
    end
  end

  describe MpesaPayoutJob do
    let(:reimbursement) do
      create(:claim, pharmacy:, product:, status: "approved")
      Payouts::CreateBatch.call.first
    end

    it "calls Daraja B2C with the idempotency key when credentials are configured" do
      config = Rails.configuration.x.afyaclaim.mpesa.merge(consumer_key: "key", consumer_secret: "secret")
      allow(Rails.configuration.x.afyaclaim).to receive(:mpesa).and_return(config)
      stub_request(:get, %r{/oauth/v1/generate}).to_return(json_body(access_token: "tkn"))
      b2c = stub_request(:post, %r{/mpesa/b2c/v3/paymentrequest})
        .with(body: hash_including("OriginatorConversationID" => reimbursement.idempotency_key, "Amount" => 120))
        .to_return(json_body(ConversationID: "AG_999", ResponseCode: "0"))

      described_class.perform_now(reimbursement.id)
      expect(b2c).to have_been_requested.once
      expect(reimbursement.reload).to have_attributes(status: "processing", mpesa_conversation_id: "AG_999", attempts: 1)

      # A retry must query status instead of paying again.
      status = stub_request(:post, %r{/transactionstatus}).to_return(json_body(ResponseCode: "0"))
      described_class.perform_now(reimbursement.id)
      expect(b2c).to have_been_requested.once
      expect(status).to have_been_requested.once
    end

    it "does nothing for already-paid reimbursements" do
      reimbursement.update!(status: "paid", mpesa_receipt: "X")
      expect { described_class.perform_now(reimbursement.id) }.not_to(change { reimbursement.reload.attempts })
    end

    def json_body(hash)
      { status: 200, body: hash.to_json, headers: { "Content-Type" => "application/json" } }
    end
  end

  describe DailyReportJob do
    it "emails the ops summary" do
      create(:claim, pharmacy:, product:, submitted_at: Date.yesterday.noon)
      expect { DailyReportJob.perform_now }.to have_enqueued_mail(OpsMailer, :daily_report)
    end
  end

  describe StockoutAlertJob do
    it "counts subsidised products at zero stock" do
      create(:inventory_item, pharmacy:, product:, quantity_on_hand: 0)
      expect(described_class.perform_now).to eq(1)
    end
  end
end
