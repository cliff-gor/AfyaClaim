class Claim < ApplicationRecord
  STATUSES = %w[submitted under_review approved rejected paid].freeze
  TEST_RESULTS = %w[positive negative].freeze

  belongs_to :pharmacy
  belongs_to :sale, optional: true
  belongs_to :product
  belongs_to :patient, optional: true
  belongs_to :reviewed_by, class_name: "User", optional: true
  belongs_to :reimbursement, optional: true
  has_many :events, -> { order(:id) }, class_name: "ClaimEvent", dependent: :destroy
  has_many :flags, class_name: "ClaimFlag", dependent: :destroy

  validates :client_uuid, presence: true, uniqueness: true
  validates :reference, presence: true, uniqueness: true
  validates :status, inclusion: { in: STATUSES }
  validates :quantity, numericality: { only_integer: true, greater_than: 0 }
  validates :amount_cents, numericality: { only_integer: true, greater_than: 0 }
  validates :test_result, inclusion: { in: TEST_RESULTS }, allow_nil: true
  validates :submitted_at, presence: true
  validates :rejection_reason, presence: true, if: -> { status == "rejected" }

  before_validation :assign_provisional_reference, on: :create
  after_create :assign_reference

  STATUSES.each do |s|
    scope s, -> { where(status: s) }
    define_method(:"#{s}?") { status == s }
  end
  scope :flagged, -> { where("flags_count > 0") }
  scope :unflagged, -> { where(flags_count: 0) }
  scope :awaiting_review, -> { where(status: %w[submitted under_review]) }
  scope :newest_first, -> { order(submitted_at: :desc, id: :desc) }

  def unresolved_flags
    flags.reject(&:resolved)
  end

  def blocking_flags?
    flags.where(resolved: false, severity: "high").exists?
  end

  def refresh_flags_count!
    update_column(:flags_count, flags.where(resolved: false).count)
  end

  private

  def assign_provisional_reference
    self.reference ||= "PENDING-#{client_uuid.presence || SecureRandom.uuid}"
  end

  # References are human-friendly and derived from the primary key, so they are
  # gap-free per database and never collide across devices.
  def assign_reference
    return unless reference.start_with?("PENDING-")

    update_column(:reference, format("AC-%<year>d-%<id>06d", year: submitted_at.year, id:))
  end
end
