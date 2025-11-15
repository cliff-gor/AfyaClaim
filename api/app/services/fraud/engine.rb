module Fraud
  class Engine
    RULES = [
      Rules::Velocity,
      Rules::NoMatchingSale,
      Rules::DuplicatePatient,
      Rules::AmountMismatch,
      Rules::StockInconsistency,
      Rules::OffHours,
      Rules::PositivityOutlier
    ].freeze

    def initialize(claim)
      @claim = claim
    end

    def run_inline!
      run!(RULES.select(&:inline))
    end

    def run_all!
      run!(RULES)
    end

    private

    def run!(rules)
      findings = rules.filter_map { |rule| rule.new(@claim).call }
      findings.each do |f|
        flag = @claim.flags.find_or_initialize_by(rule: f.rule)
        next if flag.persisted?

        flag.update!(severity: f.severity, message: f.message)
      end
      RiskScore.recompute!(@claim.pharmacy) if findings.any?
      findings
    end
  end
end
