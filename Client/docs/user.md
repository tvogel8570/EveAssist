Key points

- Access to EveAssist Api is via Oauth2 using local keycloak
- Access to EveAssist Client has options
    - email + password stored in keycloak
    - eve account from CCP
- Authorization
    - Roles from keycloak
- EAUser is locally defined, i.e. JPA Entity backed by Postgres
    - Primary attributes
        - Unique Id
        - Screen name
        - email
        - createTimestamp
        - updateTimestamp
        - enabled
    - is part of Entity Graphs for application functionality


