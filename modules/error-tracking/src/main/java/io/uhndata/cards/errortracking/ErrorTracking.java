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

package io.uhndata.cards.errortracking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ErrorTracking
{
    private static final String LOGGED_EVENTS_PATH = "/LoggedEvents/";

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorTracking.class);

    // Hide the utility class constructor
    private ErrorTracking()
    {
    }

    /*
     * Stores in the JCR, under /LoggedEvents, a nt:file node containing the passed stack trace.
     *
     * @param resolverFactory a ResourceResolverFactory the can be used for obtaining a ResourceResolver
     * with permissions to add child nodes to /LoggedEvents.
     * @param stackTrace the String containing the stack trace of the error that was thrown resulting
     * in the calling of this method
     */
    public static void logError(final ResourceResolverFactory resolverFactory, final String stackTrace)
    {
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(null)) {
            Resource eventsFolderResource = resolver.getResource(LOGGED_EVENTS_PATH);
            if (eventsFolderResource == null) {
                return;
            }

            // Create the nt:file node under /LoggedEvents and get a reference to it
            final String newFileName = UUID.randomUUID().toString();
            final Map<String, Object> eventNodeProperties = new HashMap<>();
            eventNodeProperties.put("jcr:primaryType", "nt:file");
            resolver.create(eventsFolderResource, newFileName, eventNodeProperties);
            Resource thisEventResource = resolver.getResource(LOGGED_EVENTS_PATH + newFileName);
            if (thisEventResource == null) {
                return;
            }
            final Map<String, Object> jcrContentProperties = new HashMap<>();
            jcrContentProperties.put("jcr:primaryType", "nt:resource");
            jcrContentProperties.put("jcr:mimeType", "text/plain");
            jcrContentProperties.put("jcr:data", stackTrace);
            resolver.create(thisEventResource, "jcr:content", jcrContentProperties);

            // Commit these changes to JCR
            resolver.commit();
        } catch (LoginException | PersistenceException e) {
            LOGGER.error("logError failed.", e);
            return;
        }
    }
}
