# Realistic demo data: 10 counties, ~40 pharmacies, 75 days of sales and claims,
# with a few planted fraud patterns so the review queue and analytics have signal.
require "active_support/testing/time_helpers"
include ActiveSupport::Testing::TimeHelpers

srand(2026)
Faker::Config.random = Random.new(2026)
ActiveJob::Base.queue_adapter = :inline
ActiveRecord::Base.logger = nil
Rails.logger.level = :warn
PASSWORD = "password123".freeze
SALT = "afyaclaim-demo-salt".freeze

puts "Resetting demo data…"
[ LedgerEntry, ClaimEvent, ClaimFlag ].each(&:delete_all)
Claim.update_all(reimbursement_id: nil)
[ Claim, Reimbursement, SaleItem, Sale, Patient, StockAdjustment, InventoryItem, User, Pharmacy, Product, Region ].each(&:delete_all)

REGIONS = {
  "Kisumu" => [ -0.0917, 34.7680, %w[Kisumu Ahero Maseno Kondele] ],
  "Kakamega" => [ 0.2827, 34.7519, %w[Kakamega Mumias Malava] ],
  "Busia" => [ 0.4608, 34.1115, %w[Busia Nambale Port\ Victoria] ],
  "Siaya" => [ 0.0607, 34.2881, %w[Siaya Bondo Ugunja] ],
  "Homa Bay" => [ -0.5273, 34.4571, %w[Homa\ Bay Mbita Oyugis] ],
  "Migori" => [ -1.0634, 34.4731, %w[Migori Awendo Rongo] ],
  "Kilifi" => [ -3.5107, 39.9093, %w[Kilifi Malindi Mtwapa] ],
  "Kwale" => [ -4.1816, 39.4606, %w[Ukunda Kwale Msambweni] ],
  "Turkana" => [ 3.1191, 35.5973, %w[Lodwar Kakuma] ],
  "Nairobi" => [ -1.2864, 36.8172, %w[Kibera Mathare Kayole Githurai] ]
}.freeze
# Lake-region and coastal counties carry a much higher malaria burden.
BURDEN = { "Kisumu" => 1.0, "Kakamega" => 0.8, "Busia" => 0.95, "Siaya" => 0.9, "Homa Bay" => 0.9,
           "Migori" => 0.75, "Kilifi" => 0.6, "Kwale" => 0.55, "Turkana" => 0.5, "Nairobi" => 0.2 }.freeze
POSITIVITY = { "Kisumu" => 0.34, "Kakamega" => 0.29, "Busia" => 0.36, "Siaya" => 0.31, "Homa Bay" => 0.33,
               "Migori" => 0.27, "Kilifi" => 0.22, "Kwale" => 0.2, "Turkana" => 0.25, "Nairobi" => 0.06 }.freeze

regions = REGIONS.each_with_index.to_h do |(name, _), i|
  [ name, Region.create!(name:, code: format("KE-%02d", [ 42, 37, 40, 41, 43, 44, 3, 2, 23, 47 ][i])) ]
end

products = [
  [ "RDT-MAL-01", "Malaria RDT (Pf/Pan)", "malaria_test", 15000, 12000, true ],
  [ "ACT-AL-24", "Artemether-Lumefantrine 24s", "antimalarial", 25000, 20000, true ],
  [ "ACT-AL-12", "Artemether-Lumefantrine 12s (child)", "antimalarial", 15000, 12000, true ],
  [ "FP-COC-01", "Combined oral contraceptive (cycle)", "family_planning", 10000, 8000, true ],
  [ "FP-DMPA-SC", "DMPA-SC injectable (Sayana Press)", "family_planning", 20000, 16000, true ],
  [ "FP-EC-01", "Emergency contraceptive pill", "family_planning", 15000, 10000, true ],
  [ "FP-CON-03", "Male condoms (3 pack)", "family_planning", 5000, 3000, true ],
  [ "ORS-ZN-01", "ORS + Zinc co-pack", "ors_zinc", 8000, 6000, true ],
  [ "PCM-500-10", "Paracetamol 500mg (10s)", "other", 5000, 0, false ],
  [ "AMX-250-SY", "Amoxicillin 250mg syrup", "other", 18000, 0, false ]
].map do |sku, name, category, price, subsidy, subsidised|
  Product.create!(sku:, name:, category:, unit_price_cents: price, subsidy_cents: subsidy, subsidised:)
end
by_sku = products.index_by(&:sku)

NAMES = %w[Afya\ Bora Neema Tumaini Upendo Baraka Shalom Imani Jamii Faraja Uzima Mwangaza Rehema Amani
           Wema Kheri Salama Furaha Riziki Bidii Tulia Nuru Fadhili Karibu Pendo Zawadi Huruma Msaada
           Siha Tabibu Mama\ Mboga Lakeside Sunrise Highway Mji Kijiji Riverside Mtaani Hope Grace Uhai].freeze

pharmacies = []
NAMES.each_with_index do |base, i|
  region_name = REGIONS.keys[i % REGIONS.size]
  lat, lng, towns = REGIONS[region_name]
  pharmacies << Pharmacy.create!(
    name: "#{base} #{%w[Chemist Pharmacy Chemist Pharmaceuticals Health\ Chemist][i % 5]}",
    license_number: format("PPB/R/%05d", 10_000 + (i * 137)),
    region: regions[region_name],
    town: towns[i % towns.size],
    latitude: lat + rand(-0.15..0.15), longitude: lng + rand(-0.15..0.15),
    mpesa_phone: format("2547%08d", rand(10_000_000..99_999_999)),
    status: i == 37 ? "suspended" : "active"
  )
end
home = pharmacies.first # Afya Bora Chemist, Kisumu
busia_fraud = pharmacies.find { |p| p.region.name == "Busia" && p != home }
ghost = pharmacies.find { |p| p.region.name == "Migori" }
night_owl = pharmacies.find { |p| p.region.name == "Nairobi" }
outlier = pharmacies.select { |p| p.region.name == "Kisumu" }.second

admin = User.create!(email: "admin@afyaclaim.test", name: "Achieng Odhiambo", role: "admin", password: PASSWORD)
reviewer = User.create!(email: "reviewer@afyaclaim.test", name: "Wanjiru Kamau", role: "reviewer", password: PASSWORD)
User.create!(email: "reviewer2@afyaclaim.test", name: "Brian Otieno", role: "reviewer", password: PASSWORD)
User.create!(email: "pharmacist@afyaclaim.test", name: "Mercy Atieno", role: "pharmacist", pharmacy: home, password: PASSWORD)
pharmacies.drop(1).each_with_index do |p, i|
  User.create!(email: "pharmacy#{i + 2}@afyaclaim.test", name: Faker::Name.name, role: "pharmacist", pharmacy: p, password: PASSWORD)
end
reviewers = User.where(role: %w[reviewer admin]).to_a

puts "Stocking #{pharmacies.size} pharmacies…"
pharmacies.each do |p|
  products.each do |prod|
    p.inventory_items.create!(product: prod, quantity_on_hand: rand(40..220), reorder_level: prod.subsidised ? 20 : 10)
  end
end

def pseudonym_for(phone) = Digest::SHA256.hexdigest("#{SALT}:#{phone}")

patient_pool = Array.new(1500) do
  Patient.create!(pseudonym: pseudonym_for("2547#{rand(10_000_000..99_999_999)}"),
    age_band: Patient::AGE_BANDS.sample, sex: Patient::SEXES.sample)
end

def record_sale(pharmacy, at, lines, patient)
  sale = pharmacy.sales.new(client_uuid: SecureRandom.uuid, sold_at: at, patient:)
  lines.each { |prod, qty| sale.sale_items.build(product: prod, quantity: qty, unit_price_cents: prod.unit_price_cents) }
  sale.save!
  sale
end

# Inline fraud rules only matter for recent claims; the nightly scan at the end
# covers the last 30 days. Older history is inserted directly for speed.
def submit_claim(pharmacy, prod, qty, at, sale:, patient:, test_result: nil, amount: nil)
  if at < 30.days.ago
    claim = pharmacy.claims.create!(client_uuid: SecureRandom.uuid, sale:, patient:, product: prod, quantity: qty,
      amount_cents: amount || (prod.subsidy_cents * qty), test_result:, submitted_at: at + rand(1..20).minutes)
    travel_to(claim.submitted_at) { Claims::StateMachine.record_submission!(claim) }
    return claim
  end

  travel_to(at + rand(1..20).minutes) do
    Claims::Submit.call(pharmacy:, attrs: {
      client_uuid: SecureRandom.uuid, sale_client_uuid: sale&.client_uuid, product_id: prod.id,
      quantity: qty, amount_cents: amount || (prod.subsidy_cents * qty), test_result:, submitted_at: Time.current
    }).claim.tap { |c| c.update_column(:patient_id, patient&.id) if sale.nil? && patient }
  end
end

DAYS = 75
now = Time.current
puts "Generating #{DAYS} days of sales and claims…"
DAYS.downto(0) do |days_ago|
  day = (now - days_ago.days).to_date
  # Gentle rainy-season wave in malaria demand across the window.
  season = 0.75 + (0.5 * Math.sin((DAYS - days_ago) / 14.0))
  pharmacies.each do |p|
    next if p.status == "suspended" && days_ago < 20

    region = p.region.name
    visits = (rand(2..5) * (0.4 + BURDEN[region] * season)).round
    visits.times do
      at = Time.zone.local(day.year, day.month, day.day, rand(7..20), rand(0..59))
      next if at > now

      patient = patient_pool.sample
      basket = []
      kind = rand
      if kind < 0.55 * BURDEN[region] + 0.1
        positive = rand < (p == outlier && days_ago < 14 ? 0.78 : POSITIVITY[region])
        basket << [ by_sku["RDT-MAL-01"], 1, positive ? "positive" : "negative" ]
        basket << [ by_sku[patient.age_band.in?(%w[0-4 5-14]) ? "ACT-AL-12" : "ACT-AL-24"], 1, nil ] if positive
      elsif kind < 0.8
        basket << [ by_sku[%w[FP-COC-01 FP-DMPA-SC FP-EC-01 FP-CON-03].sample], rand(1..2), nil ]
      else
        basket << [ by_sku["ORS-ZN-01"], 1, nil ]
      end
      basket << [ by_sku[%w[PCM-500-10 AMX-250-SY].sample], 1, nil ] if rand < 0.35

      sale = record_sale(p, at, basket.map { |prod, qty, _| [ prod, qty ] }, patient)
      basket.select { |prod, _, _| prod.subsidised }.each do |prod, qty, result|
        submit_claim(p, prod, qty, at, sale:, patient:, test_result: result)
      end
    end
  end
end

puts "Planting fraud patterns…"
# 1. Velocity burst: 50 malaria tests in under an hour from one Busia pharmacy.
burst_at = (now - 2.days).change(hour: 14, min: 5)
50.times do |i|
  at = burst_at + (i * 65).seconds
  patient = patient_pool.sample
  sale = record_sale(busia_fraud, at, [ [ by_sku["RDT-MAL-01"], 1 ] ], patient)
  submit_claim(busia_fraud, by_sku["RDT-MAL-01"], 1, at, sale:, patient:, test_result: "negative")
end
# 2. Claims with no matching sale.
12.times do |i|
  at = now - rand(1..6).days - rand(0..8).hours
  submit_claim(ghost, by_sku[%w[FP-DMPA-SC ACT-AL-24].sample], 1, at, sale: nil, patient: patient_pool.sample)
end
# 3. Inflated amounts.
4.times do
  at = now - rand(1..5).days
  patient = patient_pool.sample
  sale = record_sale(ghost, at, [ [ by_sku["ACT-AL-24"], 1 ] ], patient)
  submit_claim(ghost, by_sku["ACT-AL-24"], 1, at, sale:, patient:, amount: 45_000)
end
# 4. Late-night dispensing.
6.times do
  day = now - rand(1..10).days
  at = day.change(hour: [ 23, 1, 2, 3 ].sample, min: rand(0..59))
  patient = patient_pool.sample
  sale = record_sale(night_owl, at, [ [ by_sku["FP-EC-01"], 1 ] ], patient)
  submit_claim(night_owl, by_sku["FP-EC-01"], 1, at, sale:, patient:)
end
# 5. Same patient, same product, repeated within a week.
repeat_patient = patient_pool.first
[ 6, 4, 1 ].each do |d|
  at = (now - d.days).change(hour: 10)
  sale = record_sale(home, at, [ [ by_sku["FP-EC-01"], 1 ] ], repeat_patient)
  submit_claim(home, by_sku["FP-EC-01"], 1, at, sale:, patient: repeat_patient)
end

puts "Running nightly fraud scan…"
FraudScanJob.perform_now(since: 30.days.ago)

puts "Reviewing historical claims…"
reject_reasons = [
  "Receipt photo unreadable — please resubmit with a clear image",
  "Sale record does not match claimed quantity",
  "Duplicate of an earlier claim for the same patient",
  "Claimed amount exceeds programme rate",
  "Product not covered under this programme period"
]
Claim.where(submitted_at: ..(now - 2.days)).includes(:flags, :product, :pharmacy).find_each do |claim|
  age_days = ((now - claim.submitted_at) / 1.day).floor
  reviewed_at = claim.submitted_at + rand(3..40).hours
  next if reviewed_at > now - 2.hours
  next if age_days < 5 && rand < 0.55 # leave a realistic backlog

  machine = Claims::StateMachine.new(claim, actor: reviewers.sample)
  travel_to(reviewed_at - rand(10..90).minutes) { machine.start_review! } if rand < 0.7
  travel_to(reviewed_at) do
    if claim.flags.any? { |f| f.severity == "high" && !f.resolved } || rand < 0.05
      machine.reject!(reason: reject_reasons.sample)
    else
      machine.approve!(note: [ nil, nil, "Checked against sale record", "OK" ].sample)
    end
  end
end

puts "Paying out approved claims older than a week…"
[ 63, 49, 35, 21, 7 ].each do |d|
  travel_to(now - d.days) do
    Payouts::CreateBatch.call(approved_before: Time.current)
  end
end

# A few claims currently under review, as if reviewers are mid-shift.
Claim.submitted.where(flags_count: 0).order(submitted_at: :desc).limit(6).each do |c|
  Claims::StateMachine.new(c, actor: reviewer).start_review!
end

# Leave a handful of pharmacies short on subsidised stock.
InventoryItem.joins(:product).merge(Product.subsidised).order(Arel.sql("RANDOM()")).limit(14).each_with_index do |item, i|
  item.update!(quantity_on_hand: i < 6 ? 0 : rand(3..15))
end

Pharmacy.find_each { |p| Fraud::RiskScore.recompute!(p) }

puts "Done: #{Pharmacy.count} pharmacies, #{Sale.count} sales, #{Claim.count} claims " \
     "(#{Claim.flagged.count} flagged), #{Reimbursement.paid.count} payouts."
puts "Logins (password: #{PASSWORD}): admin@afyaclaim.test, reviewer@afyaclaim.test, pharmacist@afyaclaim.test"
