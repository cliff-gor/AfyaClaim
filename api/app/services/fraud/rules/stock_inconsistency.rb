module Fraud
  module Rules
    # The pharmacy claims more units in a day than it could have had on the shelf
    # that morning (current stock + units sold today − units restocked today).
    class StockInconsistency < Rule
      self.code = "stock_inconsistency"
      self.severity = "medium"

      def call
        item = InventoryItem.find_by(pharmacy_id: claim.pharmacy_id, product_id: claim.product_id)
        return finding("Pharmacy does not stock #{claim.product.name}") if item.nil?

        day = claim.submitted_at.all_day
        claimed_today = Claim.where(pharmacy_id: claim.pharmacy_id, product_id: claim.product_id, submitted_at: day).sum(:quantity)
        sold_today = SaleItem.joins(:sale)
          .where(product_id: claim.product_id, sales: { pharmacy_id: claim.pharmacy_id, sold_at: day }).sum(:quantity)
        restocked_today = item.stock_adjustments.where(created_at: day).where("delta > 0").sum(:delta)
        opening = item.quantity_on_hand + sold_today - restocked_today
        return if claimed_today <= opening

        finding("#{claimed_today} units claimed today but opening stock was #{opening}")
      end
    end
  end
end
