require "rails_helper"

RSpec.describe Claims::Submit do
  let(:pharmacy) { create(:pharmacy) }
  let(:product) { create(:product) }
  let!(:inventory) { create(:inventory_item, pharmacy:, product:) }
  let(:sale) { create(:sale, pharmacy:, product:) }
  let(:attrs) do
    { client_uuid: SecureRandom.uuid, sale_client_uuid: sale.client_uuid, product_id: product.id,
      quantity: 1, amount_cents: product.subsidy_cents, test_result: "negative", submitted_at: sale.sold_at }
  end

  it "creates a clean claim with a submission event" do
    result = described_class.call(pharmacy:, attrs:)
    expect(result.status).to eq(:accepted)
    expect(result.claim).to have_attributes(status: "submitted", sale:, flags_count: 0)
    expect(result.claim.events.map(&:to_status)).to eq([ "submitted" ])
  end

  it "is idempotent on client_uuid" do
    first = described_class.call(pharmacy:, attrs:)
    second = described_class.call(pharmacy:, attrs:)
    expect(second.status).to eq(:duplicate)
    expect(second.claim.id).to eq(first.claim.id)
    expect(Claim.count).to eq(1)
  end

  it "rejects a client_uuid already used by another pharmacy" do
    described_class.call(pharmacy:, attrs:)
    expect(described_class.call(pharmacy: create(:pharmacy), attrs:).status).to eq(:rejected)
  end

  it "flags a claim without a matching sale" do
    result = described_class.call(pharmacy:, attrs: attrs.except(:sale_client_uuid))
    expect(result.claim.flags.map(&:rule)).to include("no_matching_sale")
  end

  it "defaults the amount to the programme rate" do
    result = described_class.call(pharmacy:, attrs: attrs.merge(amount_cents: nil, quantity: 2))
    expect(result.claim.amount_cents).to eq(product.subsidy_cents * 2)
  end

  it "returns validation errors" do
    result = described_class.call(pharmacy:, attrs: attrs.merge(product_id: nil))
    expect(result.status).to eq(:rejected)
    expect(result.errors).to be_present
  end
end
