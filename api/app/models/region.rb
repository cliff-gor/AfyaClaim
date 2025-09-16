class Region < ApplicationRecord
  has_many :pharmacies, dependent: :restrict_with_error

  validates :name, presence: true
  validates :code, presence: true, uniqueness: true
end
