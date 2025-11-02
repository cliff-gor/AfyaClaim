class DocsController < ActionController::API
  SPEC_PATH = Rails.root.join("swagger/v1/openapi.yaml")

  def index
    render html: <<~HTML.html_safe
      <!doctype html>
      <html><head><meta charset="utf-8"><title>AfyaClaim API</title>
      <meta name="viewport" content="width=device-width, initial-scale=1"></head>
      <body style="margin:0"><redoc spec-url="/api-docs/openapi.yaml"></redoc>
      <script src="https://cdn.jsdelivr.net/npm/redoc@2.1.5/bundles/redoc.standalone.js"></script></body></html>
    HTML
  end

  def spec
    send_file SPEC_PATH, type: "application/yaml", disposition: "inline"
  end
end
