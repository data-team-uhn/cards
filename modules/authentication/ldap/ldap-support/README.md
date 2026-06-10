# External LDAP/ActiveDirectory authentication

This module adds support for using an external LDAP/ActiveDirectory server for authentication.

There are two source files in this module. `25-external-authentication.txt` adds support for external authentication, but does not include any specific external identity provider. `60-ldap-connection.txt` enables and configures an LDAP connection.

The default sources don't connect to any specific server, but instead enable generic support for external authentication servers, and LDAP in particular. The actual configuration can be defined either at build time, through the filesystem on a specific server where CARDS is installed, or through the UI of a running instance.

## Configuring at build time

`60-ldap-connection.txt` uses properties defined in the `pom.xml` file (Maven properties).
However, in the POM these properties are defined as empty, which means that by default no connection is configured, so LDAP isn't enabled.
The preferred way to configure these during the build is to define them in the `~/.m2/settings.xml` file, which is local to the developer's machine.

Here's an example configuration:

```
<settings>
  <profiles>
    <profile>
      <id>sickkids-ldap</id>
      <properties>
        <cards.ldap.dn>CN\=Service\ User,OU\=Service\ Accounts,DC\=sickkids,DC\=ca</cards.ldap.dn>
        <cards.ldap.password>the password</cards.ldap.password>
        <cards.ldap.host>sickkids.ca</cards.ldap.host>
        <cards.ldap.baseDn>DC\=sickkids,DC\=ca</cards.ldap.baseDn>
        <cards.ldap.idAttribute>sAMAccountName</cards.ldap.idAttribute>
        <cards.ldap.userClass>user</cards.ldap.userClass>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>sickkids-ldap</activeProfile>
  </activeProfiles>
</settings>
```

Note that all `=` and ` ` (space) characters need to be escaped.

After the build, the actual values will be substituted and included in the final artifact. This means that:

1. The username AND PASSWORD (!!!) will be stored as plain text in the artifact.
2. If the developer does have the LDAP credentials configured, during a release these developer-specific settings will be embedded in the released artifact, which is probably not what is needed.

So, if the LDAP integration isn't actively tested during each build, it is recommended to NOT make the profile active by default in the settings, but to explicitly enable it for specific builds by using the `-P profile-name` parameter when invoking `mvn`, for example:

```
mvn clean install -Psickkids-ldap
```

## Configuring on the server filesystem

Create a file `$SLINGHOME/config/org/apache/jackrabbit/oak/security/authentication/ldap/impl/LdapIdentityProvider.config` with the following content (adapted for the specific LDAP server):

```
:org.apache.felix.configadmin.revision:=L"1"
bind.dn="CN\=Service\ User,OU\=Service\ Accounts,DC\=sickkids,DC\=ca"
bind.password="the password"
host.name="sickkids.ca"
provider.name="ldap"
service.pid="org.apache.jackrabbit.oak.security.authentication.ldap.impl.LdapIdentityProvider"
user.baseDN="DC\=sickkids,DC\=ca"
user.idAttribute="sAMAccountName"
user.objectclass=[ "user" ]
```

Note that all `=` and ` ` (space) characters need to be escaped.

Don't forget to restart the application afterwards.

The full list of options can be found in [the source for `LdapProviderConfig`](https://github.com/apache/jackrabbit-oak/blob/jackrabbit-oak-1.8.8/oak-auth-ldap/src/main/java/org/apache/jackrabbit/oak/security/authentication/ldap/impl/LdapProviderConfig.java).

## Configuring through the UI

Start the application, log-in with the admin account, open http://localhost:8080/system/console/configMgr in a browser (use the correct protocol, hostname and port), edit the configuration for "Apache Jackrabbit Oak LDAP Identity Provider", and enter the right values for "LDAP Server Hostname", "Bind DN", "Bind Password" and any other fields that need to be configured.

## Other remarks

It is possible to define more than one LDAP connection, if needed, as well as multiple external identity providers besides LDAP.

LDAP authentication is enabled beside other configured authentication methods, such as local accounts in the application itself; due to implementation issues this cannot be changed.

It is also not possible to disable local accounts, since the internal `admin` account is used during startup.
