require "rails_helper"

RSpec.describe "Pharmacies and analytics" do
  let(:reviewer) { create(:user) }
  let(:region) { create(:region, name: "Kisumu") }
  let(:pharmacy) { create(:pharmacy, region:, name: "Afya Bora Chemist", risk_score: 80) }
  let(:rdt) { create(:product, name: "Malaria RDT", category: "malaria_test") }

  before do
    create(:inventory_item, pharmacy:, product: rdt, quantity_on_hand: 0, reorder_level: 10)
    create(:claim, pharmacy:, product: rdt, test_result: "positive", submitted_at: 1.day.ago)
    create(:claim, pharmacy:, product: rdt, test_result: "negative", submitted_at: 1.day.ago, status: "approved", reviewed_at: Time.current)
    create(:claim_flag, claim: Claim.first)
  end

  it "lists pharmacies with claim stats ordered by risk" do
    create(:pharmacy, risk_score: 5)
    get "/api/v1/pharmacies", headers: auth_headers(reviewer)
    first = json[:data].first
    expect(first).to include(name: "Afya Bora Chemist", high_risk: true)
    expect(first[:stats]).to include(total: 2, flagged: 1, pending: 1)
  end

  it "filters high-risk pharmacies and searches by name" do
    create(:pharmacy, name: "Other", risk_score: 5)
    get "/api/v1/pharmacies", params: { high_risk: "true", q: "afya" }, headers: auth_headers(reviewer)
    expect(json[:data].pluck(:name)).to eq([ "Afya Bora Chemist" ])
  end

  it "shows pharmacy detail with inventory, recent flags and monthly history" do
    get "/api/v1/pharmacies/#{pharmacy.id}", headers: auth_headers(reviewer)
    expect(json[:data][:inventory].first).to include(low_stock: true)
    expect(json[:data][:recent_flags].first).to include(rule: "velocity")
    expect(json[:data][:monthly].sum { |m| m[:claims] }).to eq(2)
  end

  it "returns overview KPIs" do
    get "/api/v1/analytics/overview", headers: auth_headers(reviewer)
    expect(json[:data]).to include(pending_review: 1, flagged_pending: 1, approved_today: 1, stockouts: 1)
  end

  it "groups claims by region and product" do
    get "/api/v1/analytics/claims_by_region", headers: auth_headers(reviewer)
    expect(json[:data]).to eq([ { region_id: region.id, region: "Kisumu", claims: 2, value_cents: 24_000, flagged: 1 } ])

    get "/api/v1/analytics/claims_by_product", headers: auth_headers(reviewer)
    expect(json[:data].first).to include(product: "Malaria RDT", claims: 2, units: 2)
  end

  it "returns a daily trend with positivity" do
    get "/api/v1/analytics/trend", params: { days: 7 }, headers: auth_headers(reviewer)
    expect(json[:data].size).to eq(7)
    yesterday = json[:data].find { |d| d[:date] == 1.day.ago.to_date.iso8601 }
    expect(yesterday).to include(claims: 2, positivity: nil) # too few tests to report

    4.times { |i| create(:claim, pharmacy:, product: rdt, test_result: i.zero? ? "positive" : "negative", submitted_at: 1.day.ago) }
    get "/api/v1/analytics/trend", params: { days: 7 }, headers: auth_headers(reviewer)
    yesterday = json[:data].find { |d| d[:date] == 1.day.ago.to_date.iso8601 }
    expect(yesterday).to include(claims: 6, positivity: 0.333)
  end

  it "lists stock-outs" do
    get "/api/v1/analytics/stockouts", headers: auth_headers(reviewer)
    expect(json[:data].first).to include(pharmacy: "Afya Bora Chemist", stocked_out: true)
  end
end
