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
package io.uhndata.cards.export;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Data export", description = "Configuration for periodic or triggered data exports")
public @interface ExportConfigDefinition
{
    /** Default value of how often the task should be invoked. */
    int FREQUENCY_IN_DAYS = 1;

    /** Cron-readable export schedule. */
    String NIGHTLY_EXPORT_SCHEDULE = "0 0 0 * * ? *";

    /** Default value for the file name format. */
    String FILE_NAME_FORMAT = "{resourceName}_{period}";

    @AttributeDefinition(name = "Name", description = "Configuration name")
    String name();

    @AttributeDefinition(name = "Frequency in days", description = "Time interval (days) between task invocations.")
    int frequencyInDays() default FREQUENCY_IN_DAYS;

    @AttributeDefinition(name = "Export schedule",
        description = "Cron-readable export schedule."
            + " If empty, the environment variable NIGHTLY_EXPORT_SCHEDULE is used."
            + " If this is a job that can only be invoked manually, use \"none\".")
    String exportSchedule() default NIGHTLY_EXPORT_SCHEDULE;

    @AttributeDefinition(name = "Data Retriever",
        description = "The name of the data retriever to use."
            + " A data retriever is responsible for identifying which resources need to be exported by running a query."
            + " The accepted values depend on the available implementations of the DataRetriever service.")
    String retriever();

    @AttributeDefinition(name = "Data Retriever parameters",
        description = "Optional extra parameters to pass to the data retriever."
            + " The expected values depend on the selected data retriever,"
            + " for example it could be a list of questionnaire or subject type paths.")
    String[] retrieverParameters();

    @AttributeDefinition(name = "Data Formatter",
        description = "The name of the data formatter to use."
            + " A data formatter is responsible for turning the list of identifiers retrieved by the Data Retriever"
            + " into files of a certain format, for example JSON, CSV, or binary files."
            + " The accepted values depend on the available implementations of the DataFormatter service.")
    String formatter();

    @AttributeDefinition(name = "Data Formatter parameters",
        description = "Optional extra parameters to pass to the data formatter."
            + " The expected values depend on the selected data formatter,"
            + " for example it could be extra selectors to pass to the resource to influence its JSON serialization.")
    String[] formatterParameters();

    @AttributeDefinition(name = "Filename format",
        description = "The format of the file name, including the file type extension."
            + " Special values are: "
            + " {resourceIdentifier} for a unique identifier for the item being exported,"
            + " such as the Subject identifier or the Questionnaire title,"
            + " automatically detecting which type of item is exported,"
            + " {resourceName} for the node name of the item being exported,"
            + " {resourcePath} for the full path of the item being exported with / replaced by _,"
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

    @AttributeDefinition(name = "Storage",
        description = "The name of the storage to use."
            + " This is specified in the configuration for specific implementations of the DataStore service.")
    String storage();

    @AttributeDefinition(name = "Data Storage parameters",
        description = "Optional extra parameters to pass to the data storage."
            + " The expected values depend on the selected data storage,"
            + " for example it could be a path to a directory on the filesystem,"
            + " or connection details for an outside storage.")
    String[] storageParameters();
}
