class ApplicationController < ActionController::API
  include Pagy::Backend

  class Forbidden < StandardError; end

  before_action :authenticate!

  rescue_from ActiveRecord::RecordNotFound, with: ->(e) { render_error(:not_found, "#{e.model || 'Record'} not found", :not_found) }
  rescue_from ActiveRecord::RecordInvalid, with: ->(e) { render_error(:validation_failed, e.record.errors.full_messages.to_sentence, :unprocessable_entity, details: e.record.errors.to_hash) }
  rescue_from ActionController::ParameterMissing, with: ->(e) { render_error(:bad_request, e.message, :bad_request) }
  rescue_from Forbidden, with: ->(e) { render_error(:forbidden, e.message.presence || "Not allowed", :forbidden) }
  rescue_from Claims::StateMachine::InvalidTransition, with: ->(e) { render_error(:invalid_transition, e.message, :unprocessable_entity) }
  rescue_from Claims::StateMachine::BlockedByFlags, with: ->(e) { render_error(:blocked_by_flags, e.message, :unprocessable_entity) }
  rescue_from ActiveRecord::StaleObjectError, with: ->(_) { render_error(:stale_object, "Record changed on the server, reload and retry", :conflict) }

  attr_reader :current_user

  private

  def authenticate!
    token = request.authorization.to_s.delete_prefix("Bearer ").presence
    payload = token && Auth::JsonWebToken.decode(token)
    @current_user = payload && User.find_by(id: payload["sub"])
    render_error(:unauthorized, "Missing or invalid token", :unauthorized) unless @current_user
  end

  def require_staff!
    raise Forbidden, "Reviewer access required" unless current_user.staff?
  end

  def require_admin!
    raise Forbidden, "Admin access required" unless current_user.admin?
  end

  def require_pharmacist!
    raise Forbidden, "Pharmacist access required" unless current_user.pharmacist?
  end

  def current_pharmacy
    current_user.pharmacy
  end

  def render_error(code, message, status, details: nil)
    render json: { error: { code:, message:, details: }.compact }, status:
  end

  def paginate(scope, serializer)
    pagy, records = pagy(scope, limit: params.fetch(:per_page, 25).to_i.clamp(1, 100))
    render json: { data: records.map { |r| serializer.call(r) }, meta: { page: pagy.page, per_page: pagy.limit, total: pagy.count, pages: pagy.pages } }
  end

  def append_info_to_payload(payload)
    super
    payload[:user_id] = current_user&.id
    payload[:request_id] = request.request_id
  end
end
