class SaleItem < ApplicationRecord
  belongs_to :sale
  belongs_to :product

  validates :quantity, numericality: { only_integer: true, greater_than: 0 }
  validates :unit_price_cents, numericality: { only_integer: true, greater_than_or_equal_to: 0 }
end
