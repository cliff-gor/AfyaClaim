require "rails_helper"

RSpec.describe "Auth" do
  let!(:user) { create(:pharmacist, email: "mercy@afyaclaim.test", name: "Mercy Atieno") }

  it "logs in with valid credentials" do
    post "/api/v1/auth/login", params: { email: "MERCY@afyaclaim.test", password: "password123" }
    expect(response).to have_http_status(:ok)
    expect(json[:user]).to include(email: "mercy@afyaclaim.test", role: "pharmacist")
    expect(json.dig(:user, :pharmacy, :id)).to eq(user.pharmacy_id)
    expect(Auth::JsonWebToken.decode(json[:token])["sub"]).to eq(user.id)
  end

  it "rejects a wrong password" do
    post "/api/v1/auth/login", params: { email: "mercy@afyaclaim.test", password: "nope" }
    expect(response).to have_http_status(:unauthorized)
    expect(json.dig(:error, :code)).to eq("invalid_credentials")
  end

  it "requires a token for protected endpoints" do
    get "/api/v1/me"
    expect(response).to have_http_status(:unauthorized)
  end

  it "rejects an expired token" do
    token = Auth::JsonWebToken.encode(user)
    travel 25.hours do
      get "/api/v1/me", headers: { "Authorization" => "Bearer #{token}" }
      expect(response).to have_http_status(:unauthorized)
    end
  end

  it "returns the current user" do
    get "/api/v1/me", headers: auth_headers(user)
    expect(json.dig(:user, :name)).to eq("Mercy Atieno")
  end
end
