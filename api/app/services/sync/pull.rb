module Sync
  class Pull
    def self.call(...) = new(...).call

    def initialize(pharmacy:, since: nil)
      @pharmacy = pharmacy
      @since = parse(since)
    end

    def call
      now = Time.current
      {
        products: scope(Product.all).order(:name),
        inventory: scope(@pharmacy.inventory_items).order(:id),
        claims: scope(@pharmacy.claims).order(:updated_at),
        cursor: now.utc.iso8601(6)
      }
    end

    private

    def scope(relation)
      @since ? relation.where("#{relation.table_name}.updated_at > ?", @since) : relation
    end

    def parse(value)
      Time.iso8601(value) if value.present?
    rescue ArgumentError
      nil
    end
  end
end
