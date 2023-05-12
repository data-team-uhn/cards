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
    /** Cron-readable import schedule. */
    String NIGHTLY_IMPORT_SCHEDULE = "0 0 0 * * ? *";

    @AttributeDefinition(name = "Import schedule", description = "Cron-readable import schedule")
    String nightly_import_schedule() default NIGHTLY_IMPORT_SCHEDULE;

    @AttributeDefinition(name = "Day to import",
        description = "Difference from today. 0 means today, 1 means tomorrow, -1 means yesterday. "
            + "As a special value, 2147483647 (Integer.MAX_VALUE) means no date filtering, "
            + "all data found in the table will be imported. ")
    int dayToImport() default Integer.MAX_VALUE;
}
