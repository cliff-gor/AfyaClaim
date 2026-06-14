module Api
  module V1
    # Daraja posts B2C results here. Requests are authenticated with a shared secret
    # appended to the callback URL we register, since Daraja does not sign payloads.
    class MpesaCallbacksController < ApplicationController
      skip_before_action :authenticate!
      before_action :verify_secret!

      def result
        outcome = Payouts::HandleResult.call(params.to_unsafe_h.slice("Result"))
        Rails.logger.info({ event: "mpesa_b2c_result", outcome: }.to_json)
        render json: { ResultCode: 0, ResultDesc: "Accepted" }
      end

      def timeout
        Rails.logger.warn({ event: "mpesa_b2c_timeout", payload: params.to_unsafe_h.slice("Result") }.to_json)
        render json: { ResultCode: 0, ResultDesc: "Accepted" }
      end

      private

      def verify_secret!
        expected = Rails.configuration.x.afyaclaim.mpesa[:callback_secret]
        return if ActiveSupport::SecurityUtils.secure_compare(params[:secret].to_s, expected.to_s)

        render_error(:unauthorized, "Invalid callback secret", :unauthorized)
      end
    end
  end
end
