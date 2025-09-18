class InventoryItem < ApplicationRecord
  belongs_to :pharmacy
  belongs_to :product
  has_many :stock_adjustments, dependent: :destroy

  validates :quantity_on_hand, numericality: { only_integer: true, greater_than_or_equal_to: 0 }
  validates :reorder_level, numericality: { only_integer: true, greater_than_or_equal_to: 0 }
  validates :product_id, uniqueness: { scope: :pharmacy_id }

  scope :low_stock, -> { where("quantity_on_hand <= reorder_level") }
  scope :stocked_out, -> { where(quantity_on_hand: 0) }

  def low_stock?
    quantity_on_hand <= reorder_level
  end

  # Applies a relative change atomically so that concurrent offline devices merge
  # instead of overwriting each other. Returns false if stock would go negative.
  def apply_delta!(delta)
    rows = self.class.where(id: id)
      .where("quantity_on_hand + ? >= 0", delta)
      .update_all([ "quantity_on_hand = quantity_on_hand + ?, updated_at = ?, lock_version = lock_version + 1", delta, Time.current ])
    reload
    rows == 1
  end
end
