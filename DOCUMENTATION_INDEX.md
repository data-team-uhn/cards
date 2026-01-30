# CARDS Documentation Index

> **CARDS - Clinical Archive for Data Science**
> A comprehensive web-based tool for collecting, managing, and exporting standardized medical data.
> Built on Apache Sling | Version 0.9.37-SNAPSHOT | Apache License 2.0

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Architecture Overview](#architecture-overview)
3. [Module Reference](#module-reference)
4. [API Reference](#api-reference)
5. [Configuration Guide](#configuration-guide)
6. [Development Guide](#development-guide)
7. [Deployment Guide](#deployment-guide)
8. [Existing Documentation](#existing-documentation)

---

## Quick Start

### Prerequisites
- Java 11+
- Maven 3.8+
- Python 2.5+ or 3.0+ (with psutil module recommended)
- Docker (optional, for containerized deployment)

### Build & Run

```bash
# Clone and build
git clone https://github.com/data-team-uhn/cards.git
cd cards
mvn clean install

# Run locally
./start_cards.sh

# Access at http://localhost:8080
# Default credentials: admin/admin
```

### Quick Build Options

| Command | Purpose |
|---------|---------|
| `mvn install -Pquick` | Skip tests for faster builds |
| `mvn install -Pskip-webpack` | Skip frontend rebuild |
| `mvn install -PautoInstallBundle` | Hot-deploy to running instance |
| `mvn install -Pdocker` | Build Docker image |

---

## Architecture Overview

### Technology Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 11+, Apache Sling 2.27.0 |
| **Data Store** | Apache Jackrabbit Oak 1.44.0 (JCR) |
| **Frontend** | React 19.0.0, TypeScript, Material-UI |
| **Build** | Maven (multi-module), Webpack, Yarn |
| **Container** | Docker, Docker-Compose |

### Data Model

```
SubjectType (Patient, Visit, etc.)
    └── Subject (individual patient/visit instance)
         └── Form (answers to questionnaires)
              └── Answer / AnswerSection

Questionnaire (definition)
    └── Section / Question (hierarchical structure)
```

### Key Concepts

- **Subject Types**: Define entity types (Patient → Visit hierarchy)
- **Subjects**: Individual entities that can have forms attached
- **Questionnaires**: Question definitions organized in sections
- **Forms**: Collected answers linked to subjects and questionnaires
- **Status Flags**: INCOMPLETE, INVALID, DRAFT, SUBMITTED

### Storage Backends

| Backend | Use Case | Configuration |
|---------|----------|---------------|
| **Filesystem (Oak Segment)** | Development, single-node | `OAK_FILESYSTEM=true` |
| **MongoDB (Oak Document)** | Production, clustered | `EXTERNAL_MONGO_URI=host:port` |

### Permission Schemes

| Scheme | Description | Flag |
|--------|-------------|------|
| `open` | All registered users can create/view/edit | `--permissions open` |
| `trusted` | Only TrustedUsers group members | `--permissions trusted` |
| `ownership` | Users own records they create | `--permissions ownership` |

---

## Module Reference

### Core Modules

| Module | Path | Description |
|--------|------|-------------|
| **data-model** | `modules/data-model/` | Forms, Questionnaires, Subjects, Subject Types definitions |
| **data-entry** | `modules/data-entry/` | Web UI for data collection and form submission |
| **commons** | `modules/commons/` | Shared utilities, React components, serializers |
| **export** | `modules/export/` | Multi-format export (JSON, CSV, TSV, Markdown, TXT) |

### Authentication & Authorization

| Module | Path | Description |
|--------|------|-------------|
| **login** | `modules/login/` | Basic authentication |
| **saml-support** | `modules/saml-support/` | SAML/SSO authentication support |
| **saml** | `modules/saml/` | SAML implementation |
| **uhn-saml** | `modules/uhn-saml/` | UHN-specific SAML configuration |
| **ldap-support** | `modules/ldap-support/` | LDAP directory integration |
| **token-authentication** | `modules/token-authentication/` | Token-based auth |
| **permissions** | `modules/permissions/` | Base permission framework |
| **permissions-open** | `modules/permissions-open/` | Open access scheme |
| **permissions-trusted** | `modules/permissions-trusted/` | Trusted users scheme |
| **permissions-ownership** | `modules/permissions-ownership/` | Ownership-based scheme |
| **permissions-unsubmitted** | `modules/permissions-unsubmitted/` | Unsubmitted forms control |

### User Interface

| Module | Path | Description |
|--------|------|-------------|
| **patient-portal** | `modules/patient-portal/` | Patient-facing interface |
| **homepage** | `modules/homepage/` | Main landing page |
| **ui-extension** | `modules/ui-extension/` | Plugin/extension mechanism |
| **pedigree** | `modules/pedigree/` | Pedigree visualization |
| **favicon** | `modules/favicon/` | Application favicon |
| **demo-banner** | `modules/demo-banner/` | Demo environment banner |
| **downtime-warning-banner** | `modules/downtime-warning-banner/` | Maintenance notifications |

### Integration & Export

| Module | Path | Description |
|--------|------|-------------|
| **s3-export** | `modules/s3-export/` | AWS S3 scheduled exports |
| **clarity-integration** | `modules/clarity-integration/` | EHR Clarity system import |
| **torch-import** | `modules/torch-import/` | Data import functionality |
| **webhook-backup** | `modules/webhook-backup/` | External webhook integration |
| **google-apis** | `modules/google-apis/` | Google Maps/Places services |
| **vocabularies** | `modules/vocabularies/` | BioPortal ontology integration |

### Notifications & Communication

| Module | Path | Description |
|--------|------|-------------|
| **email-notifications** | `modules/email-notifications/` | Email system |
| **slack-notifications** | `modules/slack-notifications/` | Slack integration |

### Monitoring & Operations

| Module | Path | Description |
|--------|------|-------------|
| **metrics** | `modules/metrics/` | Performance monitoring |
| **statistics** | `modules/statistics/` | Analytics and reporting |
| **error-tracking** | `modules/error-tracking/` | Error tracking |
| **locking** | `modules/locking/` | Form locking/sign-off workflows |
| **form-completion-status** | `modules/form-completion-status/` | Form status tracking |

### Utilities & Support

| Module | Path | Description |
|--------|------|-------------|
| **utils** | `modules/utils/` | General utilities |
| **http-requests** | `modules/http-requests/` | HTTP client utilities |
| **resolver-provider** | `modules/resolver-provider/` | Resource resolution |
| **startup-customization** | `modules/startup-customization/` | Custom initialization hooks |
| **versioning** | `modules/versioning/` | Version management |
| **principals** | `modules/principals/` | User principal management |
| **variants** | `modules/variants/` | Genetic variant handling |

---

## API Reference

### REST Endpoints

CARDS uses Apache Sling's REST-based architecture. Resources are accessed via their JCR paths.

#### Query Endpoint

```
GET /query?query=<JCR-SQL2>&limit=<n>&offset=<n>
```

Parameters:
- `query`: JCR-SQL2 query string
- `limit`: Number of results (default: 10)
- `offset`: Pagination offset (default: 0)
- `rawResults=true`: Return raw column data
- `showTotalRows=true`: Include complete count

Example:
```
/query?query=select * from [cards:Questionnaire] as n where contains(n.'title', 'Information')&limit=5
```

See: [doc/JQL_queries.md](doc/JQL_queries.md)

#### Pagination Endpoint

```
GET /<ResourceType>.paginate?limit=<n>&offset=<n>
```

Parameters:
- `limit`, `offset`: Pagination
- `descending=true`: Newest first
- `includeallstatus=true`: Include incomplete forms
- `filternames`, `filtercomparators`, `filtervalues`, `filtertypes`: Answer filtering

Examples:
```
/Questionnaires.paginate?resourceSelectors=deep
/Forms.paginate?includeallstatus=true&limit=100
```

See: [doc/Resource_pagination.md](doc/Resource_pagination.md)

#### Export Endpoints

```
GET /Path/To/Data.<processors>.dataFilter:<filters>.<format>
```

Formats: `.json`, `.csv`, `.tsv`, `.txt`, `.md`

Notable paths:
- `/Questionnaires/<ID>.json` - Questionnaire metadata
- `/Questionnaires/<ID>.deep.json` - Full questionnaire with questions
- `/Questionnaires/<ID>.data.json` - Forms answering the questionnaire
- `/Forms/<ID>.deep.json` - Form with all answers
- `/Subjects/<MRN>/<Encounter>.data.deep.json` - Visit data

See: [doc/Serialization.md](doc/Serialization.md)

### Common Processors

| Processor | Description |
|-----------|-------------|
| `.deep` | Include full hierarchy |
| `.bare` | Minimal output, no metadata |
| `.labels` | Human-readable answer values |
| `.-identify` | Disable identification (prefix `-` disables) |
| `.answerFilter:include=<path>` | Filter included answers |

### Common Filters

| Filter | Description |
|--------|-------------|
| `.dataFilter:modifiedAfter=<date>` | Modified since date |
| `.dataFilter:modifiedBefore=<date>` | Modified before date |
| `.dataFilter:status=SUBMITTED` | Filter by status flag |
| `.questionnaireFilter:exclude=<path>` | Exclude questions |

---

## Configuration Guide

### Environment Variables

#### Core Configuration

| Variable | Description | Example |
|----------|-------------|---------|
| `OAK_FILESYSTEM` | Use filesystem storage | `true` |
| `EXTERNAL_MONGO_URI` | MongoDB connection URI | `mongodb.example.com:27017` |
| `MONGO_AUTH` | MongoDB credentials | `user:password` |
| `PERMISSIONS` | Permission scheme | `open`, `trusted`, `ownership` |

#### External Services

| Variable | Description |
|----------|-------------|
| `BIOPORTAL_APIKEY` | BioPortal API key for vocabularies |
| `GOOGLE_APIKEY` | Google Maps/Places API key |
| `CARDS_HOST_AND_PORT` | Public URL for emails |

#### AWS S3 Export

| Variable | Description |
|----------|-------------|
| `S3_ENDPOINT_URL` | S3 endpoint URL |
| `S3_ENDPOINT_REGION` | S3 region |
| `S3_BUCKET_NAME` | Target bucket |
| `AWS_KEY` | Access key |
| `AWS_SECRET` | Secret key |
| `NIGHTLY_EXPORT_SCHEDULE` | Cron schedule |

#### Clarity Integration

| Variable | Description |
|----------|-------------|
| `CLARITY_SQL_SERVER` | MS-SQL server:port |
| `CLARITY_SQL_USERNAME` | Database username |
| `CLARITY_SQL_PASSWORD` | Database password |
| `CLARITY_SQL_SCHEMA` | Schema name |
| `CLARITY_SQL_TABLE` | Table name |

#### Notifications

| Variable | Description |
|----------|-------------|
| `SMTPS_ENABLED` | Enable email (`true`) |
| `NIGHTLY_NOTIFICATIONS_SCHEDULE` | Email cron schedule |
| `SLACK_PERFORMANCE_URL` | Slack webhook URL |

See: [environment.md](environment.md) for complete reference.

### Startup Options

```bash
./start_cards.sh [options]
```

| Option | Description |
|--------|-------------|
| `-p PORT` | Custom port (default: 8080) |
| `--permissions SCHEME` | Permission scheme |
| `--dev` | Enable Composum browser |
| `--test` | Include test questionnaires |
| `--demo` | Show demo banner |
| `--clarity` | Enable Clarity integration |
| `--locking` | Enable form locking |
| `--mongo` | Use MongoDB storage |
| `--debug` | Enable remote debugging (port 5005) |

---

## Development Guide

### Project Structure

```
cards/
├── modules/                    # 48 OSGi modules
│   ├── data-model/            # Core data types
│   ├── data-entry/            # Main UI
│   ├── commons/               # Shared code
│   └── ...
├── aggregated-frontend/       # React frontend
│   └── src/main/frontend/     # Node.js project
├── distribution/              # Sling feature packaging
├── test-resources/            # Test questionnaires
├── tests/                     # Integration tests
├── Utilities/                 # Scripts and tools
└── doc/                       # Documentation
```

### Build Profiles

| Profile | Purpose |
|---------|---------|
| `-Pquick` | Skip tests |
| `-Pclean-node` | Clean frontend |
| `-Pclean-instance` | Clean Sling data |
| `-Pskip-webpack` | Skip frontend build |
| `-PautoInstallBundle` | Hot deploy to running instance |
| `-PintegrationTests` | Run integration tests |
| `-Ptests` | Enable unit tests |
| `-Pdocker` | Build Docker image |

### Hot Deployment

Deploy changes to a running instance:

```bash
mvn install -PautoInstallBundle -Dsling.url=http://localhost:8080/system/console
```

### Frontend Development

```bash
cd aggregated-frontend/src/main/frontend
yarn install
yarn build
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -PintegrationTests
```

### Content Browser (Dev Mode)

Start with `--dev` flag, then access:
```
http://localhost:8080/bin/browser.html
```

---

## Deployment Guide

### Docker (Development)

```bash
# Build image
mvn clean install -Pdocker

# Run with filesystem storage
docker run --rm -e OAK_FILESYSTEM=true -p 127.0.0.1:8080:8080 -it cards/cards
```

### Docker (Production)

```bash
# Create network and MongoDB
docker network create cardsbridge
docker run --rm --network cardsbridge --name mongo -d mongo

# Create persistent volume
docker volume create --label server=production cards-production-volume

# Run CARDS
docker container run --rm --network cardsbridge --detach \
  --volume cards-production-volume:/opt/cards/sling/ \
  -p 8080:8080 --name cards-production cards/cards
```

### Docker-Compose

For clustered MongoDB with sharding and replication:

1. Build the image: `mvn clean install -Pdocker`
2. Clone [cards-deploy-tool](https://github.com/data-team-uhn/cards-deploy-tool)
3. Generate compose file:
   ```bash
   python3 generate_compose_yaml.py --mongo_cluster --shards 2 --replicas 3 \
     --cards_docker_image cards/cards:latest
   ```
4. Start: `docker-compose up -d`

### Self-Contained Docker Image

```bash
cd Utilities/Packaging/Docker
./build_self_contained.sh cards/cards:1.0.0
```

---

## Existing Documentation

| Document | Location | Description |
|----------|----------|-------------|
| **README** | [README.md](README.md) | Build, run, and Docker instructions |
| **Environment Variables** | [environment.md](environment.md) | Complete environment variable reference |
| **Data Serialization** | [doc/Serialization.md](doc/Serialization.md) | Export formats, processors, filters |
| **JQL Queries** | [doc/JQL_queries.md](doc/JQL_queries.md) | Query language reference |
| **Resource Pagination** | [doc/Resource_pagination.md](doc/Resource_pagination.md) | Pagination servlet documentation |

---

## Additional Resources

### Source Code References

- **Node Types (CND)**: Search `*.cnd` files for JCR node type definitions
- **OSGi Components**: Look for `@Component` annotations in Java code
- **React Components**: Located in `aggregated-frontend/src/main/frontend/src/`

### External Documentation

- [Apache Sling](https://sling.apache.org/documentation.html)
- [Apache Jackrabbit Oak](https://jackrabbit.apache.org/oak/docs/)
- [JCR-SQL2 Query Grammar](https://jackrabbit.apache.org/oak/docs/query/grammar-sql2.html)
- [JCR Node Type Notation](https://jackrabbit.apache.org/jcr/node-type-notation.html)

### Support

- GitHub Issues: https://github.com/data-team-uhn/cards/issues
- Organization: DATA Team at UHN (University Health Network)

---

*Generated: 2026-01-30 | CARDS v0.9.37-SNAPSHOT*
