class DailyReportJob < ApplicationJob
  queue_as :default

  def perform(date: Date.yesterday)
    report = Analytics::Report.new(from: date.beginning_of_day, to: date.end_of_day)
    summary = report.overview.slice(:claims_in_period, :claims_value_cents, :approved_today,
      :rejected_today, :flag_rate, :awaiting_payout_cents, :stockouts)
    OpsMailer.daily_report(date:, summary:, by_region: report.claims_by_region).deliver_later
    summary
  end
end
