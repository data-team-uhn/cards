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

package io.uhndata.cards.torch.internal;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "PROMs import",
    description = "Configuration for the PROMs importer")
public @interface ImportConfigDefinition
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
    String NIGHTLY_IMPORT_SCHEDULE = "0 0 0 * * ? *";

    /** Vault role to login to. */
    String VAULT_ROLE = "prom_role";

    /** Allowed provider roles. */
    String PROVIDER_ROLE = "ATND";

    @AttributeDefinition(name = "Name", description = "Configuration name")
    String name();

    @AttributeDefinition(name = "Import schedule", description = "Cron-readable import schedule")
    String nightly_import_schedule() default NIGHTLY_IMPORT_SCHEDULE;

    @AttributeDefinition(type = AttributeType.INTEGER, name = "days to query",
        description = "Number of days of appointments to query ahead of schedule")
    int days_to_query() default DAYS_TO_QUERY;

    @AttributeDefinition(name = "Endpoint URL", description = "The Torch endpoint to query")
    String endpoint_url() default TORCH_ENDPOINT_URL;

    @AttributeDefinition(name = "Authentication URL",
        description = "The Vault URL to query for an auth token for accessing Torch")
    String auth_url() default VAULT_AUTH_URL;

    @AttributeDefinition(name = "Vault token", description = "Authentication token for accessing Vault")
    String vault_token() default VAULT_TOKEN;

    @AttributeDefinition(name = "Clinic names", description = "The clinic names to query")
    String[] clinic_names();

    @AttributeDefinition(name = "Provider names",
        description = "List of names of providers to query. If empty, all providers will be fetched.", required = false)
    String[] provider_names();

    @AttributeDefinition(name = "Allowed provider roles",
        description = "If set along with the provider names attribute, only take appointments if the provider is"
            + " one of the given roles. The most useful role to filter by is ATND for an attending physician.",
        required = false)
    String[] allowed_roles() default PROVIDER_ROLE;

    @AttributeDefinition(name = "Vault role name",
        description = "Name of the role to login to Vault with. If not given, skip the Vault login process.",
        required = false)
    String vault_role();

    @AttributeDefinition(name = "Dates to query",
        description = "If set, only appointments for these dates will be imported. Must be in the format yyyy-mm-dd.",
        required = false)
    String[] dates_to_query();
}
