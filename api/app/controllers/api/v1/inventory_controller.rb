module Api
  module V1
    class InventoryController < ApplicationController
      before_action :require_pharmacist!

      def index
        items = current_pharmacy.inventory_items.eager_load(:product).order("products.name")
        render json: { data: items.map { |i| Serializers.inventory(i) } }
      end

      # Absolute update guarded by optimistic locking; devices normally send
      # relative deltas through /sync instead.
      def update
        item = current_pharmacy.inventory_items.find(params[:id])
        item.update!(params.require(:inventory_item).permit(:quantity_on_hand, :reorder_level, :lock_version))
        render json: { data: Serializers.inventory(item) }
      end
    end
  end
end
