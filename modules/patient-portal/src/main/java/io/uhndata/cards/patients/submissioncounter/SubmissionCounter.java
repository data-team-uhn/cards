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
package io.uhndata.cards.patients.submissioncounter;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventListener;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;

@Designate(ocd = SubmissionCounter.Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public final class SubmissionCounter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionCounter.class);

    // The data type for the "is submitted" question can only ever be cards:BooleanAnswer
    private static final String[] MONITORED_JCR_NODE_TYPES = {
        "cards:BooleanAnswer"
    };

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private FormUtils formUtils;

    private ResourceResolver resolver;

    private Session session;

    @ObjectClassDefinition(name = "Submission Counter",
        description = "Configuration for a surveys submitted performance counter")
    public @interface Config
    {
        @AttributeDefinition(name = "Name", description = "Name")
        String name() default "SurveysSubmitted";

        @AttributeDefinition(name = "Submitted Flag Path",
            description = "A question that marks a form as submitted.")
        String submittedFlagPath() default "/Questionnaires/Visit information/surveys_submitted";

        @AttributeDefinition(name = "Linking Subject Type", description = "Subject type that links the Form with the"
            + " \"submit\" button to the submitted Forms")
        String linkingSubjectType() default "/SubjectTypes/Patient/Visit";

        @AttributeDefinition(name = "Excluded Questionnaires", description = "Do not count any Forms which are built"
            + " from any of these types of Questionnaires")
        String[] excludedQuestionnaires() default {};
    }

    @Activate
    private void activate(final Config config)
    {
        LOGGER.info("ACTIVATING SubmissionCounter with name: {}", config.name());

        try {
            Map<String, Object> params = new HashMap<>();
            params.put(ResourceResolverFactory.SUBSERVICE, "EmailNotifications");
            this.resolver = this.resolverFactory.getServiceResourceResolver(params);

            Map<String, Object> listenerParams = new HashMap<>();
            listenerParams.put("submissionCounterName", config.name());
            listenerParams.put("submittedFlagPath", config.submittedFlagPath());
            listenerParams.put("linkingSubjectType", config.linkingSubjectType());
            listenerParams.put("excludedQuestionnairePaths", config.excludedQuestionnaires());
            EventListener myEventListener = new SubmissionEventListener(this.formUtils, this.resolverFactory,
                this.resolver, listenerParams);

            this.session = this.resolver.adaptTo(Session.class);
            this.session.getWorkspace().getObservationManager().addEventListener(
                myEventListener,
                Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED,
                "/Forms",
                true,
                null,
                MONITORED_JCR_NODE_TYPES,
                false);
        } catch (Exception e) {
            LOGGER.warn("Failed to register SubmissionCounter event handler: {}", e.getMessage());
        }
    }

    @Deactivate
    private void deactivate()
    {
        if (this.session != null) {
            this.session.logout();
        }
        if (this.resolver != null) {
            this.resolver.close();
        }
    }
}
