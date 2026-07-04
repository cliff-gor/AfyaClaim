class StockoutAlertJob < ApplicationJob
  queue_as :default

  def perform
    stockouts = Analytics::Report.new.stockouts.select { |s| s[:stocked_out] }
    stockouts.each do |s|
      Rails.logger.warn({ event: "stockout", pharmacy_id: s[:pharmacy_id], product: s[:product] }.to_json)
    end
    stockouts.size
  end
end
