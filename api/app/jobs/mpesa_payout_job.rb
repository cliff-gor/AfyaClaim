class MpesaPayoutJob < ApplicationJob
  queue_as :payouts
  limits_concurrency to: 1, key: ->(reimbursement_id) { "payout-#{reimbursement_id}" }
  retry_on Mpesa::Client::Error, wait: :polynomially_longer, attempts: Reimbursement::MAX_ATTEMPTS

  def perform(reimbursement_id)
    reimbursement = Reimbursement.find(reimbursement_id)
    return unless reimbursement.pending? || reimbursement.processing?
    # A previous attempt reached Daraja; ask for the outcome instead of paying again.
    return query_status(reimbursement) if reimbursement.mpesa_conversation_id.present?

    reimbursement.increment!(:attempts)
    reimbursement.update!(status: "processing")

    if simulated?
      simulate_success(reimbursement)
    else
      response = client.b2c_payment(
        phone: reimbursement.pharmacy.mpesa_phone,
        amount_cents: reimbursement.amount_cents,
        originator_conversation_id: reimbursement.idempotency_key,
        remarks: "AfyaClaim reimbursement ##{reimbursement.id}"
      )
      reimbursement.update!(mpesa_conversation_id: response["ConversationID"])
    end
  end

  private

  def query_status(reimbursement)
    client.transaction_status(originator_conversation_id: reimbursement.idempotency_key) unless simulated?
  end

  def client
    @client ||= Mpesa::Client.new
  end

  def simulated?
    Rails.configuration.x.afyaclaim.mpesa[:consumer_key].blank?
  end

  def simulate_success(reimbursement)
    conversation_id = "AG_SIM_#{reimbursement.idempotency_key.delete('-').first(16).upcase}"
    reimbursement.update!(mpesa_conversation_id: conversation_id)
    Payouts::HandleResult.call("Result" => {
      "ResultCode" => 0, "ResultDesc" => "Simulated sandbox payment",
      "ConversationID" => conversation_id,
      "OriginatorConversationID" => reimbursement.idempotency_key,
      "TransactionID" => "SIM#{SecureRandom.alphanumeric(7).upcase}"
    })
  end
end
