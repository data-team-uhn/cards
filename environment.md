# Environment Variables

There are various environment variables that can be set in a production environment to enable different functionalities.

### Environment variables read by CARDS
The following environment variables are read by CARDS and thus can be used in both Docker and non-Docker deployments.

| Environment Variable | Description | Sample |
| ------------- | ----------- | -----: |
| `S3_ENDPOINT_URL` | URL for an Amazon S3 endpoint to export data to | `https://sns.us-west-1.amazonaws.com` |
| `S3_ENDPOINT_REGION` | The region to use with the above for SigV4 signing of requests | `us-west-1` |
| `S3_BUCKET_NAME` | S3 bucket to export to | `uhn` |
| `AWS_KEY` | AWS access key | |
| `AWS_SECRET` | AWS secret access key | |
| `NIGHTLY_EXPORT_SCHEDULE` | Crontab-readable (Quartz Job Scheduler) schedule to perform nightly export to the configured S3 bucket | `0 0 6 * * ? *` |
| `REFERENCE_DATE` | A reference date from which all dates are to be measured from (for more details, see `DateObfuscationProcessor.java`) | `2020-01-01` |
| `COMPUTED_ANSWERS_DISABLED` | If set to `true`, computed answers are disabled | `true` |
| `CARDS_HOST_AND_PORT` | The URL to CARDS, required when emails are enabled | `localhost:8080` |
| `NIGHTLY_NOTIFICATIONS_SCHEDULE` | Crontab-readable (Quartz Job Scheduler) schedule to perform nightly notification emails | `0 0 6 * * ? *` |
| `NIGHTLY_SLACK_NOTIFICATIONS_SCHEDULE` | Crontab-readable schedule (Quartz Job Scheduler) for performing Slack performance metric update notification messages | `0 0 6 * * ? *` |
| `SLACK_PERFORMANCE_URL` | The Slack incoming webhook URL which the performance logger (`io.uhndata.cards.patients.slacknotifications`) can write its performance update messages to | `https://hooks.slack.com/services/ery8974/342rUYEiue/KJHkggI8973130DddE3r` |
| `SLACK_BACKUP_NOTIFICATIONS_URL` | The Slack incoming webhook URL which the Webhook backup task (`io.uhndata.cards.webhookbackup`) uses to log its backup task status (_started_/_completed_/_failed_) messages | `https://hooks.slack.com/services/ery8974/342rUYEiue/KJHkggI8973130DddE3r` |
| `BIOPORTAL_APIKEY` | API key [for Bioportal vocabularies](https://data.bioontology.org/documentation) | |
| `NIGHTLY_WEBHOOK_BACKUP_SCHEDULE` | Crontab-readable schedule (Quartz Job Scheduler) for performing Webhook backups of CARDS | `0 0 6 * * ? *` |
| `BACKUP_WEBHOOK_URL` | Webhook URL to perform backups of CARDS to | `http://localhost:8012` |
| `CLARITY_SQL_SERVER` | The MS-SQL server and port number to import clarity data from | `mssql:1433` |
| `CLARITY_SQL_USERNAME` | Username to use when connecting to the Clarity MS-SQL server | `sa` |
| `CLARITY_SQL_PASSWORD` | Password to use when connecting to the Clarity MS-SQL server | `testPassword_` |
| `CLARITY_SQL_SCHEMA` | The schema name to connect to | `path` |
| `CLARITY_SQL_TABLE` | The name of the table containing the Clarity data to be imported | `PatientActivity_data_for_PtExpSurveyApp` |
| `CLARITY_EVENT_TIME_COLUMN` | The column containing the event date, used for filtering and sorting | `HOSP_DISCHARGE_DTTM` |
| `CLARITY_SQL_ENCRYPT` | If true, [TLS encryption will be used for all data sent to and from](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties?view=sql-server-ver16) the Clarity MS-SQL server | `false` |

### Environment variables read by the CARDS docker container entrypoint
The following environment variables are read by the CARDS Docker container _entrypoint_ script and thus are _only_ usable in Docker-based deployments.

| Environment Variable | Description | Sample |
| ------------- | ----------- | -----: |
| `CARDS_PROJECT` | The CARDS-based project to run (eg. CARDS4LFS, CARDS4HERACLES, etc...) | `cards4proms` |
| `DEMO_BANNER` | If specified, enables the _demo banner_ on the CARDS web interface | `true` |
| `DEMO` | If specified, enables the _complete_ set of CARDS demo features (_demo banner_, _upgrade marker_, _demo forms_) | `true` |
| `DEV` | If specified, enables the _Composum_ JCR explorer. | `true` |
| `DEBUG` | If specified, starts CARDS in _debug_ mode, so that JDB can connect to the container's Java process at port `5005`. | `true` |
| `ENABLE_TEST_FEATURES` | If specified, enables the _complete_ set of CARDS _test_ Forms. | `true` |
| `SAML_AUTH_ENABLED` | If set to `true`, enables user authentication via SAML | `true` |
| `SAML_CLOUD_IAM_DEMO` | If specified, allows SAML authentication via https://lemur-15.cloud-iam.com/auth/realms/uhn-cards-test/protocol/saml. (Only useful for UHN DATA Team Developers) | `true` |
| `OAK_FILESYSTEM` | If specified, the local file system, as opposed to a Mongo database, will be used for JCR data storage. | `true` |
| `PERMISSIONS` | The _permissions mode_ to use for the data entered into CARDS | `open`, `trusted`, `ownership` |
| `EXTERNAL_MONGO_URI` | The URI of a Mongo database to use for data persistence | `mongodb.example.com:27017` |
| `MONGO_AUTH` | If specified, authenticates to the Mongo database as `<username>:<password>` | `mongouser:password` |
| `CUSTOM_MONGO_DB_NAME` | If specified, uses the specified name as the Mongo database for JCR persistence instead of the default `oak`. | `sling` |
| `SMTPS_ENABLED` | If set to `true`, enables the sending of _SMTPS_ email notifications from CARDS. | `true` |
| `ADDITIONAL_SLING_FEATURES` | If set, enables the listed Sling features. | `mvn:io.uhndata.cards/some-other-sling-feature/0.9-SNAPSHOT/slingosgifeature` |
