Rails.application.config.x.afyaclaim = ActiveSupport::OrderedOptions.new.tap do |c|
  c.jwt_secret = ENV.fetch("JWT_SECRET") { Rails.application.secret_key_base }
  c.jwt_ttl = 24.hours
  c.velocity_limit = ENV.fetch("FRAUD_VELOCITY_LIMIT", 20).to_i
  c.velocity_window = 60.minutes
  c.mpesa = {
    base_url: ENV.fetch("MPESA_BASE_URL", "https://sandbox.safaricom.co.ke"),
    consumer_key: ENV["MPESA_CONSUMER_KEY"],
    consumer_secret: ENV["MPESA_CONSUMER_SECRET"],
    initiator_name: ENV.fetch("MPESA_INITIATOR_NAME", "testapi"),
    security_credential: ENV["MPESA_SECURITY_CREDENTIAL"],
    shortcode: ENV.fetch("MPESA_SHORTCODE", "600000"),
    result_url: ENV.fetch("MPESA_RESULT_URL", "https://api.afyaclaim.dev/api/v1/mpesa/b2c/result"),
    timeout_url: ENV.fetch("MPESA_TIMEOUT_URL", "https://api.afyaclaim.dev/api/v1/mpesa/b2c/timeout"),
    callback_secret: ENV.fetch("MPESA_CALLBACK_SECRET", "dev-callback-secret")
  }
end
