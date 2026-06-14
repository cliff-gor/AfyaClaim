module Payouts
  # Processes the Daraja B2C result callback. Safe to call more than once for the
  # same result: a reimbursement that is already paid/failed is left untouched.
  class HandleResult
    def self.call(...) = new(...).call

    def initialize(payload)
      @result = payload.to_h.deep_stringify_keys.fetch("Result", {})
    end

    def call
      reimbursement = Reimbursement.find_by(mpesa_conversation_id: @result["ConversationID"]) ||
        Reimbursement.find_by(idempotency_key: @result["OriginatorConversationID"])
      return :unknown unless reimbursement
      return :ignored if reimbursement.paid? || reimbursement.failed?

      @result["ResultCode"].to_i.zero? ? mark_paid(reimbursement) : mark_failed(reimbursement)
    end

    private

    def mark_paid(reimbursement)
      Reimbursement.transaction do
        reimbursement.update!(status: "paid", paid_at: Time.current, mpesa_receipt: @result["TransactionID"])
        Ledger.post!(group: "settle:#{reimbursement.id}", reimbursement:, memo: "M-Pesa #{@result['TransactionID']}",
          lines: [
            [ LedgerEntry.payable_account(reimbursement.pharmacy), "debit", reimbursement.amount_cents ],
            [ "mpesa_clearing", "credit", reimbursement.amount_cents ]
          ])
        reimbursement.claims.approved.find_each do |claim|
          Claims::StateMachine.new(claim, actor: nil).pay!(reimbursement:)
        end
      end
      :paid
    end

    # Reverse the original posting and release the claims so they can be paid in a later batch.
    def mark_failed(reimbursement)
      Reimbursement.transaction do
        reimbursement.update!(status: "failed", failure_reason: @result["ResultDesc"])
        Ledger.post!(group: "reverse:#{reimbursement.id}", reimbursement:, memo: "Reversal: #{@result['ResultDesc']}",
          lines: [
            [ LedgerEntry.payable_account(reimbursement.pharmacy), "debit", reimbursement.amount_cents ],
            [ LedgerEntry::FLOAT_ACCOUNT, "credit", reimbursement.amount_cents ]
          ])
        reimbursement.claims.update_all(reimbursement_id: nil, updated_at: Time.current)
      end
      Rails.logger.error({ event: "mpesa_payout_failed", reimbursement_id: reimbursement.id,
        result_code: @result["ResultCode"], result_desc: @result["ResultDesc"] }.to_json)
      :failed
    end
  end
end
