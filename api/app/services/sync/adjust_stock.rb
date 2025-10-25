module Sync
  class AdjustStock
    Result = Struct.new(:adjustment, :status, :message, keyword_init: true)

    def self.call(...) = new(...).call

    def initialize(pharmacy:, attrs:)
      @pharmacy = pharmacy
      @attrs = attrs.to_h.symbolize_keys
    end

    def call
      if (existing = StockAdjustment.find_by(client_uuid: @attrs[:client_uuid]))
        return Result.new(adjustment: existing, status: :duplicate)
      end

      item = @pharmacy.inventory_items.find_or_create_by!(product_id: @attrs[:product_id])
      delta = @attrs[:delta].to_i
      StockAdjustment.transaction do
        unless item.apply_delta!(delta)
          return Result.new(status: :conflict,
            message: "Adjustment of #{delta} would make stock negative (server has #{item.quantity_on_hand})")
        end

        adj = item.stock_adjustments.create!(client_uuid: @attrs[:client_uuid], delta:, reason: @attrs[:reason].presence || "count_correction")
        Result.new(adjustment: adj, status: :accepted)
      end
    rescue ActiveRecord::RecordInvalid => e
      Result.new(status: :rejected, message: e.record.errors.full_messages.to_sentence)
    end
  end
end
