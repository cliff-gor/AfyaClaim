module Fraud
  class Rule
    Finding = Struct.new(:rule, :severity, :message, keyword_init: true)

    class_attribute :code, :severity, :inline, default: true

    def initialize(claim)
      @claim = claim
    end

    # Returns a Finding when the rule fires, nil otherwise.
    def call
      raise NotImplementedError
    end

    private

    attr_reader :claim

    def finding(message)
      Finding.new(rule: code, severity:, message:)
    end

    def config
      Rails.configuration.x.afyaclaim
    end
  end
end
