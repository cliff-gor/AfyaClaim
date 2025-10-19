require "rails_helper"

RSpec.describe "Claims" do
  let(:reviewer) { create(:user, role: "reviewer") }
  let(:pharmacist) { create(:pharmacist) }
  let(:pharmacy) { pharmacist.pharmacy }
  let(:product) { create(:product) }
  let!(:inventory) { create(:inventory_item, pharmacy:, product:) }

  def make_claim(**opts)
    sale = create(:sale, pharmacy:, product:)
    create(:claim, pharmacy:, product:, sale:, **opts).tap { |c| Claims::StateMachine.record_submission!(c) }
  end

  describe "GET /api/v1/claims" do
    it "lists claims newest first with pagination meta" do
      older = make_claim(submitted_at: 2.days.ago)
      newer = make_claim(submitted_at: 1.hour.ago)
      get "/api/v1/claims", headers: auth_headers(reviewer)
      expect(json[:data].pluck(:id)).to eq([ newer.id, older.id ])
      expect(json[:meta]).to include(page: 1, total: 2)
    end

    it "filters by status, flagged and search" do
      flagged = make_claim
      create(:claim_flag, claim: flagged)
      make_claim(status: "approved")
      get "/api/v1/claims", params: { status: "submitted", flagged: "true" }, headers: auth_headers(reviewer)
      expect(json[:data].pluck(:id)).to eq([ flagged.id ])
      expect(json[:data].first[:top_flag]).to include(rule: "velocity", severity: "high")

      get "/api/v1/claims", params: { q: flagged.reload.reference }, headers: auth_headers(reviewer)
      expect(json[:data].pluck(:id)).to eq([ flagged.id ])
    end

    it "is forbidden to pharmacists" do
      get "/api/v1/claims", headers: auth_headers(pharmacist)
      expect(response).to have_http_status(:forbidden)
    end
  end

  describe "GET /api/v1/claims/:id" do
    it "returns the claim with sale, flags, events and an intact audit chain" do
      claim = make_claim
      get "/api/v1/claims/#{claim.id}", headers: auth_headers(reviewer)
      expect(json[:data]).to include(id: claim.id, audit_intact: true)
      expect(json.dig(:data, :sale, :items).first).to include(product_name: product.name)
      expect(json.dig(:data, :events).first).to include(to_status: "submitted", actor: "Device sync")
    end

    it "lets a pharmacist see their own claims only" do
      mine = make_claim
      other = create(:claim)
      get "/api/v1/claims/#{mine.id}", headers: auth_headers(pharmacist)
      expect(response).to have_http_status(:ok)
      get "/api/v1/claims/#{other.id}", headers: auth_headers(pharmacist)
      expect(response).to have_http_status(:forbidden)
    end

    it "404s for unknown claims" do
      get "/api/v1/claims/0", headers: auth_headers(reviewer)
      expect(response).to have_http_status(:not_found)
    end
  end

  describe "POST /api/v1/claims" do
    it "submits a claim for the pharmacist's pharmacy" do
      sale = create(:sale, pharmacy:, product:)
      body = { claim: { client_uuid: SecureRandom.uuid, sale_client_uuid: sale.client_uuid, product_id: product.id, quantity: 1 } }
      post "/api/v1/claims", params: body.to_json, headers: auth_headers(pharmacist)
      expect(response).to have_http_status(:created)
      expect(json[:data]).to include(status: "submitted", amount_cents: product.subsidy_cents, flags_count: 0)

      post "/api/v1/claims", params: body.to_json, headers: auth_headers(pharmacist)
      expect(response).to have_http_status(:ok)
      expect(json[:status]).to eq("duplicate")
    end

    it "is forbidden to reviewers" do
      post "/api/v1/claims", params: { claim: { product_id: product.id } }.to_json, headers: auth_headers(reviewer)
      expect(response).to have_http_status(:forbidden)
    end
  end

  describe "review actions" do
    let(:claim) { make_claim }

    it "starts review, then approves" do
      post "/api/v1/claims/#{claim.id}/start_review", headers: auth_headers(reviewer)
      expect(json.dig(:data, :status)).to eq("under_review")
      post "/api/v1/claims/#{claim.id}/approve", params: { note: "ok" }.to_json, headers: auth_headers(reviewer)
      expect(json.dig(:data, :status)).to eq("approved")
      expect(json.dig(:data, :reviewed_by, :id)).to eq(reviewer.id)
      expect(json.dig(:data, :events).size).to eq(3)
    end

    it "rejects with a reason" do
      post "/api/v1/claims/#{claim.id}/reject", params: { reason: "Blurry receipt" }.to_json, headers: auth_headers(reviewer)
      expect(json.dig(:data, :status)).to eq("rejected")
      expect(json.dig(:data, :rejection_reason)).to eq("Blurry receipt")
    end

    it "requires a reason to reject" do
      post "/api/v1/claims/#{claim.id}/reject", headers: auth_headers(reviewer)
      expect(response).to have_http_status(:unprocessable_entity)
    end

    it "returns 422 for invalid transitions" do
      claim.update!(status: "paid")
      post "/api/v1/claims/#{claim.id}/approve", headers: auth_headers(reviewer)
      expect(response).to have_http_status(:unprocessable_entity)
      expect(json.dig(:error, :code)).to eq("invalid_transition")
    end

    it "blocks approval of a claim with a high flag unless overridden" do
      create(:claim_flag, claim:)
      post "/api/v1/claims/#{claim.id}/approve", headers: auth_headers(reviewer)
      expect(json.dig(:error, :code)).to eq("blocked_by_flags")
      post "/api/v1/claims/#{claim.id}/approve", params: { override: true, note: "Verified" }.to_json, headers: auth_headers(reviewer)
      expect(json.dig(:data, :status)).to eq("approved")
    end
  end

  describe "POST /api/v1/claims/bulk_approve" do
    it "approves clean claims and skips flagged ones" do
      clean = make_claim
      flagged = make_claim
      create(:claim_flag, claim: flagged)
      post "/api/v1/claims/bulk_approve", params: { ids: [ clean.id, flagged.id ] }.to_json, headers: auth_headers(reviewer)
      results = json[:results].index_by { |r| r[:id] }
      expect(results[clean.id][:status]).to eq("approved")
      expect(results[flagged.id][:status]).to eq("skipped")
    end
  end
end
