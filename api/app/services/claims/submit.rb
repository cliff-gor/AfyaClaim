module Claims
  # Creates a claim (idempotently, keyed on client_uuid), records the submission
  # event and runs the inline fraud rules.
  class Submit
    Result = Struct.new(:claim, :status, :errors, keyword_init: true) do
      def ok? = %i[accepted duplicate].include?(status)
    end

    def self.call(...) = new(...).call

    def initialize(pharmacy:, attrs:, actor: nil)
      @pharmacy = pharmacy
      @attrs = attrs.to_h.symbolize_keys
      @actor = actor
    end

    def call
      if (existing = Claim.find_by(client_uuid: @attrs[:client_uuid]))
        return Result.new(claim: existing, status: existing.pharmacy_id == @pharmacy.id ? :duplicate : :rejected,
          errors: existing.pharmacy_id == @pharmacy.id ? [] : [ "client_uuid already used" ])
      end

      claim = build_claim
      Claim.transaction do
        claim.save!
        StateMachine.record_submission!(claim, actor: @actor)
      end
      Fraud::Engine.new(claim).run_inline!
      Result.new(claim: claim.reload, status: :accepted, errors: [])
    rescue ActiveRecord::RecordInvalid => e
      Result.new(claim: e.record, status: :rejected, errors: e.record.errors.full_messages)
    rescue ActiveRecord::RecordNotUnique
      retry
    end

    private

    def build_claim
      product = Product.find_by(id: @attrs[:product_id])
      sale = resolve_sale
      quantity = @attrs[:quantity].presence || 1

      @pharmacy.claims.new(
        client_uuid: @attrs[:client_uuid],
        product:,
        sale:,
        patient: sale&.patient,
        quantity:,
        amount_cents: @attrs[:amount_cents].presence || (product&.subsidy_cents.to_i * quantity.to_i),
        test_result: @attrs[:test_result].presence,
        submitted_at: @attrs[:submitted_at].presence || Time.current,
        status: "submitted"
      )
    end

    def resolve_sale
      if @attrs[:sale_client_uuid].present?
        @pharmacy.sales.find_by(client_uuid: @attrs[:sale_client_uuid])
      elsif @attrs[:sale_id].present?
        @pharmacy.sales.find_by(id: @attrs[:sale_id])
      end
    end
  end
end
