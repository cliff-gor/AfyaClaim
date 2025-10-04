module Api
  module V1
    class SalesController < ApplicationController
      before_action :require_pharmacist!

      def index
        paginate(current_pharmacy.sales.includes(:patient, sale_items: :product).recent, Serializers.method(:sale))
      end

      def create
        result = Sync::RecordSale.call(pharmacy: current_pharmacy, attrs: sale_params)
        if result.status == :rejected
          render_error(:validation_failed, result.message || "Sale rejected", :unprocessable_entity)
        else
          render json: { data: Serializers.sale(result.sale), status: result.status, message: result.message },
            status: result.status == :duplicate ? :ok : :created
        end
      end

      private

      def sale_params
        params.require(:sale).permit(:client_uuid, :sold_at, :total_cents,
          patient: %i[pseudonym age_band sex], items: %i[product_id quantity unit_price_cents]).to_h
      end
    end
  end
end
