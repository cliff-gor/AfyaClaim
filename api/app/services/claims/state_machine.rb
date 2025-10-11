module Claims
  # Owns every status change a claim can go through. Each transition is wrapped in a
  # transaction together with its audit event, so the trail can never drift from state.
  class StateMachine
    class InvalidTransition < StandardError; end
    class BlockedByFlags < StandardError; end

    TRANSITIONS = {
      start_review: { from: %w[submitted], to: "under_review" },
      approve: { from: %w[submitted under_review], to: "approved" },
      reject: { from: %w[submitted under_review], to: "rejected" },
      pay: { from: %w[approved], to: "paid" }
    }.freeze

    def self.record_submission!(claim, actor: nil)
      claim.events.create!(from_status: nil, to_status: "submitted", actor:,
        metadata: { source: actor ? "api" : "sync" })
    end

    def initialize(claim, actor:)
      @claim = claim
      @actor = actor
    end

    def can?(event)
      TRANSITIONS.fetch(event)[:from].include?(@claim.status)
    end

    def start_review!(note: nil)
      transition!(:start_review, note:)
    end

    def approve!(note: nil, override: false)
      if @claim.blocking_flags? && !override
        raise BlockedByFlags, "Claim #{@claim.reference} has unresolved high-severity flags"
      end

      metadata = override ? { override: true, overridden_flags: @claim.flags.unresolved.pluck(:rule) } : {}
      transition!(:approve, note:, metadata:) do
        @claim.flags.unresolved.update_all(resolved: true, updated_at: Time.current) if override
        @claim.reviewed_by = @actor
        @claim.reviewed_at = Time.current
      end
    end

    def reject!(reason:)
      raise ArgumentError, "reason is required" if reason.blank?

      transition!(:reject, note: reason) do
        @claim.rejection_reason = reason
        @claim.reviewed_by = @actor
        @claim.reviewed_at = Time.current
      end
    end

    def pay!(reimbursement:)
      transition!(:pay, metadata: { reimbursement_id: reimbursement.id, mpesa_receipt: reimbursement.mpesa_receipt })
    end

    private

    def transition!(event, note: nil, metadata: {})
      rule = TRANSITIONS.fetch(event)

      Claim.transaction do
        @claim.lock!
        unless rule[:from].include?(@claim.status)
          raise InvalidTransition, "Cannot #{event.to_s.humanize.downcase} a claim that is #{@claim.status.humanize.downcase}"
        end

        from = @claim.status
        yield if block_given?
        @claim.status = rule[:to]
        @claim.save!
        @claim.events.create!(from_status: from, to_status: rule[:to], actor: @actor, note:, metadata:)
        @claim.refresh_flags_count!
      end
      @claim
    end
  end
end
