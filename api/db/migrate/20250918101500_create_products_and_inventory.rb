class CreateProductsAndInventory < ActiveRecord::Migration[8.1]
  def change
    create_table :products do |t|
      t.string :sku, null: false
      t.string :name, null: false
      t.string :category, null: false
      t.integer :unit_price_cents, null: false
      t.integer :subsidy_cents, null: false, default: 0
      t.boolean :subsidised, null: false, default: false
      t.timestamps
    end
    add_index :products, :sku, unique: true

    create_table :inventory_items do |t|
      t.references :pharmacy, null: false, foreign_key: true
      t.references :product, null: false, foreign_key: true
      t.integer :quantity_on_hand, null: false, default: 0
      t.integer :reorder_level, null: false, default: 10
      t.integer :lock_version, null: false, default: 0
      t.timestamps
    end
    add_index :inventory_items, %i[pharmacy_id product_id], unique: true
    add_check_constraint :inventory_items, "quantity_on_hand >= 0", name: "inventory_non_negative"

    create_table :stock_adjustments do |t|
      t.string :client_uuid, null: false
      t.references :inventory_item, null: false, foreign_key: true
      t.integer :delta, null: false
      t.string :reason, null: false
      t.timestamps
    end
    add_index :stock_adjustments, :client_uuid, unique: true
  end
end
