# Patients are pseudonymous. The device hashes the phone number with a programme salt
# and only the resulting digest ever reaches the server.
class Patient < ApplicationRecord
  AGE_BANDS = %w[0-4 5-14 15-24 25-49 50+].freeze
  SEXES = %w[female male].freeze

  has_many :sales, dependent: :nullify
  has_many :claims, dependent: :nullify

  validates :pseudonym, presence: true, uniqueness: true, format: { with: /\A[0-9a-f]{64}\z/ }
  validates :age_band, inclusion: { in: AGE_BANDS }, allow_nil: true
  validates :sex, inclusion: { in: SEXES }, allow_nil: true
end
