require "rails_helper"

RSpec.describe User do
  it { is_expected.to validate_inclusion_of(:role).in_array(described_class::ROLES) }

  it "normalises email" do
    expect(create(:user, email: "  Mercy@Example.COM ").email).to eq("mercy@example.com")
  end

  it "requires a pharmacy for pharmacists" do
    expect(build(:user, role: "pharmacist", pharmacy: nil)).not_to be_valid
  end

  it "treats reviewers and admins as staff" do
    expect(build(:user, role: "reviewer")).to be_staff
    expect(build(:admin)).to be_staff
    expect(build(:pharmacist)).not_to be_staff
  end
end
