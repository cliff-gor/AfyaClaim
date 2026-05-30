module Payouts
  # Groups approved, unpaid claims per pharmacy into reimbursements and writes the
  # ledger entries *before* any money moves (debit programme float, credit the
  # pharmacy payable). Payment then happens asynchronously in MpesaPayoutJob.
  class CreateBatch
    def self.call(...) = new(...).call

    # approved_before lets ops run a weekly payout for everything approved up to a cutoff.
    def initialize(pharmacy_ids: nil, approved_before: nil)
      @pharmacy_ids = pharmacy_ids
      @approved_before = approved_before
    end

    def call
      scope = Claim.approved.where(reimbursement_id: nil)
      scope = scope.where(pharmacy_id: @pharmacy_ids) if @pharmacy_ids.present?
      scope = scope.where(reviewed_at: ..@approved_before) if @approved_before

      scope.group(:pharmacy_id).pluck(:pharmacy_id).filter_map do |pharmacy_id|
        create_for(Pharmacy.find(pharmacy_id), scope.where(pharmacy_id:))
      end
    end

    private

    def create_for(pharmacy, claims)
      Reimbursement.transaction do
        locked = claims.lock("FOR UPDATE SKIP LOCKED").to_a
        next nil if locked.empty?

        amount = locked.sum(&:amount_cents)
        reimbursement = pharmacy.reimbursements.create!(amount_cents: amount, status: "pending")
        Claim.where(id: locked.map(&:id)).update_all(reimbursement_id: reimbursement.id, updated_at: Time.current)
        Ledger.post!(
          group: "payout:#{reimbursement.id}",
          reimbursement:,
          memo: "Reimbursement for #{locked.size} claims",
          lines: [
            [ LedgerEntry::FLOAT_ACCOUNT, "debit", amount ],
            [ LedgerEntry.payable_account(pharmacy), "credit", amount ]
          ]
        )
        MpesaPayoutJob.perform_later(reimbursement.id)
        reimbursement
      end
    end
  end
end
