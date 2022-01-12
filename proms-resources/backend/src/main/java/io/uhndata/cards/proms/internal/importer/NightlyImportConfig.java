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

package io.uhndata.cards.proms.internal.importer;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Nightly PROMs Import Config",
  description = "Configuration for the PROMs nightly importer")
public @interface NightlyImportConfig
{
    /** Number of days to look ahead when querying for appointments. */
    int DAYS_TO_QUERY = 3;

    /** Torch FHIR GraphQL endpoint. */
    String TORCH_ENDPOINT_URL = "https://prom.dev.uhn.io/graphql";

    /** Vault JWT refresh endpoint. */
    String VAULT_AUTH_URL = "https://vault.dev.uhn.io/v1/auth/jwt/login";

    /** Vault JWT token. */
    String VAULT_TOKEN = "";

    /** Cron-readable import schedule. */
    String NIGHTLY_IMPORT_SCHEDULE = "";

    @AttributeDefinition(name = "Import schedule",
        description = "Cron-readable import schedule")
    String nightly_import_schedule() default NIGHTLY_IMPORT_SCHEDULE;

    @AttributeDefinition(type = AttributeType.INTEGER, name = "days to query",
        description = "Number of days of appointments to query ahead of schedule")
    int days_to_query() default DAYS_TO_QUERY;

    @AttributeDefinition(name = "endpoint URL")
    String endpoint_url() default TORCH_ENDPOINT_URL;

    @AttributeDefinition(name = "authentication URL")
    String auth_url() default VAULT_AUTH_URL;

    @AttributeDefinition(name = "Vault token")
    String vault_token() default VAULT_TOKEN;
}
