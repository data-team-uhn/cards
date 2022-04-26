- Using a Python shell with the `requests` module as an example, here is
how to obtain a JWT and query Torch for upcoming appointments.

- Import the `requests` Python module

```python
import requests
```

- Obtain a Vault Session Token for the `proms-cardsapp` Vault service account.

```python
vault_resp = requests.post("https://vault.prod.uhn.io/v1/auth/userpass/login/prom-cardsapp", json={"password":"PASSWORD-FOR-VAULT-SERVICE-ACCOUNT"})
vault_session_token = vault_resp.json()['auth']['client_token']
```

- Use that Vault Session Token to obtain a JWT that can be used for querying Torch

```python
vault_resp = requests.get("https://vault.prod.uhn.io/v1/identity/oidc/token/proms-role", headers={"X-Vault-Token": vault_session_token})
torch_access_jwt = vault_resp.json()['data']['token']
```

- Query Torch for upcoming appointments

```python
graphql_query = '{patientsByDateAndClinic(location: "6012-HC-Congenital Cardiac", start: "2022-04-25", end: "2022-04-28") {fhirID mrn name {given family} appointments {fhirID time location participants{role physician {name {given family} eID}}}}}'
torch_resp = requests.post("https://prom.prod.uhn.io/graphql", data=graphql_query, headers={"Content-Type": "application/graphql", "Authorization": "Bearer " + torch_access_jwt})
```

- The upcoming appointments can be accessed through `torch_resp.json()['data']`.
