## Infrastructure

Create postgres and keycloak docker instances

## Prerequisites

### postgres

- ensure `password.txt` exists under `postgres` directory
- ensure `postgres/backup/latest-postgresschema.pgdump` is the latest sdeyaml export
  from [Fuzzwork](https://www.fuzzwork.co.uk/dump/)

### keycloak-signed certificate and private key exist and are named properly

    - `keycloak/dev.eveassist.com.crt`
    - `keycloak/dev.eveassist.com.key`
