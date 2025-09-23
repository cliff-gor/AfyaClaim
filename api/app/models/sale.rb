class Sale < ApplicationRecord
  belongs_to :pharmacy
  belongs_to :patient, optional: true
  has_many :sale_items, dependent: :destroy
  has_many :products, through: :sale_items
  has_many :claims, dependent: :nullify

  accepts_nested_attributes_for :sale_items

  validates :client_uuid, presence: true, uniqueness: true
  validates :sold_at, presence: true
  validates :total_cents, numericality: { only_integer: true, greater_than_or_equal_to: 0 }
  validate :has_items

  before_validation :compute_total, if: -> { total_cents.blank? || total_cents.zero? }

  scope :recent, -> { order(sold_at: :desc) }

  def quantity_of(product)
    sale_items.select { |i| i.product_id == product.id }.sum(&:quantity)
  end

  private

  def compute_total
    self.total_cents = sale_items.sum { |i| i.quantity.to_i * i.unit_price_cents.to_i }
  end

  def has_items
    errors.add(:sale_items, "must include at least one product") if sale_items.empty?
  end
end
