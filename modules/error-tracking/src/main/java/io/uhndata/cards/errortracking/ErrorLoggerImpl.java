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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, service = ErrorLoggerService.class)
public final class ErrorLoggerImpl implements ErrorLoggerService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorLoggerImpl.class);

    private static final String LOGGED_EVENTS_PATH = "/LoggedEvents/";

    @Reference
    private ResourceResolverFactory rrf;

    @Activate
    protected void activate(ComponentContext componentContext) throws Exception
    {
        LOGGER.warn("ErrorTracking IS ACTIVATING!");
        ErrorLogger.setService(this);
    }

    /*
     * Stores in the JCR, under /LoggedEvents, a nt:file node containing the passed stack trace.
     *
     * @param loggedError the Throwable containing the stack trace of the error that was thrown resulting
     * in the calling of this method
     */
    public void logError(final Throwable loggedError)
    {
        try (ResourceResolver resolver = this.rrf.getServiceResourceResolver(null)) {
            if (resolver == null) {
                return;
            }
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

            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            loggedError.printStackTrace(pw);

            final Map<String, Object> jcrContentProperties = new HashMap<>();
            jcrContentProperties.put("jcr:primaryType", "nt:resource");
            jcrContentProperties.put("jcr:mimeType", "text/plain");
            jcrContentProperties.put("jcr:data", sw.toString());
            resolver.create(thisEventResource, "jcr:content", jcrContentProperties);

            // Commit these changes to JCR
            resolver.commit();
        } catch (LoginException | PersistenceException e) {
            LOGGER.error("logError failed.", e);
            return;
        }
    }
}
