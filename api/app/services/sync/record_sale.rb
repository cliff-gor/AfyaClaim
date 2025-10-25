module Sync
  # Records a sale coming from a device. Idempotent on client_uuid and decrements
  # stock with relative deltas so concurrent devices merge cleanly.
  class RecordSale
    Result = Struct.new(:sale, :status, :message, keyword_init: true)

    def self.call(...) = new(...).call

    def initialize(pharmacy:, attrs:)
      @pharmacy = pharmacy
      @attrs = attrs.to_h.deep_symbolize_keys
    end

    def call
      if (existing = Sale.find_by(client_uuid: @attrs[:client_uuid]))
        return Result.new(sale: existing, status: existing.pharmacy_id == @pharmacy.id ? :duplicate : :rejected)
      end

      Sale.transaction do
        sale = @pharmacy.sales.new(
          client_uuid: @attrs[:client_uuid],
          sold_at: @attrs[:sold_at].presence || Time.current,
          patient: resolve_patient,
          total_cents: @attrs[:total_cents].to_i
        )
        Array(@attrs[:items]).each do |item|
          sale.sale_items.build(product_id: item[:product_id], quantity: item[:quantity],
            unit_price_cents: item[:unit_price_cents])
        end
        sale.save!

        shortfalls = decrement_stock(sale)
        status = shortfalls.empty? ? :accepted : :conflict
        Result.new(sale:, status:, message: shortfalls.presence&.join("; "))
      end
    rescue ActiveRecord::RecordInvalid => e
      Result.new(sale: e.record, status: :rejected, message: e.record.errors.full_messages.to_sentence)
    end

    private

    def resolve_patient
      p = @attrs[:patient]
      return nil if p.blank? || p[:pseudonym].blank?

      Patient.create_or_find_by!(pseudonym: p[:pseudonym]) do |patient|
        patient.age_band = p[:age_band]
        patient.sex = p[:sex]
      end
    end

    # The sale is kept even if stock is short: the dispensing already happened
    # in the real world. The shortfall is reported back as a conflict so the
    # pharmacist can reconcile their count.
    def decrement_stock(sale)
      sale.sale_items.filter_map do |item|
        inv = @pharmacy.inventory_items.find_or_create_by!(product_id: item.product_id)
        next if inv.apply_delta!(-item.quantity)

        inv.update_column(:quantity_on_hand, 0)
        "#{item.product.name}: sold #{item.quantity}, server stock was lower"
      end
    end
  end
end
