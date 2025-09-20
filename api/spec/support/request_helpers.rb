module RequestHelpers
  def auth_headers(user)
    { "Authorization" => "Bearer #{Auth::JsonWebToken.encode(user)}", "Content-Type" => "application/json" }
  end

  def json
    JSON.parse(response.body, symbolize_names: true)
  end
end

RSpec.configure { |c| c.include RequestHelpers, type: :request }
