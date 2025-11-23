module Api
  module V1
    class PharmaciesController < ApplicationController
      before_action :require_staff!

      def index
        scope = Pharmacy.includes(:region)
        scope = scope.where(region_id: params[:region_id]) if params[:region_id].present?
        scope = scope.where("pharmacies.name ILIKE ?", "%#{Pharmacy.sanitize_sql_like(params[:q])}%") if params[:q].present?
        scope = scope.high_risk if params[:high_risk] == "true"
        scope = scope.order(risk_score: :desc, name: :asc)
        pagy, records = pagy(scope, limit: params.fetch(:per_page, 50).to_i.clamp(1, 100))
        stats = claim_stats(records.map(&:id))
        render json: {
          data: records.map { |p| Serializers.pharmacy(p, stats: stats[p.id] || empty_stats) },
          meta: { page: pagy.page, per_page: pagy.limit, total: pagy.count, pages: pagy.pages }
        }
      end

      def show
        pharmacy = Pharmacy.includes(:region).find(params[:id])
        stats = claim_stats([ pharmacy.id ])[pharmacy.id] || empty_stats
        recent_flags = ClaimFlag.unresolved.joins(:claim).where(claims: { pharmacy_id: pharmacy.id })
          .order(created_at: :desc).limit(10).includes(:claim)
        render json: {
          data: Serializers.pharmacy(pharmacy, stats:).merge(
            inventory: pharmacy.inventory_items.eager_load(:product).order("products.name").map { |i| Serializers.inventory(i) },
            recent_flags: recent_flags.map { |f| f.slice(:id, :rule, :severity, :message, :created_at).merge(claim_id: f.claim_id, claim_reference: f.claim.reference) },
            monthly: monthly(pharmacy)
          )
        }
      end

      private

      def claim_stats(ids)
        Claim.where(pharmacy_id: ids).group(:pharmacy_id).pluck(
          :pharmacy_id, Arel.sql("COUNT(*)"),
          Arel.sql("COUNT(*) FILTER (WHERE status IN ('submitted','under_review'))"),
          Arel.sql("COUNT(*) FILTER (WHERE flags_count > 0)"),
          Arel.sql("COUNT(*) FILTER (WHERE status = 'rejected')"),
          Arel.sql("COALESCE(SUM(amount_cents) FILTER (WHERE status IN ('approved','paid')), 0)")
        ).to_h { |id, total, pending, flagged, rejected, value| [ id, { total:, pending:, flagged:, rejected:, approved_value_cents: value.to_i } ] }
      end

      def empty_stats
        { total: 0, pending: 0, flagged: 0, rejected: 0, approved_value_cents: 0 }
      end

      def monthly(pharmacy)
        pharmacy.claims.where(submitted_at: 6.months.ago.beginning_of_month..)
          .group(Arel.sql("DATE_TRUNC('month', submitted_at)"))
          .order(Arel.sql("DATE_TRUNC('month', submitted_at)"))
          .pluck(Arel.sql("DATE_TRUNC('month', submitted_at)"), Arel.sql("COUNT(*)"), Arel.sql("COUNT(*) FILTER (WHERE flags_count > 0)"))
          .map { |m, n, f| { month: m.to_date.iso8601, claims: n, flagged: f } }
      end
    end
  end
end
