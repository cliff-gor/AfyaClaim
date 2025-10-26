module Api
  module V1
    class SyncController < ApplicationController
      before_action :require_pharmacist!

      def push
        results = Sync::Push.call(pharmacy: current_pharmacy, payload: push_params, actor: current_user)
        render json: { results: }
      rescue ArgumentError => e
        render_error(:bad_request, e.message, :bad_request)
      end

      def pull
        delta = Sync::Pull.call(pharmacy: current_pharmacy, since: params[:since])
        render json: {
          products: delta[:products].map { |p| Serializers.product(p) },
          inventory: delta[:inventory].map { |i| Serializers.inventory(i) },
          claims: delta[:claims].map { |c| Serializers.sync_claim(c) },
          cursor: delta[:cursor]
        }
      end

      private

      def push_params
        params.permit(
          sales: [ :client_uuid, :sold_at, :total_cents, { patient: %i[pseudonym age_band sex] }, { items: %i[product_id quantity unit_price_cents] } ],
          claims: %i[client_uuid sale_client_uuid product_id quantity amount_cents test_result submitted_at],
          stock_adjustments: %i[client_uuid product_id delta reason]
        ).to_h
      end
    end
  end
end
