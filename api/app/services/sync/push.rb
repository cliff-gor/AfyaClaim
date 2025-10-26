module Sync
  # Applies a batch of offline records from a device. Sales are applied before claims
  # so that a claim can reference a sale recorded in the same batch.
  class Push
    MAX_BATCH = 200

    def self.call(...) = new(...).call

    def initialize(pharmacy:, payload:, actor:)
      @pharmacy = pharmacy
      @payload = payload.to_h.deep_symbolize_keys
      @actor = actor
    end

    def call
      records = Array(@payload[:sales]) + Array(@payload[:claims]) + Array(@payload[:stock_adjustments])
      raise ArgumentError, "batch too large (max #{MAX_BATCH})" if records.size > MAX_BATCH

      results = []
      Array(@payload[:sales]).each do |attrs|
        r = RecordSale.call(pharmacy: @pharmacy, attrs:)
        results << row("sale", attrs[:client_uuid], r.status, r.sale&.id, r.message)
      end
      Array(@payload[:stock_adjustments]).each do |attrs|
        r = AdjustStock.call(pharmacy: @pharmacy, attrs:)
        results << row("stock_adjustment", attrs[:client_uuid], r.status, r.adjustment&.id, r.message)
      end
      Array(@payload[:claims]).each do |attrs|
        r = Claims::Submit.call(pharmacy: @pharmacy, attrs:, actor: @actor)
        results << row("claim", attrs[:client_uuid], r.status, r.claim&.id, r.errors.presence&.to_sentence)
      end
      results
    end

    private

    def row(type, client_uuid, status, server_id, message)
      { type:, client_uuid:, status: status.to_s, server_id: (server_id if %i[accepted duplicate conflict].include?(status)), message: }
    end
  end
end
