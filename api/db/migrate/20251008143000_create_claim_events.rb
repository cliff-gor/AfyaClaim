class CreateClaimEvents < ActiveRecord::Migration[8.1]
  def change
    create_table :claim_events do |t|
      t.references :claim, null: false, foreign_key: true
      t.string :from_status
      t.string :to_status, null: false
      t.references :actor, foreign_key: { to_table: :users }
      t.text :note
      t.jsonb :metadata, null: false, default: {}
      t.string :prev_digest
      t.string :digest, null: false
      t.datetime :created_at, null: false
    end
  end
end
