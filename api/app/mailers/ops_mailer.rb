class OpsMailer < ApplicationMailer
  def daily_report(date:, summary:, by_region:)
    @date = date
    @summary = summary
    @by_region = by_region
    mail(to: ENV.fetch("OPS_REPORT_EMAIL", "ops@afyaclaim.dev"), subject: "AfyaClaim daily report — #{date.to_fs(:long)}")
  end
end
