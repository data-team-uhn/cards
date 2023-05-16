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
package io.uhndata.cards.patients.emailnotifications;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventListener;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.messaging.mail.MailService;
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

@Designate(ocd = EmailFormAlerts.Config.class, factory = true)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public final class EmailFormAlerts
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailFormAlerts.class);

    // The data type for the "is submitted" question can only ever be cards:BooleanAnswer
    private static final String[] MONITORED_JCR_NODE_TYPES = {
        "cards:BooleanAnswer"
    };

    @Reference
    private MailService mailService;

    /** Provides access to resources. */
    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private FormUtils formUtils;

    private ResourceResolver resolver;

    private Session session;

    @ObjectClassDefinition(name = "Email Alerts",
        description = "Configuration for triggering an email alert based on the patient's responses")
    public @interface Config
    {
        @AttributeDefinition(name = "Name", description = "Name")
        String name() default "PatientHealthAlert001";

        @AttributeDefinition(name = "Submitted Flag Path",
            description = "A question that marks a form as submitted. Unsubmitted forms do not trigger an alert.")
        String submittedFlagPath() default "/Questionnaires/Visit information/surveys_submitted";

        @AttributeDefinition(name = "Linking Subject Type", description = "Subject type that links the Form with the"
            + " \"submit\" button to the Form with the alert generating question")
        String linkingSubjectType() default "/SubjectTypes/Patient/Visit";

        @AttributeDefinition(name = "Alerting Question Path", description = "A question that can trigger the alert")
        String alertingQuestionPath() default "/Questionnaires/PHQ9/phq9_survey/phq9_more/phq9_9";

        @AttributeDefinition(name = "Trigger Expression Operator", description = "Trigger Expression")
        String triggerOperator() default ">";

        @AttributeDefinition(name = "Trigger Expression Operand", description = "Trigger Expression")
        String triggerOperand() default "0";

        @AttributeDefinition(name = "Alert Description", description = "A message to be placed in the email body.")
        String alertDescription() default "";

        @AttributeDefinition(name = "Clinic ID Link", description = "Response associated with the"
            + " subject of Linking Subject Type that associates it with a clinic")
        String clinicIdLink() default "/Questionnaires/Visit information/clinic";

        @AttributeDefinition(name = "Clinics JCR Path")
        String clinicsJcrPath() default "/Survey";

        @AttributeDefinition(name = "Clinic Email Property",
            description = "Property of the Clinic definition where the emergency contact is stored")
        String clinicEmailProperty() default "emergencyContact";

        @AttributeDefinition(name = "From Email", description = "The email address which this email originates from")
        String emailFromAddress() default "";
    }

    @Activate
    private void activate(final Config config)
    {
        LOGGER.info("ACTIVATING EmailFormAlerts with name: {}", config.name());

        try {
            Map<String, Object> params = new HashMap<>();
            params.put(ResourceResolverFactory.SUBSERVICE, "EmailNotifications");
            this.resolver = this.resolverFactory.getServiceResourceResolver(params);

            // Get the UUID associated with config.submittedFlagPath()
            final String submittedFlagUUID = this.resolver.getResource(
                config.submittedFlagPath()).getValueMap().get("jcr:uuid", "");

            if ("".equals(submittedFlagUUID)) {
                return;
            }

            Map<String, String> listenerParams = new HashMap<>();
            listenerParams.put("alertName", config.name());
            listenerParams.put("submittedFlagUUID", submittedFlagUUID);
            listenerParams.put("linkingSubjectType", config.linkingSubjectType());
            listenerParams.put("alertingQuestionPath", config.alertingQuestionPath());
            listenerParams.put("triggerOperator", config.triggerOperator());
            listenerParams.put("triggerOperand", config.triggerOperand());
            listenerParams.put("alertDescription", config.alertDescription());
            listenerParams.put("clinicIdLink", config.clinicIdLink());
            listenerParams.put("clinicsJcrPath", config.clinicsJcrPath());
            listenerParams.put("clinicEmailProperty", config.clinicEmailProperty());
            listenerParams.put("emailFromAddress", config.emailFromAddress());
            EventListener myEventListener = new EmailAlertEventListener(
                this.resolverFactory, this.formUtils, this.mailService, listenerParams);

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
            LOGGER.warn("Failed to register EmailFormAlerts event handler: {}", e.getMessage());
        }
    }

    @Deactivate
    private void deactivate()
    {
        if (this.session != null) {
            this.session.logout();
        }
    }
}
