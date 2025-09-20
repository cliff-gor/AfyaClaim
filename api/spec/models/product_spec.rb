require "rails_helper"

RSpec.describe Product do
  it { is_expected.to validate_inclusion_of(:category).in_array(described_class::CATEGORIES) }

  it "does not allow a subsidy above the unit price" do
    product = build(:product, unit_price_cents: 100, subsidy_cents: 200)
    expect(product).not_to be_valid
  end
end
