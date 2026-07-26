Rails.application.routes.draw do
  get "up" => "rails/health#show", as: :rails_health_check
  get "api-docs" => "docs#index"
  get "api-docs/openapi.yaml" => "docs#spec"

  namespace :api do
    namespace :v1 do
      post "auth/login", to: "auth#login"
      get "me", to: "auth#me"

      resources :products, only: :index
      resources :inventory, only: %i[index update]
      resources :sales, only: %i[index create]

      post "sync", to: "sync#push"
      get "sync", to: "sync#pull"

      resources :claims, only: %i[index show create] do
        post :bulk_approve, on: :collection
        member do
          post :start_review
          post :approve
          post :reject
        end
      end

      resources :pharmacies, only: %i[index show]
      resources :reimbursements, only: %i[index create]

      scope :analytics, controller: :analytics, as: :analytics do
        get :overview
        get :claims_by_region
        get :claims_by_product
        get :trend
        get :stockouts
      end

      post "mpesa/b2c/result", to: "mpesa_callbacks#result"
      post "mpesa/b2c/timeout", to: "mpesa_callbacks#timeout"
    end
  end
end
