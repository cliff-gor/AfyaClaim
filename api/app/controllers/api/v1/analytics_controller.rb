module Api
  module V1
    class AnalyticsController < ApplicationController
      before_action :require_staff!

      def overview = render(json: { data: report.overview })
      def claims_by_region = render(json: { data: report.claims_by_region })
      def claims_by_product = render(json: { data: report.claims_by_product })
      def trend = render(json: { data: report.trend })
      def stockouts = render(json: { data: report.stockouts })

      private

      def report
        days = params.fetch(:days, 30).to_i.clamp(1, 365)
        Analytics::Report.new(from: (days - 1).days.ago.beginning_of_day, region_id: params[:region_id])
      end
    end
  end
end
