module Mpesa
  # Thin Daraja client. Only the B2C and transaction-status calls are used.
  class Client
    class Error < StandardError; end

    def initialize(config: Rails.configuration.x.afyaclaim.mpesa, http: nil)
      @config = config
      @http = http || Faraday.new(url: config[:base_url]) do |f|
        f.request :json
        f.response :json
        f.options.timeout = 15
        f.options.open_timeout = 5
      end
    end

    # originator_conversation_id is our idempotency key: Daraja rejects a repeat,
    # which is what stops a retried job from paying twice.
    def b2c_payment(phone:, amount_cents:, originator_conversation_id:, remarks:)
      post("/mpesa/b2c/v3/paymentrequest", {
        OriginatorConversationID: originator_conversation_id,
        InitiatorName: @config[:initiator_name],
        SecurityCredential: @config[:security_credential],
        CommandID: "BusinessPayment",
        Amount: (amount_cents / 100.0).ceil,
        PartyA: @config[:shortcode],
        PartyB: phone,
        Remarks: remarks.truncate(100),
        QueueTimeOutURL: @config[:timeout_url],
        ResultURL: @config[:result_url],
        Occasion: "AfyaClaim reimbursement"
      })
    end

    def transaction_status(originator_conversation_id:)
      post("/mpesa/transactionstatus/v1/query", {
        Initiator: @config[:initiator_name],
        SecurityCredential: @config[:security_credential],
        CommandID: "TransactionStatusQuery",
        OriginatorConversationID: originator_conversation_id,
        PartyA: @config[:shortcode],
        IdentifierType: "4",
        ResultURL: @config[:result_url],
        QueueTimeOutURL: @config[:timeout_url],
        Remarks: "Reconciliation"
      })
    end

    private

    def post(path, body)
      res = @http.post(path, body, { "Authorization" => "Bearer #{access_token}" })
      raise Error, "Daraja #{res.status}: #{res.body}" unless res.success?

      res.body
    rescue Faraday::Error => e
      raise Error, e.message
    end

    def access_token
      Rails.cache.fetch("mpesa/access_token", expires_in: 50.minutes) do
        conn = Faraday.new(url: @config[:base_url]) { |f| f.response :json }
        conn.set_basic_auth(@config[:consumer_key], @config[:consumer_secret])
        res = conn.get("/oauth/v1/generate", grant_type: "client_credentials")
        raise Error, "Daraja auth failed (#{res.status})" unless res.success?

        res.body.fetch("access_token")
      end
    end
  end
end
