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

package io.uhndata.cards.auth.token.impl;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

import javax.jcr.query.Query;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpiredTokensCleanupTask implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpiredTokensCleanupTask.class);

    private final ResourceResolverFactory rrf;

    ExpiredTokensCleanupTask(final ResourceResolverFactory rrf)
    {
        this.rrf = rrf;
    }

    @Override
    public void run()
    {
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
            final Iterator<Resource> resources = resolver.findResources("SELECT * FROM [cards:Token] WHERE ["
                + CardsTokenImpl.TOKEN_ATTRIBUTE_EXPIRY + "] < '"
                + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")) + "'",
                Query.JCR_SQL2);
            resources.forEachRemaining(token -> {
                try {
                    resolver.delete(token);
                } catch (final PersistenceException e) {
                    LOGGER.warn("Failed to delete expired token {}: {}", token.getPath(), e.getMessage());
                }
            });
            resolver.commit();
        } catch (final LoginException e) {
            LOGGER.warn("Invalid setup, service rights not set up for the expired tokens cleanup task");
        } catch (final PersistenceException e) {
            LOGGER.warn("Failed to delete expired tokens: {}", e.getMessage());
        }
    }
}
