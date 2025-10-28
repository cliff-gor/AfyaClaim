require "rails_helper"

RSpec.describe "Offline sync" do
  let(:pharmacist) { create(:pharmacist) }
  let(:pharmacy) { pharmacist.pharmacy }
  let(:rdt) { create(:product, category: "malaria_test") }
  let!(:inventory) { create(:inventory_item, pharmacy:, product: rdt, quantity_on_hand: 10) }
  let(:sale_uuid) { SecureRandom.uuid }
  let(:claim_uuid) { SecureRandom.uuid }
  let(:payload) do
    {
      sales: [ {
        client_uuid: sale_uuid, sold_at: Time.current.change(hour: 9).iso8601, total_cents: rdt.unit_price_cents,
        patient: { pseudonym: Digest::SHA256.hexdigest("2547000"), age_band: "25-49", sex: "female" },
        items: [ { product_id: rdt.id, quantity: 2, unit_price_cents: rdt.unit_price_cents } ]
      } ],
      claims: [ {
        client_uuid: claim_uuid, sale_client_uuid: sale_uuid, product_id: rdt.id, quantity: 2,
        amount_cents: rdt.subsidy_cents * 2, test_result: "positive", submitted_at: Time.current.change(hour: 9).iso8601
      } ],
      stock_adjustments: [ { client_uuid: SecureRandom.uuid, product_id: rdt.id, delta: 20, reason: "restock" } ]
    }
  end

  it "applies a batch and reports per-record results" do
    post "/api/v1/sync", params: payload.to_json, headers: auth_headers(pharmacist)
    expect(response).to have_http_status(:ok)
    statuses = json[:results].to_h { |r| [ r[:type], r[:status] ] }
    expect(statuses).to eq("sale" => "accepted", "claim" => "accepted", "stock_adjustment" => "accepted")
    expect(inventory.reload.quantity_on_hand).to eq(10 - 2 + 20)
    claim = Claim.find_by(client_uuid: claim_uuid)
    expect(claim.sale.client_uuid).to eq(sale_uuid)
    expect(claim.patient.age_band).to eq("25-49")
  end

  it "is idempotent when a device retries" do
    2.times { post "/api/v1/sync", params: payload.to_json, headers: auth_headers(pharmacist) }
    expect(json[:results].pluck(:status)).to all(eq("duplicate"))
    expect(Sale.count).to eq(1)
    expect(Claim.count).to eq(1)
    expect(inventory.reload.quantity_on_hand).to eq(28)
  end

  it "keeps the sale but reports a conflict when server stock is short" do
    inventory.update!(quantity_on_hand: 1)
    post "/api/v1/sync", params: payload.slice(:sales).to_json, headers: auth_headers(pharmacist)
    expect(json[:results].first).to include(status: "conflict")
    expect(inventory.reload.quantity_on_hand).to eq(0)
  end

  it "rejects stock adjustments that would go negative" do
    body = { stock_adjustments: [ { client_uuid: SecureRandom.uuid, product_id: rdt.id, delta: -50, reason: "damaged" } ] }
    post "/api/v1/sync", params: body.to_json, headers: auth_headers(pharmacist)
    expect(json[:results].first).to include(status: "conflict")
  end

  it "rejects invalid records without failing the batch" do
    bad = payload.deep_dup
    bad[:sales].first[:items] = []
    post "/api/v1/sync", params: bad.to_json, headers: auth_headers(pharmacist)
    expect(json[:results].find { |r| r[:type] == "sale" }[:status]).to eq("rejected")
  end

  it "pulls deltas since a cursor" do
    get "/api/v1/sync", headers: auth_headers(pharmacist)
    cursor = json[:cursor]
    expect(json[:products].pluck(:id)).to include(rdt.id)
    expect(json[:inventory].first).to include(product_id: rdt.id, quantity_on_hand: 10)

    travel 1.minute do
      claim = create(:claim, pharmacy:, product: rdt)
      get "/api/v1/sync", params: { since: cursor }, headers: auth_headers(pharmacist)
      expect(json[:products]).to be_empty
      expect(json[:claims].pluck(:client_uuid)).to eq([ claim.client_uuid ])
    end
  end

  it "is limited to pharmacists" do
    post "/api/v1/sync", params: payload.to_json, headers: auth_headers(create(:user))
    expect(response).to have_http_status(:forbidden)
  end
end
