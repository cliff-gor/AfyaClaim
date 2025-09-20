require "rails_helper"

RSpec.describe InventoryItem do
  let(:item) { create(:inventory_item, quantity_on_hand: 5, reorder_level: 10) }

  it "applies relative deltas" do
    expect(item.apply_delta!(-3)).to be(true)
    expect(item.quantity_on_hand).to eq(2)
  end

  it "refuses deltas that would make stock negative" do
    expect(item.apply_delta!(-6)).to be(false)
    expect(item.quantity_on_hand).to eq(5)
  end

  it "knows when it is low on stock" do
    expect(item).to be_low_stock
    expect(described_class.low_stock).to include(item)
  end
end
