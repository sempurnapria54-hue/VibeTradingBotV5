ui = true

disable_clustering = true

storage "file" {
  path = "/vault/file"
}

listener "tcp" {
  address = "0.0.0.0:18200"
  tls_disable = 1
}

api_addr = "http://vault:18200"

disable_mlock = true
