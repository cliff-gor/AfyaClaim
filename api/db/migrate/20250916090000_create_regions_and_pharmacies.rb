class CreateRegionsAndPharmacies < ActiveRecord::Migration[8.1]
  def change
    create_table :regions do |t|
      t.string :name, null: false
      t.string :code, null: false
      t.timestamps
    end
    add_index :regions, :code, unique: true

    create_table :pharmacies do |t|
      t.string :name, null: false
      t.string :license_number, null: false
      t.references :region, null: false, foreign_key: true
      t.string :town, null: false
      t.decimal :latitude, precision: 9, scale: 6
      t.decimal :longitude, precision: 9, scale: 6
      t.string :mpesa_phone
      t.string :status, null: false, default: "active"
      t.integer :risk_score, null: false, default: 0
      t.timestamps
    end
    add_index :pharmacies, :license_number, unique: true
    add_index :pharmacies, :status
  end
end
