class LedgerEntry < ApplicationRecord
  DIRECTIONS = %w[debit credit].freeze
  FLOAT_ACCOUNT = "programme_float".freeze

  belongs_to :reimbursement, optional: true

  validates :account, presence: true
  validates :direction, inclusion: { in: DIRECTIONS }
  validates :amount_cents, numericality: { only_integer: true, greater_than: 0 }
  validates :entry_group, presence: true

  def readonly?
    persisted?
  end

  def self.payable_account(pharmacy)
    "payable:pharmacy:#{pharmacy.id}"
  end

  def self.balanced?
    where(direction: "debit").sum(:amount_cents) == where(direction: "credit").sum(:amount_cents)
  end

  def self.unbalanced_groups
    group(:entry_group)
      .having("SUM(CASE WHEN direction = 'debit' THEN amount_cents ELSE -amount_cents END) <> 0")
      .pluck(:entry_group)
  end
end
