module Api
  module V1
    class ReimbursementsController < ApplicationController
      before_action :require_staff!, only: :index
      before_action :require_admin!, only: :create

      def index
        scope = Reimbursement.includes(:pharmacy, :claims).order(created_at: :desc)
        scope = scope.where(status: params[:status]) if params[:status].present?
        paginate(scope, Serializers.method(:reimbursement))
      end

      def create
        reimbursements = Payouts::CreateBatch.call(pharmacy_ids: params[:pharmacy_ids])
        render json: { data: reimbursements.map { |r| Serializers.reimbursement(r.reload) } }, status: :created
      end
    end
  end
end
