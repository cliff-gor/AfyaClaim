class CreatePatientsAndSales < ActiveRecord::Migration[8.1]
  def change
    create_table :patients do |t|
      t.string :pseudonym, null: false
      t.string :age_band
      t.string :sex
      t.timestamps
    end
    add_index :patients, :pseudonym, unique: true

    create_table :sales do |t|
      t.string :client_uuid, null: false
      t.references :pharmacy, null: false, foreign_key: true
      t.references :patient, foreign_key: true
      t.datetime :sold_at, null: false
      t.integer :total_cents, null: false, default: 0
      t.string :receipt_key
      t.timestamps
    end
    add_index :sales, :client_uuid, unique: true
    add_index :sales, %i[pharmacy_id sold_at]

    create_table :sale_items do |t|
      t.references :sale, null: false, foreign_key: true
      t.references :product, null: false, foreign_key: true
      t.integer :quantity, null: false
      t.integer :unit_price_cents, null: false
      t.timestamps
    end
  end
end
