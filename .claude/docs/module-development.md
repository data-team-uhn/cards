# Module Development

## Creating a New Module

1. Create directory under `modules/`
2. Add `pom.xml` with bundle packaging
3. Register in parent `modules/pom.xml`
4. Create Sling feature file if needed

## Module POM Template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.uhndata.cards</groupId>
        <artifactId>cards-modules</artifactId>
        <version>0.9.37-SNAPSHOT</version>
    </parent>

    <artifactId>cards-my-module</artifactId>
    <packaging>bundle</packaging>
    <name>CARDS - My Module</name>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.felix</groupId>
                <artifactId>maven-bundle-plugin</artifactId>
                <extensions>true</extensions>
            </plugin>
        </plugins>
    </build>
</project>
```

## Sling Features

Features aggregate bundles for deployment. Located in `distribution/src/main/features/`.

Feature JSON structure:
```json
{
    "id": "io.uhndata.cards:my-feature:slingosgifeature:1.0.0",
    "bundles": [
        {"id": "io.uhndata.cards:cards-my-module:1.0.0"}
    ]
}
```

## Hot Deployment

Deploy to running instance:
```bash
mvn install -PautoInstallBundle -pl modules/my-module
```

## Module Categories

| Category | Purpose | Examples |
|----------|---------|----------|
| Core | Data model, entry | data-model, data-entry |
| Auth | Authentication | login, saml-support, ldap-support |
| Permissions | Access control | permissions-*, ownership |
| Integration | External systems | clarity-integration, s3-export |
| UI | Interface features | patient-portal, pedigree |
