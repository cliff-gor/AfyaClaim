require "rails_helper"

RSpec.describe "Reimbursements and M-Pesa callbacks" do
  let(:admin) { create(:admin) }
  let(:pharmacy) { create(:pharmacy) }
  let(:product) { create(:product) }
  let!(:claims) do
    create_list(:claim, 3, pharmacy:, product:, status: "approved", reviewed_at: Time.current)
  end

  it "creates a payout batch, debits the ledger first and pays via the simulated sandbox" do
    perform_enqueued_jobs do
      post "/api/v1/reimbursements", headers: auth_headers(admin)
    end
    expect(response).to have_http_status(:created)

    reimbursement = Reimbursement.last
    expect(reimbursement).to have_attributes(status: "paid", amount_cents: 36_000)
    expect(reimbursement.mpesa_receipt).to start_with("SIM")
    expect(claims.map { |c| c.reload.status }).to all(eq("paid"))
    expect(LedgerEntry.balanced?).to be(true)
    expect(LedgerEntry.where(account: LedgerEntry.payable_account(pharmacy)).pluck(:direction)).to contain_exactly("credit", "debit")
  end

  it "only pays claims approved before the cutoff" do
    claims.first.update!(reviewed_at: 1.day.from_now)
    reimbursement = Payouts::CreateBatch.call(approved_before: Time.current).first
    expect(reimbursement.amount_cents).to eq(24_000)
    expect(claims.first.reload.reimbursement_id).to be_nil
  end

  it "is admin-only" do
    post "/api/v1/reimbursements", headers: auth_headers(create(:user))
    expect(response).to have_http_status(:forbidden)
  end

  it "lists reimbursements for staff" do
    pharmacy.reimbursements.create!(amount_cents: 100)
    get "/api/v1/reimbursements", headers: auth_headers(create(:user))
    expect(json[:data].first).to include(amount_cents: 100, status: "pending")
  end

  describe "B2C result callback" do
    let(:reimbursement) do
      Payouts::CreateBatch.call.first.tap { |r| r.update!(status: "processing", mpesa_conversation_id: "AG_123") }
    end
    let(:secret) { Rails.configuration.x.afyaclaim.mpesa[:callback_secret] }

    def callback(code, desc = "ok")
      { Result: { ResultCode: code, ResultDesc: desc, ConversationID: "AG_123",
                  OriginatorConversationID: reimbursement.idempotency_key, TransactionID: "QKX123ABC" } }.to_json
    end

    it "marks a successful payment as paid, once" do
      2.times do
        post "/api/v1/mpesa/b2c/result?secret=#{secret}", params: callback(0), headers: { "Content-Type" => "application/json" }
      end
      expect(reimbursement.reload).to have_attributes(status: "paid", mpesa_receipt: "QKX123ABC")
      expect(LedgerEntry.where(entry_group: "settle:#{reimbursement.id}").count).to eq(2)
    end

    it "reverses the ledger and releases claims on failure" do
      post "/api/v1/mpesa/b2c/result?secret=#{secret}", params: callback(2001, "Initiator information is invalid"),
        headers: { "Content-Type" => "application/json" }
      expect(reimbursement.reload).to have_attributes(status: "failed", failure_reason: "Initiator information is invalid")
      expect(claims.map { |c| c.reload.reimbursement_id }).to all(be_nil)
      expect(LedgerEntry.balanced?).to be(true)
    end

    it "rejects callbacks without the shared secret" do
      post "/api/v1/mpesa/b2c/result", params: callback(0), headers: { "Content-Type" => "application/json" }
      expect(response).to have_http_status(:unauthorized)
    end
  end
end
