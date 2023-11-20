/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.s3export;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "S3 export", description = "Configuration for scheduled S3 exports")
public @interface ExportConfigDefinition
{
    /** Default value of how often the task should be invoked. */
    int FREQUENCY_IN_DAYS = 1;

    /** Cron-readable export schedule. */
    String NIGHTLY_EXPORT_SCHEDULE = "0 0 0 * * ? *";

    /** Default value of the local path where exported files should be saved to. */
    String SAVE_PATH = ".";

    /** Default value for the file name format. */
    String FILE_NAME_FORMAT = "{subject}_{period}";

    @AttributeDefinition(name = "Name", description = "Configuration name")
    String name();

    @AttributeDefinition(name = "Frequency in days", description = "Time interval (days) between task invocations")
    int frequencyInDays() default FREQUENCY_IN_DAYS;

    @AttributeDefinition(name = "Export schedule", description = "Cron-readable export schedule")
    String exportSchedule() default NIGHTLY_EXPORT_SCHEDULE;

    @AttributeDefinition(name = "Questionnaires to be exported",
        description = "List of questionnaires specified to be exported periodically, as full paths")
    String[] questionnairesToBeExported();

    @AttributeDefinition(name = "Selectors",
        description = "Optional selectors that may influence which data is exported, and how it is exported,"
            + " e.g. \".labels\" to export labels instead of raw values. Must start with a leading dot if not empty.")
    String selectors();

    @AttributeDefinition(name = "Filename format",
        description = "The format of the file name, excluding the file type extension."
            + " Special values are {subject} for the subject identifier (only if export unit type is 'subject'),"
            + " {questionnaire} for the questionnaire name (only if export unit type is 'questionnaire'),"
            + " {today} for the current date in the yyyy-mm-dd format,"
            + " {yesterday} for the date one day ago in the yyyy-mm-dd format,"
            + " {now} for the current time in the hh-mm-ss format,"
            + " {now(format string)} for the current date and time"
            + " in any format supported by DateTimeFormatter, for example {now(yyMMddHHmm)},"
            + " {yesterday(format string)} for the date and time one day ago,"
            + " {start(format string)} for the lower date and time limit being queried,"
            + " {end(format string)} for the upper date and time limit being queried,"
            + " {period} for the time period being queried in the yyyy-mm-dd_yyyy-mm-dd format.")
    String fileNameFormat() default FILE_NAME_FORMAT;

    @AttributeDefinition(name = "Export data type",
        description = "What the basic unit to export in a file is,"
            + " either modified forms as individual files,"
            + " subject with modified data,"
            + " or questionnaires with modified data."
            + " Must be one of 'form', 'subject' or 'questionnaire'.")
    String exportType() default "subject";

    @AttributeDefinition(name = "Export format",
        description = "Whether the export should be as JSON, CSV or a TSV files."
            + " Must be one of 'json', 'csv' or 'tsv',"
            + " and only the 'questionnaire' export data type supports anything other than JSON at the moment.")
    String exportFormat() default "json";

    @AttributeDefinition(name = "S3 Endpoint URL",
        description = "An URL like https://s3.server:9000 without the trailing slash.")
    String endpoint() default "%ENV%S3_ENDPOINT_URL";

    @AttributeDefinition(name = "S3 Endpoint region",
        description = "A region name, like us-west-1")
    String region() default "%ENV%S3_ENDPOINT_REGION";

    @AttributeDefinition(name = "S3 Bucket name",
        description = "The bucket to use")
    String bucket() default "%ENV%S3_BUCKET_NAME";

    @AttributeDefinition(name = "AWS access key",
        description = "Authentication access key for the S3 server")
    String accessKey() default "%ENV%AWS_KEY";

    @AttributeDefinition(name = "AWS secret key",
        description = "Authentication secret key for the S3 server")
    String secretKey() default "%ENV%AWS_SECRET";
}
