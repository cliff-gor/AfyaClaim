class CreateClaims < ActiveRecord::Migration[8.1]
  def change
    create_table :claims do |t|
      t.string :client_uuid, null: false
      t.string :reference, null: false
      t.references :pharmacy, null: false, foreign_key: true
      t.references :sale, foreign_key: true
      t.references :product, null: false, foreign_key: true
      t.references :patient, foreign_key: true
      t.integer :quantity, null: false, default: 1
      t.integer :amount_cents, null: false
      t.string :test_result
      t.string :status, null: false, default: "submitted"
      t.datetime :submitted_at, null: false
      t.references :reviewed_by, foreign_key: { to_table: :users }
      t.datetime :reviewed_at
      t.text :rejection_reason
      t.integer :flags_count, null: false, default: 0
      t.timestamps
    end
    add_index :claims, :client_uuid, unique: true
    add_index :claims, :reference, unique: true
    add_index :claims, %i[status submitted_at]
    add_index :claims, %i[pharmacy_id product_id submitted_at]
    add_index :claims, :updated_at
  end
end
