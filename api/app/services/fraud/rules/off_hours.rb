module Fraud
  module Rules
    class OffHours < Rule
      self.code = "off_hours"
      self.severity = "low"

      def call
        at = (claim.sale&.sold_at || claim.submitted_at).in_time_zone("Africa/Nairobi")
        return unless at.hour >= 23 || at.hour < 5

        finding("Sale recorded at #{at.strftime('%H:%M')} EAT")
      end
    end
  end
end
