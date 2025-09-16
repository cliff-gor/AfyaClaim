class User < ApplicationRecord
  ROLES = %w[pharmacist reviewer admin].freeze

  has_secure_password
  belongs_to :pharmacy, optional: true

  normalizes :email, with: ->(email) { email.strip.downcase }

  validates :name, presence: true
  validates :email, presence: true, uniqueness: { case_sensitive: false },
    format: { with: URI::MailTo::EMAIL_REGEXP }
  validates :role, inclusion: { in: ROLES }
  validates :password, length: { minimum: 8 }, allow_nil: true
  validates :pharmacy, presence: true, if: :pharmacist?

  ROLES.each do |r|
    define_method(:"#{r}?") { role == r }
  end

  def staff?
    reviewer? || admin?
  end
end
