# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.1].define(version: 2026_05_12_100000) do
  # These are extensions that must be enabled in order to support this database
  enable_extension "pg_catalog.plpgsql"

  create_table "claim_events", force: :cascade do |t|
    t.bigint "claim_id", null: false
    t.string "from_status"
    t.string "to_status", null: false
    t.bigint "actor_id"
    t.text "note"
    t.jsonb "metadata", default: {}, null: false
    t.string "prev_digest"
    t.string "digest", null: false
    t.datetime "created_at", null: false
    t.index ["actor_id"], name: "index_claim_events_on_actor_id"
    t.index ["claim_id"], name: "index_claim_events_on_claim_id"
  end

  create_table "claim_flags", force: :cascade do |t|
    t.bigint "claim_id", null: false
    t.string "rule", null: false
    t.string "severity", null: false
    t.string "message", null: false
    t.boolean "resolved", default: false, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["claim_id", "rule"], name: "index_claim_flags_on_claim_id_and_rule", unique: true
    t.index ["claim_id"], name: "index_claim_flags_on_claim_id"
    t.index ["severity", "resolved"], name: "index_claim_flags_on_severity_and_resolved"
  end

  create_table "claims", force: :cascade do |t|
    t.string "client_uuid", null: false
    t.string "reference", null: false
    t.bigint "pharmacy_id", null: false
    t.bigint "sale_id"
    t.bigint "product_id", null: false
    t.bigint "patient_id"
    t.integer "quantity", default: 1, null: false
    t.integer "amount_cents", null: false
    t.string "test_result"
    t.string "status", default: "submitted", null: false
    t.datetime "submitted_at", null: false
    t.bigint "reviewed_by_id"
    t.datetime "reviewed_at"
    t.text "rejection_reason"
    t.integer "flags_count", default: 0, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.bigint "reimbursement_id"
    t.index ["client_uuid"], name: "index_claims_on_client_uuid", unique: true
    t.index ["patient_id"], name: "index_claims_on_patient_id"
    t.index ["pharmacy_id", "product_id", "submitted_at"], name: "index_claims_on_pharmacy_id_and_product_id_and_submitted_at"
    t.index ["pharmacy_id"], name: "index_claims_on_pharmacy_id"
    t.index ["product_id"], name: "index_claims_on_product_id"
    t.index ["reference"], name: "index_claims_on_reference", unique: true
    t.index ["reimbursement_id"], name: "index_claims_on_reimbursement_id"
    t.index ["reviewed_by_id"], name: "index_claims_on_reviewed_by_id"
    t.index ["sale_id"], name: "index_claims_on_sale_id"
    t.index ["status", "submitted_at"], name: "index_claims_on_status_and_submitted_at"
    t.index ["updated_at"], name: "index_claims_on_updated_at"
  end

  create_table "inventory_items", force: :cascade do |t|
    t.bigint "pharmacy_id", null: false
    t.bigint "product_id", null: false
    t.integer "quantity_on_hand", default: 0, null: false
    t.integer "reorder_level", default: 10, null: false
    t.integer "lock_version", default: 0, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["pharmacy_id", "product_id"], name: "index_inventory_items_on_pharmacy_id_and_product_id", unique: true
    t.index ["pharmacy_id"], name: "index_inventory_items_on_pharmacy_id"
    t.index ["product_id"], name: "index_inventory_items_on_product_id"
    t.check_constraint "quantity_on_hand >= 0", name: "inventory_non_negative"
  end

  create_table "ledger_entries", force: :cascade do |t|
    t.bigint "reimbursement_id"
    t.string "account", null: false
    t.string "direction", null: false
    t.integer "amount_cents", null: false
    t.string "entry_group", null: false
    t.string "memo"
    t.datetime "created_at", null: false
    t.index ["account"], name: "index_ledger_entries_on_account"
    t.index ["entry_group"], name: "index_ledger_entries_on_entry_group"
    t.index ["reimbursement_id"], name: "index_ledger_entries_on_reimbursement_id"
    t.check_constraint "amount_cents > 0", name: "ledger_positive_amount"
  end

  create_table "patients", force: :cascade do |t|
    t.string "pseudonym", null: false
    t.string "age_band"
    t.string "sex"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["pseudonym"], name: "index_patients_on_pseudonym", unique: true
  end

  create_table "pharmacies", force: :cascade do |t|
    t.string "name", null: false
    t.string "license_number", null: false
    t.bigint "region_id", null: false
    t.string "town", null: false
    t.decimal "latitude", precision: 9, scale: 6
    t.decimal "longitude", precision: 9, scale: 6
    t.string "mpesa_phone"
    t.string "status", default: "active", null: false
    t.integer "risk_score", default: 0, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["license_number"], name: "index_pharmacies_on_license_number", unique: true
    t.index ["region_id"], name: "index_pharmacies_on_region_id"
    t.index ["status"], name: "index_pharmacies_on_status"
  end

  create_table "products", force: :cascade do |t|
    t.string "sku", null: false
    t.string "name", null: false
    t.string "category", null: false
    t.integer "unit_price_cents", null: false
    t.integer "subsidy_cents", default: 0, null: false
    t.boolean "subsidised", default: false, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["sku"], name: "index_products_on_sku", unique: true
  end

  create_table "regions", force: :cascade do |t|
    t.string "name", null: false
    t.string "code", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["code"], name: "index_regions_on_code", unique: true
  end

  create_table "reimbursements", force: :cascade do |t|
    t.bigint "pharmacy_id", null: false
    t.integer "amount_cents", null: false
    t.string "status", default: "pending", null: false
    t.string "idempotency_key", null: false
    t.string "mpesa_conversation_id"
    t.string "mpesa_receipt"
    t.text "failure_reason"
    t.integer "attempts", default: 0, null: false
    t.datetime "paid_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["idempotency_key"], name: "index_reimbursements_on_idempotency_key", unique: true
    t.index ["mpesa_conversation_id"], name: "index_reimbursements_on_mpesa_conversation_id", unique: true
    t.index ["pharmacy_id"], name: "index_reimbursements_on_pharmacy_id"
    t.index ["status"], name: "index_reimbursements_on_status"
  end

  create_table "sale_items", force: :cascade do |t|
    t.bigint "sale_id", null: false
    t.bigint "product_id", null: false
    t.integer "quantity", null: false
    t.integer "unit_price_cents", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["product_id"], name: "index_sale_items_on_product_id"
    t.index ["sale_id"], name: "index_sale_items_on_sale_id"
  end

  create_table "sales", force: :cascade do |t|
    t.string "client_uuid", null: false
    t.bigint "pharmacy_id", null: false
    t.bigint "patient_id"
    t.datetime "sold_at", null: false
    t.integer "total_cents", default: 0, null: false
    t.string "receipt_key"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["client_uuid"], name: "index_sales_on_client_uuid", unique: true
    t.index ["patient_id"], name: "index_sales_on_patient_id"
    t.index ["pharmacy_id", "sold_at"], name: "index_sales_on_pharmacy_id_and_sold_at"
    t.index ["pharmacy_id"], name: "index_sales_on_pharmacy_id"
  end

  create_table "solid_queue_batch_executions", force: :cascade do |t|
    t.bigint "job_id", null: false
    t.bigint "batch_id", null: false
    t.datetime "created_at", null: false
    t.index ["batch_id"], name: "index_solid_queue_batch_executions_on_batch_id"
    t.index ["job_id"], name: "index_solid_queue_batch_executions_on_job_id", unique: true
  end

  create_table "solid_queue_batches", force: :cascade do |t|
    t.string "active_job_batch_id"
    t.string "description"
    t.text "on_finish"
    t.text "on_success"
    t.text "on_failure"
    t.text "metadata"
    t.integer "total_jobs", default: 0, null: false
    t.integer "completed_jobs", default: 0, null: false
    t.integer "failed_jobs", default: 0, null: false
    t.datetime "enqueued_at"
    t.datetime "finished_at"
    t.datetime "failed_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["active_job_batch_id"], name: "index_solid_queue_batches_on_active_job_batch_id", unique: true
    t.index ["finished_at"], name: "index_solid_queue_batches_on_finished_at"
  end

  create_table "solid_queue_blocked_executions", force: :cascade do |t|
    t.bigint "job_id", null: false
    t.string "queue_name", null: false
    t.integer "priority", default: 0, null: false
    t.string "concurrency_key", null: false
    t.datetime "expires_at", null: false
    t.datetime "created_at", null: false
    t.index ["concurrency_key", "priority", "job_id"], name: "index_solid_queue_blocked_executions_for_release"
    t.index ["expires_at", "concurrency_key"], name: "index_solid_queue_blocked_executions_for_maintenance"
    t.index ["job_id"], name: "index_solid_queue_blocked_executions_on_job_id", unique: true
  end

  create_table "solid_queue_claimed_executions", force: :cascade do |t|
    t.bigint "job_id", null: false
    t.bigint "process_id"
    t.datetime "created_at", null: false
    t.index ["job_id"], name: "index_solid_queue_claimed_executions_on_job_id", unique: true
    t.index ["process_id", "job_id"], name: "index_solid_queue_claimed_executions_on_process_id_and_job_id"
  end

  create_table "solid_queue_failed_executions", force: :cascade do |t|
    t.bigint "job_id", null: false
    t.text "error"
    t.datetime "created_at", null: false
    t.index ["job_id"], name: "index_solid_queue_failed_executions_on_job_id", unique: true
  end

  create_table "solid_queue_jobs", force: :cascade do |t|
    t.string "queue_name", null: false
    t.string "class_name", null: false
    t.text "arguments"
    t.integer "priority", default: 0, null: false
    t.string "active_job_id"
    t.datetime "scheduled_at"
    t.datetime "finished_at"
    t.string "concurrency_key"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.bigint "batch_id"
    t.index ["active_job_id"], name: "index_solid_queue_jobs_on_active_job_id"
    t.index ["batch_id"], name: "index_solid_queue_jobs_on_batch_id"
    t.index ["class_name"], name: "index_solid_queue_jobs_on_class_name"
    t.index ["finished_at"], name: "index_solid_queue_jobs_on_finished_at"
    t.index ["queue_name", "finished_at"], name: "index_solid_queue_jobs_for_filtering"
    t.index ["scheduled_at", "finished_at"], name: "index_solid_queue_jobs_for_alerting"
  end

  create_table "solid_queue_pauses", force: :cascade do |t|
    t.string "queue_name", null: false
    t.datetime "created_at", null: false
    t.index ["queue_name"], name: "index_solid_queue_pauses_on_queue_name", unique: true
  end

  create_table "solid_queue_processes", force: :cascade do |t|
    t.string "kind", null: false
    t.datetime "last_heartbeat_at", null: false
    t.bigint "supervisor_id"
    t.integer "pid", null: false
    t.string "hostname"
    t.text "metadata"
    t.datetime "created_at", null: false
    t.string "name", null: false
    t.index ["last_heartbeat_at"], name: "index_solid_queue_processes_on_last_heartbeat_at"
    t.index ["name", "supervisor_id"], name: "index_solid_queue_processes_on_name_and_supervisor_id", unique: true
    t.index ["supervisor_id"], name: "index_solid_queue_processes_on_supervisor_id"
  end

  create_table "solid_queue_ready_executions", force: :cascade do |t|
    t.bigint "job_id", null: false
    t.string "queue_name", null: false
    t.integer "priority", default: 0, null: false
    t.datetime "created_at", null: false
    t.index ["job_id"], name: "index_solid_queue_ready_executions_on_job_id", unique: true
    t.index ["priority", "job_id"], name: "index_solid_queue_poll_all"
    t.index ["queue_name", "priority", "job_id"], name: "index_solid_queue_poll_by_queue"
  end

  create_table "solid_queue_recurring_executions", force: :cascade do |t|
    t.bigint "job_id", null: false
    t.string "task_key", null: false
    t.datetime "run_at", null: false
    t.datetime "created_at", null: false
    t.index ["job_id"], name: "index_solid_queue_recurring_executions_on_job_id", unique: true
    t.index ["task_key", "run_at"], name: "index_solid_queue_recurring_executions_on_task_key_and_run_at", unique: true
  end

  create_table "solid_queue_recurring_tasks", force: :cascade do |t|
    t.string "key", null: false
    t.string "schedule", null: false
    t.string "command", limit: 2048
    t.string "class_name"
    t.text "arguments"
    t.string "queue_name"
    t.integer "priority", default: 0
    t.boolean "static", default: true, null: false
    t.text "description"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["key"], name: "index_solid_queue_recurring_tasks_on_key", unique: true
    t.index ["static"], name: "index_solid_queue_recurring_tasks_on_static"
  end

  create_table "solid_queue_scheduled_executions", force: :cascade do |t|
    t.bigint "job_id", null: false
    t.string "queue_name", null: false
    t.integer "priority", default: 0, null: false
    t.datetime "scheduled_at", null: false
    t.datetime "created_at", null: false
    t.index ["job_id"], name: "index_solid_queue_scheduled_executions_on_job_id", unique: true
    t.index ["scheduled_at", "priority", "job_id"], name: "index_solid_queue_dispatch_all"
  end

  create_table "solid_queue_semaphores", force: :cascade do |t|
    t.string "key", null: false
    t.integer "value", default: 1, null: false
    t.datetime "expires_at", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["expires_at"], name: "index_solid_queue_semaphores_on_expires_at"
    t.index ["key", "value"], name: "index_solid_queue_semaphores_on_key_and_value"
    t.index ["key"], name: "index_solid_queue_semaphores_on_key", unique: true
  end

  create_table "stock_adjustments", force: :cascade do |t|
    t.string "client_uuid", null: false
    t.bigint "inventory_item_id", null: false
    t.integer "delta", null: false
    t.string "reason", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["client_uuid"], name: "index_stock_adjustments_on_client_uuid", unique: true
    t.index ["inventory_item_id"], name: "index_stock_adjustments_on_inventory_item_id"
  end

  create_table "users", force: :cascade do |t|
    t.string "email", null: false
    t.string "name", null: false
    t.string "password_digest", null: false
    t.string "role", default: "pharmacist", null: false
    t.bigint "pharmacy_id"
    t.datetime "last_login_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index "lower((email)::text)", name: "index_users_on_lower_email", unique: true
    t.index ["pharmacy_id"], name: "index_users_on_pharmacy_id"
  end

  add_foreign_key "claim_events", "claims"
  add_foreign_key "claim_events", "users", column: "actor_id"
  add_foreign_key "claim_flags", "claims"
  add_foreign_key "claims", "patients"
  add_foreign_key "claims", "pharmacies"
  add_foreign_key "claims", "products"
  add_foreign_key "claims", "reimbursements"
  add_foreign_key "claims", "sales"
  add_foreign_key "claims", "users", column: "reviewed_by_id"
  add_foreign_key "inventory_items", "pharmacies"
  add_foreign_key "inventory_items", "products"
  add_foreign_key "ledger_entries", "reimbursements"
  add_foreign_key "pharmacies", "regions"
  add_foreign_key "reimbursements", "pharmacies"
  add_foreign_key "sale_items", "products"
  add_foreign_key "sale_items", "sales"
  add_foreign_key "sales", "patients"
  add_foreign_key "sales", "pharmacies"
  add_foreign_key "solid_queue_batch_executions", "solid_queue_batches", column: "batch_id", on_delete: :cascade
  add_foreign_key "solid_queue_batch_executions", "solid_queue_jobs", column: "job_id", on_delete: :cascade
  add_foreign_key "solid_queue_blocked_executions", "solid_queue_jobs", column: "job_id", on_delete: :cascade
  add_foreign_key "solid_queue_claimed_executions", "solid_queue_jobs", column: "job_id", on_delete: :cascade
  add_foreign_key "solid_queue_failed_executions", "solid_queue_jobs", column: "job_id", on_delete: :cascade
  add_foreign_key "solid_queue_ready_executions", "solid_queue_jobs", column: "job_id", on_delete: :cascade
  add_foreign_key "solid_queue_recurring_executions", "solid_queue_jobs", column: "job_id", on_delete: :cascade
  add_foreign_key "solid_queue_scheduled_executions", "solid_queue_jobs", column: "job_id", on_delete: :cascade
  add_foreign_key "stock_adjustments", "inventory_items"
  add_foreign_key "users", "pharmacies"
end
