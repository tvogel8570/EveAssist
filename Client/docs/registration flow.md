Registration flow for EveAssist (EA)

- User lands on login page for the first time
- Selects register
- Enters requested information
    - Screen name, email address, password
- System creates keycloak identity
- System creates EveAssistUser (EAU)
- System links EAU and keycloak by storing other system's primary key as attribute

Future

- System sends confirmation to email address (use keycloak or create custom?)
- User clicks on link in confirmation
- System enables user in both keycloak and EA

