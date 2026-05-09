module Analytics
  class Report
    # Below this many tests in a day, positivity is noise (e.g. the first sale of the morning).
    MIN_TESTS_FOR_POSITIVITY = 5

    def initialize(from: 30.days.ago.beginning_of_day, to: Time.current, region_id: nil)
      @from = from
      @to = to
      @region_id = region_id
    end

    def overview
      claims = scoped_claims
      today = Time.current.all_day
      {
        pending_review: Claim.awaiting_review.count,
        flagged_pending: Claim.awaiting_review.flagged.count,
        approved_today: Claim.where(status: %w[approved paid], reviewed_at: today).count,
        rejected_today: Claim.rejected.where(reviewed_at: today).count,
        awaiting_payout_cents: Claim.approved.sum(:amount_cents),
        paid_cents: claims.paid.sum(:amount_cents),
        claims_in_period: claims.count,
        claims_value_cents: claims.sum(:amount_cents),
        flag_rate: claims.count.zero? ? 0.0 : (claims.flagged.count.to_f / claims.count).round(4),
        approval_rate: approval_rate(claims),
        active_pharmacies: Pharmacy.active.count,
        high_risk_pharmacies: Pharmacy.high_risk.count,
        stockouts: InventoryItem.stocked_out.joins(:product).merge(Product.subsidised).count,
        median_review_hours: median_review_hours(claims)
      }
    end

    def claims_by_region
      scoped_claims.joins(pharmacy: :region)
        .group("regions.id", "regions.name")
        .order("regions.name")
        .pluck("regions.id", "regions.name", Arel.sql("COUNT(*)"), Arel.sql("SUM(claims.amount_cents)"),
          Arel.sql("COUNT(*) FILTER (WHERE claims.flags_count > 0)"))
        .map { |id, name, count, value, flagged| { region_id: id, region: name, claims: count, value_cents: value.to_i, flagged: } }
    end

    def claims_by_product
      scoped_claims.joins(:product)
        .group("products.id", "products.name", "products.category")
        .order(Arel.sql("COUNT(*) DESC"))
        .pluck("products.id", "products.name", "products.category", Arel.sql("COUNT(*)"), Arel.sql("SUM(claims.quantity)"), Arel.sql("SUM(claims.amount_cents)"))
        .map { |id, name, cat, count, units, value| { product_id: id, product: name, category: cat, claims: count, units:, value_cents: value.to_i } }
    end

    def trend
      rows = scoped_claims
        .group(Arel.sql("DATE((claims.submitted_at AT TIME ZONE 'UTC') AT TIME ZONE 'Africa/Nairobi')"))
        .pluck(Arel.sql("DATE((claims.submitted_at AT TIME ZONE 'UTC') AT TIME ZONE 'Africa/Nairobi')"),
          Arel.sql("COUNT(*)"),
          Arel.sql("COUNT(*) FILTER (WHERE claims.status IN ('approved','paid'))"),
          Arel.sql("COUNT(*) FILTER (WHERE claims.test_result = 'positive')"),
          Arel.sql("COUNT(*) FILTER (WHERE claims.test_result IS NOT NULL)"))
        .index_by(&:first)

      (@from.to_date..@to.to_date).map do |day|
        _, total, approved, positive, tested = rows[day] || [ day, 0, 0, 0, 0 ]
        { date: day.iso8601, claims: total, approved:, positivity: tested < MIN_TESTS_FOR_POSITIVITY ? nil : (positive.to_f / tested).round(3) }
      end
    end

    def stockouts
      InventoryItem.low_stock.joins(:product, pharmacy: :region)
        .merge(Product.subsidised)
        .order(:quantity_on_hand, "pharmacies.name")
        .pluck("pharmacies.id", "pharmacies.name", "regions.name", "products.name", "products.category",
          :quantity_on_hand, :reorder_level, "inventory_items.updated_at")
        .map do |pid, pname, region, product, category, qty, reorder, updated|
          { pharmacy_id: pid, pharmacy: pname, region:, product:, category:, quantity_on_hand: qty,
            reorder_level: reorder, stocked_out: qty.zero?, updated_at: updated }
        end
    end

    private

    def scoped_claims
      scope = Claim.where(submitted_at: @from..@to)
      scope = scope.joins(:pharmacy).where(pharmacies: { region_id: @region_id }) if @region_id.present?
      scope
    end

    def approval_rate(claims)
      decided = claims.where(status: %w[approved paid rejected]).count
      return 0.0 if decided.zero?

      (claims.where(status: %w[approved paid]).count.to_f / decided).round(4)
    end

    def median_review_hours(claims)
      value = claims.where.not(reviewed_at: nil)
        .pick(Arel.sql("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (reviewed_at - submitted_at)))"))
      value ? (value.to_f / 3600).round(1) : nil
    end
  end
end
