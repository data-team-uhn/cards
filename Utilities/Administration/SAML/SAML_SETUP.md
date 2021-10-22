# SAML Setup

1. Ensure that you have a SAML Identity Provider (IdP) server available.
If you are testing locally, you may use a Keycloak Docker container

```bash
docker pull quay.io/keycloak/keycloak:15.0.2
docker run --rm -p 8484:8080 -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin quay.io/keycloak/keycloak:15.0.2
```

2. Ensure that a _realm_ and _user account_ are available on the IdP
server. If you are using the local Keycloak Docker container for testing
purposes, do the following:

2.1. Visit `http://localhost:8484/auth/admin` and login as `admin`:`admin`

2.2. Import the `myrealm` realm by clicking _Master_, then _Add realm_,
then _Select file_, select the `test_realm.json` file, then click
_Create_.

**Please note that the file `test_realm.json` contains predefined
private keys and therefore should _only_ be used in test environments
and _never_ in production environments.**

2.3. Create the `myuser` user. Go to _Users_ --> _Add user_ and enter
the following values:
  - _Username_: `myuser`
  - _Email_: `myuser@uhn.ca`
  - _First Name_: `My`
  - _Last Name_: `User`
then click _Save_. Click on the _Credentials_ tab and enter the
following values:
  - _Password_: `password`
  - _Password Confirmation_: `password`
  - _Temporary_: `OFF`
then click _Set Password_.

If this CARDS instance is being deployed in a production environment,
please verify with the Single-Sign-On (SSO) administrator that the
required user accounts are available.

3. Now, create the Java keystore that is to be used by CARDS by running
`./prepare_keystore.sh`.

3.1. You may leave the following fields blank

```
Country Name (2 letter code) [AU]:
State or Province Name (full name) [Some-State]:
Locality Name (eg, city) []:
Organization Name (eg, company) [Internet Widgits Pty Ltd]:
Organizational Unit Name (eg, section) []:
Common Name (e.g. server FQDN or YOUR name) []:
Email Address []:
```

3.2. Enter the keystore _Export_ password. You may use the default
`changeit` value.

3.3. Import `samlSPcert.pem` into the IdP. If you are using a local
Keycloak Docker container as an IdP, go to
_Clients_ --> _http://localhost:8080_ --> _Keys_ --> _Import_. Select
_Certificate PEM_ as the _Archive Format_ and upload `samlSPcert.pem`.
Otherwise send this file to the SSO administrator. Press _Enter_.

3.4. Next, the IdP public key certificate will be automatically imported
from the IdP's XML metadata URL. Enter the keystore export password from
_step 3.2_ and enter `yes` for `Trust this certificate? [no]:`.

3.5. Lastly, copy the file `samlKeystore.p12` into the root directory for
the CARDS project. You may now start CARDS with _SAML Login Support_
enabled (`./start_cards.sh --dev --saml`).

4. Enable and configure the SAML login for CARDS by running:
`./setup_saml.sh`.

5. Point your browser to CARDS (`http://localhost:8080` if testing
locally). You should automatically be redirected to the SSO login page
(Keycloak instance at `localhost:8484` if testing locally). Login with
your credentials (`myuser`:`password` if using the testing Keycloak
Docker container). You should now be able to access CARDS as your user.

### Security Tests

The following tests can be performed to verify that CARDS will _only_
accept SAML logins from trusted identity providers (IdPs).

- After sucessfully following the above instructions to configure a local
Keycloak Docker container as an IdP:
  - Visit `http://localhost:8484/auth/admin` and login as `admin`:`admin`
  - Go to _Realm Settings_ --> _Keys_ --> _Providers_ --> _Add keystore..._ _rsa-generated_
    - _Console Display Name_: _some-other-key_
    - _Priority_: _10_
    - _Enabled_: _ON_
    - _Active_: _ON_
    - _Algorithm_: _RS256_
    - _Key size_: _2048_
    - _Key use_: _sig_
  - Click _Save_
  - Go back to the _Providers_ tab and delete the `rsa-generated` key
  - Visit `localhost:8080` again and you will be redirected to Keycloak (`localhost:8484`) just as before
  - Attempt to login as `myuser`:`password`
  - You should be shown a page with the error `Signature cryptographic validation not successful`
  - Go back to `http://localhost:8484/auth/admin` and login as `admin`:`admin`
  - Go to _Clients_ --> _http://localhost:8080/_
  - Disable _Sign Documents_ and _Sign Assertions_
  - Click _Save_
  - Visit `localhost:8080` again and you will be redirected to Keycloak (`localhost:8484`) just as before
  - Attempt to login as `myuser`:`password`
  - You should be shown a page with the error `The SAML Assertion was not signed!`
