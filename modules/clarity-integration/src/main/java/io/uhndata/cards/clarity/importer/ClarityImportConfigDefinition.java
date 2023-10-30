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

package io.uhndata.cards.clarity.importer;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Clarity import",
    description = "Configuration for the Clarity importer")
public @interface ClarityImportConfigDefinition
{
    /** Cron-readable schedule. */
    String NIGHTLY = "0 0 0 * * ? *";

    @AttributeDefinition(name = "Name", description = "Configuration name")
    String name();

    @AttributeDefinition(name = "Import schedule", description = "Quartz-readable import schedule")
    String importSchedule() default NIGHTLY;

    @AttributeDefinition(name = "Day to import",
        description = "Difference from today. 0 means today, 1 means tomorrow, -1 means yesterday. "
            + "As a special value, 2147483647 (Integer.MAX_VALUE) means no date filtering, "
            + "all data found in the table will be imported. ")
    int dayToImport() default Integer.MAX_VALUE;

    @AttributeDefinition(name = "Server")
    String server() default "%ENV%CLARITY_SQL_SERVER";

    @AttributeDefinition(name = "Username")
    String username() default "%ENV%CLARITY_SQL_USERNAME";

    @AttributeDefinition(name = "Password")
    String password() default "%ENV%CLARITY_SQL_PASSWORD";

    @AttributeDefinition(name = "Encrypted connection", description = "Use either true or false")
    String encrypt() default "%ENV%CLARITY_SQL_ENCRYPT";

    @AttributeDefinition(name = "Schema")
    String schemaName() default "%ENV%CLARITY_SQL_SCHEMA";

    @AttributeDefinition(name = "Table")
    String tableName() default "%ENV%CLARITY_SQL_TABLE";

    @AttributeDefinition(name = "Date column", description = "An (optional) Clarity column to use for date filtering",
        required = false)
    String dateColumn() default "%ENV%CLARITY_EVENT_TIME_COLUMN";

    @AttributeDefinition(name = "Column mapping", description = "Full path to the clarity mapping node")
    String mapping() default "/apps/cards/clarityImport";
}
