require "rails_helper"

RSpec.describe "Catalogue, inventory and sales" do
  let(:pharmacist) { create(:pharmacist) }
  let!(:product) { create(:product) }
  let!(:item) { create(:inventory_item, pharmacy: pharmacist.pharmacy, product:, quantity_on_hand: 30) }

  it "lists products" do
    get "/api/v1/products", headers: auth_headers(pharmacist)
    expect(json[:data].first).to include(sku: product.sku, subsidy_cents: 12_000)
  end

  it "lists and updates the pharmacy's own inventory with optimistic locking" do
    get "/api/v1/inventory", headers: auth_headers(pharmacist)
    expect(json[:data].first).to include(product_id: product.id, quantity_on_hand: 30)

    patch "/api/v1/inventory/#{item.id}", params: { inventory_item: { quantity_on_hand: 25, lock_version: 0 } }.to_json,
      headers: auth_headers(pharmacist)
    expect(json.dig(:data, :quantity_on_hand)).to eq(25)

    patch "/api/v1/inventory/#{item.id}", params: { inventory_item: { quantity_on_hand: 20, lock_version: 0 } }.to_json,
      headers: auth_headers(pharmacist)
    expect(response).to have_http_status(:conflict)
  end

  it "records a sale and lists it" do
    body = { sale: { client_uuid: SecureRandom.uuid, items: [ { product_id: product.id, quantity: 2, unit_price_cents: 15_000 } ] } }
    post "/api/v1/sales", params: body.to_json, headers: auth_headers(pharmacist)
    expect(response).to have_http_status(:created)
    expect(json.dig(:data, :total_cents)).to eq(30_000)

    get "/api/v1/sales", headers: auth_headers(pharmacist)
    expect(json[:meta][:total]).to eq(1)
  end

  it "serves the OpenAPI document" do
    get "/api-docs/openapi.yaml"
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("openapi: 3.0")
  end
end
