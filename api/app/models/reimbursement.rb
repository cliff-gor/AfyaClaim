class Reimbursement < ApplicationRecord
  STATUSES = %w[pending processing paid failed].freeze
  MAX_ATTEMPTS = 3

  belongs_to :pharmacy
  has_many :claims, dependent: :nullify
  has_many :ledger_entries, dependent: :restrict_with_error

  validates :status, inclusion: { in: STATUSES }
  validates :amount_cents, numericality: { only_integer: true, greater_than: 0 }
  validates :idempotency_key, presence: true, uniqueness: true

  before_validation { self.idempotency_key ||= SecureRandom.uuid }

  STATUSES.each do |s|
    scope s, -> { where(status: s) }
    define_method(:"#{s}?") { status == s }
  end
end
