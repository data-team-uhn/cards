/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.healthcheck.internal;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Check that all the required node properties are present. The list of properties to check is defined as nodes in the
 * repository, under {@code /libs/cards/healthcheck/requiredProperties/}, with the {@code propertyPath} property holding
 * the full JCR path to a property, and an optional {@code requiredValue} property holding the expected value for the
 * property, compared through its string representation. If the required value is not set, only the property's
 * existence is checked. Other CARDS modules should provide the actual required properties to check for.
 *
 * @version $Id$
 * @since 0.9.37
 */
@Component(service = HealthCheck.class, property = { HealthCheck.TAGS + "=cards",
    HealthCheck.NAME + "=CARDS properties present" }, immediate = true)
public class PropertiesPresentHealthCheck implements HealthCheck
{
    /** JCR node where all the configurations are stored. */
    public static final String CONFIGURATION_PATH = "/libs/cards/healthcheck/requiredProperties";

    /** The name of the configuration node property holding the path to the property to check. */
    public static final String PATH_PROPERTY = "propertyPath";

    /** The name of the configuration node property holding the required value to check. */
    public static final String VALUE_PROPERTY = "requiredValue";

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesPresentHealthCheck.class);

    @Reference
    private ResourceResolverFactory rrf;

    @Override
    public Result execute()
    {
        final FormattingResultLog result = new FormattingResultLog();
        int present = 0;
        int missing = 0;
        try (ResourceResolver resolver =
            this.rrf.getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "healthcheck"))) {
            final Session session = resolver.adaptTo(Session.class);
            if (session == null) {
                result.healthCheckError("The resource resolver is not backed by a JCR session");
                return new Result(result);
            }
            if (!session.nodeExists(CONFIGURATION_PATH)) {
                result.warn("No required properties configuration, please check the system integrity");
                return new Result(result);
            }
            final NodeIterator configurations = session.getNode(CONFIGURATION_PATH).getNodes();
            while (configurations.hasNext()) {
                final Node configuration = configurations.nextNode();
                try {
                    if (checkProperty(configuration, session, result)) {
                        present++;
                    } else {
                        missing++;
                    }
                } catch (RepositoryException e) {
                    LOGGER.error("Unexpected exception while checking required properties: {}", e.getMessage(), e);
                    result.healthCheckError("Cannot run status check: {}", e.getMessage(), e);
                }
            }
        } catch (LoginException | RepositoryException e) {
            result.healthCheckError("Healthcheck module not set up properly: {}", e.getMessage());
        }
        result.info("{} required properties present" + (missing != 0 ? " and {} wrong/missing" : ""), present, missing);
        return new Result(result);
    }

    /**
     * Runs one configured check: the property must exist, and, when the configuration specifies a required value,
     * its value must match it.
     *
     * @param configuration the configuration node describing the check
     * @param session the session to read the checked property with
     * @param result the result log to report into
     * @return {@code true} if the property is present and correct
     * @throws RepositoryException if accessing the configuration or the checked property fails unexpectedly
     */
    private boolean checkProperty(final Node configuration, final Session session, final FormattingResultLog result)
        throws RepositoryException
    {
        final String propertyPath = configuration.getProperty(PATH_PROPERTY).getString();
        if (!session.propertyExists(propertyPath)) {
            result.critical("Required property not found: {}", propertyPath);
            return false;
        }
        if (!configuration.hasProperty(VALUE_PROPERTY)) {
            result.debug("Required property exists: {}", propertyPath);
            return true;
        }

        // Compare string representations, so that a required value configured as a string can match an actual
        // property of another type, e.g. a boolean or a long
        final String requiredValue = configuration.getProperty(VALUE_PROPERTY).getString();
        final String actualValue = session.getProperty(propertyPath).getString();
        if (!requiredValue.equals(actualValue)) {
            result.critical("Wrong value for required property {}: expected {}, got {}",
                propertyPath, requiredValue, actualValue);
            return false;
        }
        result.debug("Required property is correct: {}={}", propertyPath, actualValue);
        return true;
    }
}
