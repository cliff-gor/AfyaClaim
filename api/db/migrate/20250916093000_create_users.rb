class CreateUsers < ActiveRecord::Migration[8.1]
  def change
    create_table :users do |t|
      t.string :email, null: false
      t.string :name, null: false
      t.string :password_digest, null: false
      t.string :role, null: false, default: "pharmacist"
      t.references :pharmacy, foreign_key: true
      t.datetime :last_login_at
      t.timestamps
    end
    add_index :users, "lower(email)", unique: true
  end
end
