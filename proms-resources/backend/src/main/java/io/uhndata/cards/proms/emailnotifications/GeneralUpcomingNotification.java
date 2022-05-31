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
package io.uhndata.cards.proms.emailnotifications;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Designate(ocd = GeneralUpcomingNotification.Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public final class GeneralUpcomingNotification
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralUpcomingNotification.class);

    @ObjectClassDefinition(name = "Upcoming appointment notification",
        description = "Send emails for upcoming appointments")
    public static @interface Config
    {
        @AttributeDefinition(name = "Name", description = "Name")
        String name();

        @AttributeDefinition(name = "Email Subject Line", description = "Email Subject Line")
        String emailSubject();

        @AttributeDefinition(name = "Plaintext Email Template JCR Path",
            description = "Plaintext Email Template JCR Path")
        String plainTextEmailTemplatePath();

        @AttributeDefinition(name = "HTML Email Template JCR Path", description = "HTML Email Template JCR Path")
        String htmlEmailTemplatePath();

        @AttributeDefinition(name = "Hours before upcoming visit", description = "Hours before upcoming visit")
        int hoursBeforeUpcomingVisit();
    }

    @Activate
    private void activate(final Config config)
    {
        LOGGER.info("Activating upcoming email notifications: {}", config.name());
    }

    @Deactivate
    private void deactivate(final Config config)
    {
        LOGGER.info("Deactivating upcoming email notifications: {}", config.name());
    }
}
