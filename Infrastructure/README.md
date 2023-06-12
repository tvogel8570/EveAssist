## Infrastructure
Create postgres and keycloak docker instances

## Prerequisites
### postgres
- ensure `password.txt` exists under `postgres` directory
- ensure `postgres/sql/backup/postgres-schema-latest.dmp` is the latest sdeyaml export from [Fuzzwork](https://www.fuzzwork.co.uk/dump/)
### keycloak
- ensure self-signed certificate and private key exist and are named properly
    - `keycloak/self_signed.crt`
    - `keycloak/self_signed_key.pem`
