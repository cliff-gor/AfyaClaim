class Product < ApplicationRecord
  CATEGORIES = %w[malaria_test antimalarial family_planning ors_zinc other].freeze

  has_many :inventory_items, dependent: :restrict_with_error
  has_many :sale_items, dependent: :restrict_with_error
  has_many :claims, dependent: :restrict_with_error

  validates :sku, presence: true, uniqueness: true
  validates :name, presence: true
  validates :category, inclusion: { in: CATEGORIES }
  validates :unit_price_cents, numericality: { only_integer: true, greater_than_or_equal_to: 0 }
  validates :subsidy_cents, numericality: { only_integer: true, greater_than_or_equal_to: 0 }
  validate :subsidy_not_above_price

  scope :subsidised, -> { where(subsidised: true) }

  def malaria_test?
    category == "malaria_test"
  end

  private

  def subsidy_not_above_price
    return if subsidy_cents.to_i <= unit_price_cents.to_i

    errors.add(:subsidy_cents, "cannot exceed unit price")
  end
end
