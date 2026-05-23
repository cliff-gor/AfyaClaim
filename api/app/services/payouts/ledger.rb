module Payouts
  module Ledger
    class Unbalanced < StandardError; end

    def self.post!(group:, lines:, reimbursement: nil, memo: nil)
      debits = lines.select { |_, dir, _| dir == "debit" }.sum(&:last)
      credits = lines.select { |_, dir, _| dir == "credit" }.sum(&:last)
      raise Unbalanced, "#{group}: debits #{debits} != credits #{credits}" unless debits == credits

      LedgerEntry.transaction do
        lines.each do |account, direction, amount|
          LedgerEntry.create!(account:, direction:, amount_cents: amount, entry_group: group, reimbursement:, memo:)
        end
      end
    end
  end
end
