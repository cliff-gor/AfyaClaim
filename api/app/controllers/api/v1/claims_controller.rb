module Api
  module V1
    class ClaimsController < ApplicationController
      before_action :require_staff!, except: %i[show create]
      before_action :load_claim, except: %i[index create bulk_approve]

      def index
        scope = Claim.includes(:product, :pharmacy, :flags)
        scope = scope.where(status: params[:status].split(",")) if params[:status].present?
        scope = scope.flagged if params[:flagged] == "true"
        scope = scope.unflagged if params[:flagged] == "false"
        scope = scope.where(pharmacy_id: params[:pharmacy_id]) if params[:pharmacy_id].present?
        scope = scope.where(product_id: params[:product_id]) if params[:product_id].present?
        scope = scope.joins(:pharmacy).where(pharmacies: { region_id: params[:region_id] }) if params[:region_id].present?
        if params[:q].present?
          q = "%#{Claim.sanitize_sql_like(params[:q])}%"
          scope = scope.left_joins(:pharmacy).where("claims.reference ILIKE :q OR pharmacies.name ILIKE :q", q:)
        end
        scope = params[:sort] == "risk" ? scope.order(flags_count: :desc, submitted_at: :desc) : scope.newest_first
        paginate(scope, Serializers.method(:claim_row))
      end

      def show
        unless current_user.staff? || @claim.pharmacy_id == current_user.pharmacy_id
          raise Forbidden, "You can only view your pharmacy's claims"
        end
        render json: { data: Serializers.claim_detail(@claim) }
      end

      def create
        require_pharmacist!
        result = Claims::Submit.call(pharmacy: current_pharmacy, attrs: claim_params, actor: current_user)
        if result.ok?
          render json: { data: Serializers.claim_detail(result.claim), status: result.status },
            status: result.status == :duplicate ? :ok : :created
        else
          render_error(:validation_failed, result.errors.to_sentence, :unprocessable_entity)
        end
      end

      def start_review
        machine.start_review!(note: params[:note])
        render_detail
      end

      def approve
        machine.approve!(note: params[:note], override: ActiveModel::Type::Boolean.new.cast(params[:override]))
        render_detail
      end

      def reject
        return render_error(:validation_failed, "A rejection reason is required", :unprocessable_entity) if params[:reason].blank?

        machine.reject!(reason: params[:reason])
        render_detail
      end

      def bulk_approve
        ids = Array(params.require(:ids)).first(100)
        results = Claim.where(id: ids).includes(:flags).map do |claim|
          if claim.flags_count.positive?
            { id: claim.id, status: "skipped", message: "Flagged — needs individual review" }
          else
            Claims::StateMachine.new(claim, actor: current_user).approve!(note: "Bulk approved")
            { id: claim.id, status: "approved" }
          end
        rescue Claims::StateMachine::InvalidTransition, Claims::StateMachine::BlockedByFlags => e
          { id: claim.id, status: "skipped", message: e.message }
        end
        render json: { results: }
      end

      private

      def load_claim
        @claim = Claim.includes(:product, :pharmacy, :flags, :reviewed_by, :reimbursement, sale: { sale_items: :product }).find(params[:id])
      end

      def machine
        Claims::StateMachine.new(@claim, actor: current_user)
      end

      def render_detail
        render json: { data: Serializers.claim_detail(@claim.reload) }
      end

      def claim_params
        params.require(:claim).permit(:client_uuid, :sale_client_uuid, :sale_id, :product_id, :quantity, :amount_cents, :test_result, :submitted_at).to_h
      end
    end
  end
end
