class Rack::Attack
  Rack::Attack.cache.store = ActiveSupport::Cache::MemoryStore.new

  throttle("logins/ip", limit: 10, period: 1.minute) do |req|
    req.ip if req.path == "/api/v1/auth/login" && req.post?
  end

  throttle("api/ip", limit: 600, period: 1.minute) do |req|
    req.ip if req.path.start_with?("/api/")
  end

  self.throttled_responder = lambda do |_req|
    [ 429, { "Content-Type" => "application/json" },
      [ { error: { code: "rate_limited", message: "Too many requests" } }.to_json ] ]
  end
end

Rails.application.config.middleware.use Rack::Attack unless Rails.env.test?
