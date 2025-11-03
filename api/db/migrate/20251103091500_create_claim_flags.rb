class CreateClaimFlags < ActiveRecord::Migration[8.1]
  def change
    create_table :claim_flags do |t|
      t.references :claim, null: false, foreign_key: true
      t.string :rule, null: false
      t.string :severity, null: false
      t.string :message, null: false
      t.boolean :resolved, null: false, default: false
      t.timestamps
    end
    add_index :claim_flags, %i[claim_id rule], unique: true
    add_index :claim_flags, %i[severity resolved]
  end
end
