class Pharmacy < ApplicationRecord
  STATUSES = %w[active suspended].freeze
  HIGH_RISK_THRESHOLD = 70

  belongs_to :region
  has_many :users, dependent: :nullify
  has_many :inventory_items, dependent: :destroy
  has_many :products, through: :inventory_items
  has_many :sales, dependent: :restrict_with_error
  has_many :claims, dependent: :restrict_with_error
  has_many :reimbursements, dependent: :restrict_with_error

  validates :name, :town, presence: true
  validates :license_number, presence: true, uniqueness: true
  validates :status, inclusion: { in: STATUSES }
  validates :risk_score, numericality: { in: 0..100 }
  validates :mpesa_phone, format: { with: /\A2547\d{8}\z|\A2541\d{8}\z/ }, allow_blank: true

  scope :active, -> { where(status: "active") }
  scope :high_risk, -> { where(risk_score: HIGH_RISK_THRESHOLD..) }

  def high_risk?
    risk_score >= HIGH_RISK_THRESHOLD
  end
end
