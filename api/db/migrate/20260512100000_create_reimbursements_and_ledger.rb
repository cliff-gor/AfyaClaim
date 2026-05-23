class CreateReimbursementsAndLedger < ActiveRecord::Migration[8.1]
  def change
    create_table :reimbursements do |t|
      t.references :pharmacy, null: false, foreign_key: true
      t.integer :amount_cents, null: false
      t.string :status, null: false, default: "pending"
      t.string :idempotency_key, null: false
      t.string :mpesa_conversation_id
      t.string :mpesa_receipt
      t.text :failure_reason
      t.integer :attempts, null: false, default: 0
      t.datetime :paid_at
      t.timestamps
    end
    add_index :reimbursements, :idempotency_key, unique: true
    add_index :reimbursements, :mpesa_conversation_id, unique: true
    add_index :reimbursements, :status

    add_reference :claims, :reimbursement, foreign_key: true

    create_table :ledger_entries do |t|
      t.references :reimbursement, foreign_key: true
      t.string :account, null: false
      t.string :direction, null: false
      t.integer :amount_cents, null: false
      t.string :entry_group, null: false
      t.string :memo
      t.datetime :created_at, null: false
    end
    add_index :ledger_entries, :entry_group
    add_index :ledger_entries, :account
    add_check_constraint :ledger_entries, "amount_cents > 0", name: "ledger_positive_amount"
  end
end
