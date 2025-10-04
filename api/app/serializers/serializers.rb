# Plain-Ruby serializers keep the JSON contract explicit and easy to diff against the OpenAPI spec.
module Serializers
  module_function

  def user(u)
    { id: u.id, email: u.email, name: u.name, role: u.role,
      pharmacy: u.pharmacy && { id: u.pharmacy.id, name: u.pharmacy.name, town: u.pharmacy.town } }
  end

  def product(p)
    p.slice(:id, :sku, :name, :category, :unit_price_cents, :subsidy_cents, :subsidised)
  end

  def inventory(i)
    i.slice(:id, :product_id, :quantity_on_hand, :reorder_level, :lock_version, :updated_at)
      .merge(low_stock: i.low_stock?, product: i.association(:product).loaded? ? product(i.product) : nil).compact
  end

  def pharmacy(p, stats: nil)
    p.slice(:id, :name, :license_number, :town, :status, :risk_score, :mpesa_phone, :latitude, :longitude)
      .merge(region: { id: p.region.id, name: p.region.name }, high_risk: p.high_risk?, stats:).compact
  end

  def sale(s)
    s.slice(:id, :client_uuid, :sold_at, :total_cents).merge(
      patient: s.patient && s.patient.slice(:age_band, :sex).merge(pseudonym: s.patient.pseudonym.first(10)),
      items: s.sale_items.map { |i| i.slice(:product_id, :quantity, :unit_price_cents).merge(product_name: i.product.name) }
    )
  end

  def claim_row(c)
    c.slice(:id, :client_uuid, :reference, :status, :quantity, :amount_cents, :test_result,
      :submitted_at, :reviewed_at, :rejection_reason, :flags_count, :updated_at).merge(
      product: { id: c.product.id, name: c.product.name, category: c.product.category },
      pharmacy: { id: c.pharmacy.id, name: c.pharmacy.name, town: c.pharmacy.town, risk_score: c.pharmacy.risk_score },
      has_sale: c.sale_id.present?,
      top_flag: c.flags.reject(&:resolved).max_by(&:weight)&.slice(:rule, :severity, :message)
    )
  end

  def claim_detail(c)
    events = c.events.includes(:actor).to_a
    claim_row(c).merge(
      reviewed_by: c.reviewed_by && { id: c.reviewed_by.id, name: c.reviewed_by.name },
      sale: c.sale && sale(c.sale),
      patient: c.patient && c.patient.slice(:age_band, :sex),
      flags: c.flags.sort_by { |f| [ f.resolved ? 1 : 0, -f.weight ] }.map { |f| f.slice(:id, :rule, :severity, :message, :resolved, :created_at) },
      events: events.map { |e| e.slice(:id, :from_status, :to_status, :note, :metadata, :created_at, :digest).merge(actor: e.actor&.name || "Device sync") },
      audit_intact: ClaimEvent.chain_intact?(events),
      reimbursement: c.reimbursement&.slice(:id, :status, :mpesa_receipt, :paid_at)
    )
  end

  def sync_claim(c)
    c.slice(:id, :client_uuid, :reference, :product_id, :quantity, :amount_cents, :status, :rejection_reason, :updated_at)
  end

  def reimbursement(r)
    r.slice(:id, :amount_cents, :status, :mpesa_conversation_id, :mpesa_receipt, :attempts, :failure_reason, :paid_at, :created_at)
      .merge(pharmacy: { id: r.pharmacy.id, name: r.pharmacy.name, mpesa_phone: r.pharmacy.mpesa_phone },
        claims_count: r.claims.size)
  end
end
