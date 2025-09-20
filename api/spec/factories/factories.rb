FactoryBot.define do
  factory :region do
    sequence(:name) { |n| "County #{n}" }
    sequence(:code) { |n| format("KE-%03d", n) }
  end

  factory :pharmacy do
    region
    sequence(:name) { |n| "Pharmacy #{n}" }
    sequence(:license_number) { |n| format("PPB/R/%05d", n) }
    town { "Kisumu" }
    mpesa_phone { "254712345678" }
  end

  factory :user do
    sequence(:email) { |n| "user#{n}@afyaclaim.test" }
    name { "Test User" }
    password { "password123" }
    role { "reviewer" }

    factory :pharmacist do
      role { "pharmacist" }
      pharmacy
    end

    factory :admin do
      role { "admin" }
    end
  end

  factory :product do
    sequence(:sku) { |n| "SKU-#{n}" }
    name { "Malaria RDT" }
    category { "malaria_test" }
    unit_price_cents { 15_000 }
    subsidy_cents { 12_000 }
    subsidised { true }
  end

  factory :inventory_item do
    pharmacy
    product
    quantity_on_hand { 100 }
    reorder_level { 10 }
  end

  factory :patient do
    sequence(:pseudonym) { |n| Digest::SHA256.hexdigest("patient-#{n}") }
    age_band { "25-49" }
    sex { "female" }
  end

  factory :sale do
    pharmacy
    client_uuid { SecureRandom.uuid }
    sold_at { Time.current.change(hour: 10) }

    transient do
      product { nil }
      quantity { 1 }
    end

    after(:build) do |sale, ctx|
      prod = ctx.product || create(:product)
      sale.sale_items.build(product: prod, quantity: ctx.quantity, unit_price_cents: prod.unit_price_cents)
    end
  end

  factory :claim do
    pharmacy
    product
    client_uuid { SecureRandom.uuid }
    quantity { 1 }
    amount_cents { product.subsidy_cents * quantity }
    submitted_at { Time.current.change(hour: 10) }
    status { "submitted" }
  end

  factory :claim_flag do
    claim
    rule { "velocity" }
    severity { "high" }
    message { "Too many claims" }
  end
end
