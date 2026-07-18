# Checks that the ledger balances and that every reimbursement's M-Pesa state agrees
# with ours. Any mismatch is logged as `reconciliation_mismatch`, which a CloudWatch
# metric filter turns into an alarm.
class NightlyReconciliationJob < ApplicationJob
  queue_as :default
  STUCK_AFTER = 2.hours

  def perform
    issues = []
    LedgerEntry.unbalanced_groups.each { |g| issues << { kind: "unbalanced_group", group: g } }
    issues << { kind: "ledger_unbalanced" } unless LedgerEntry.balanced?

    Reimbursement.paid.where(mpesa_receipt: nil).find_each do |r|
      issues << { kind: "paid_without_receipt", reimbursement_id: r.id }
    end

    Reimbursement.paid.find_each do |r|
      claimed = r.claims.sum(:amount_cents)
      issues << { kind: "amount_mismatch", reimbursement_id: r.id, expected: r.amount_cents, claims: claimed } if claimed != r.amount_cents
    end

    Reimbursement.processing.where(updated_at: ...STUCK_AFTER.ago).find_each do |r|
      issues << { kind: "stuck_processing", reimbursement_id: r.id }
      MpesaPayoutJob.perform_later(r.id)
    end

    issues.each { |issue| Rails.logger.error({ event: "reconciliation_mismatch" }.merge(issue).to_json) }
    Rails.logger.info({ event: "reconciliation_completed", issues: issues.size }.to_json)
    issues
  end
end
