module Api
  module V1
    class ProductsController < ApplicationController
      def index
        render json: { data: Product.order(:category, :name).map { |p| Serializers.product(p) } }
      end
    end
  end
end
