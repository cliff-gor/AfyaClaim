class StockAdjustment < ApplicationRecord
  REASONS = %w[sale restock damaged expired count_correction].freeze

  belongs_to :inventory_item

  validates :client_uuid, presence: true, uniqueness: true
  validates :delta, numericality: { only_integer: true, other_than: 0 }
  validates :reason, inclusion: { in: REASONS }
end
