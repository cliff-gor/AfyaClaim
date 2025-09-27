module Auth
  class JsonWebToken
    ALGORITHM = "HS256".freeze

    class << self
      def encode(user)
        now = Time.current.to_i
        payload = { sub: user.id, role: user.role, iat: now, exp: now + config.jwt_ttl.to_i }
        JWT.encode(payload, config.jwt_secret, ALGORITHM)
      end

      def decode(token)
        JWT.decode(token, config.jwt_secret, true, algorithm: ALGORITHM).first
      rescue JWT::DecodeError
        nil
      end

      private

      def config
        Rails.configuration.x.afyaclaim
      end
    end
  end
end
