module Api
  module V1
    class AuthController < ApplicationController
      skip_before_action :authenticate!, only: :login

      def login
        user = User.find_by(email: params.require(:email).to_s.strip.downcase)
        if user&.authenticate(params.require(:password))
          user.update_column(:last_login_at, Time.current)
          render json: { token: Auth::JsonWebToken.encode(user), user: Serializers.user(user) }
        else
          render_error(:invalid_credentials, "Email or password is incorrect", :unauthorized)
        end
      end

      def me
        render json: { user: Serializers.user(current_user) }
      end
    end
  end
end
