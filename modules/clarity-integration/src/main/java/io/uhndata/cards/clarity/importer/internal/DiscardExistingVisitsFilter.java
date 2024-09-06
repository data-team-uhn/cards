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

package io.uhndata.cards.clarity.importer.internal;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.clarity.importer.spi.AbstractClarityDataProcessor;
import io.uhndata.cards.clarity.importer.spi.ClarityDataProcessor;
import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Clarity import processor that discards existing visits when another visit takes priority and should be imported
 * instead.
 *
 * @version $Id$
 */
@Designate(ocd = DiscardExistingVisitsFilter.Config.class)
@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class DiscardExistingVisitsFilter extends AbstractClarityDataProcessor implements ClarityDataProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscardExistingVisitsFilter.class);

    private static final SimpleDateFormat SQL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");

    private static final SimpleDateFormat JCR_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private FormUtils formUtils;

    private final String dateColumn;

    private final Set<String> clinicsToConsider;

    private final boolean discardNewEvent;

    @ObjectClassDefinition(
        name = "Clarity import filter - Delete previously imported events",
        description = "Configuration for the Clarity importer to delete existing visits on the same day as the newly imported event")
    public @interface Config
    {
        @AttributeDefinition(name = "Enabled")
        boolean enable() default true;

        @AttributeDefinition(name = "Supported import types", description = "Leave empty to support all imports")
        String[] supportedTypes();

        @AttributeDefinition(name = "Visit date column")
        String dateColumn();

        @AttributeDefinition(name = "Clinics to consider",
            description = "List paths to the clinics to consider for deletion."
                + " If empty, all existing visits will be considered regardless of their clinic.")
        String[] clinics();

        @AttributeDefinition(name = "Discard new event too")
        boolean discardNew() default false;
    }

    @Activate
    public DiscardExistingVisitsFilter(Config config)
    {
        super(config.enable(), config.supportedTypes(), 100);
        this.dateColumn = config.dateColumn();
        this.clinicsToConsider = (config.clinics() == null || config.clinics().length == 0) ? Collections.emptySet()
            : Set.of(config.clinics());
        this.discardNewEvent = config.discardNew();
    }

    @Override
    public Map<String, String> processEntry(final Map<String, String> input)
    {
        try {
            deleteEvents(input);
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to process entry: {}", e.getMessage(), e);
        } catch (ParseException e) {
            LOGGER.warn("Invalid date: {}", input.getOrDefault(this.dateColumn, ""), e);
        }
        return this.discardNewEvent ? null : input;
    }

    private void deleteEvents(final Map<String, String> input) throws RepositoryException, ParseException
    {
        final Calendar startTime = Calendar.getInstance();
        startTime.setTime(SQL_DATE_FORMAT.parse(input.getOrDefault(this.dateColumn, "")));
        atMidnight(startTime);
        final Calendar endTime = (Calendar) startTime.clone();
        endTime.add(Calendar.DATE, 1);
        final Session session = this.rrp.getThreadResourceResolver().adaptTo(Session.class);
        final String patientUuid = findSubject(input, session);
        final String formQuery = String.format(
            "SELECT * FROM [cards:Form] AS vi"
                + " INNER JOIN [cards:DateAnswer] AS time ON time.form = vi.[jcr:uuid]"
                + " WHERE"
                + " vi.questionnaire = '%s' AND vi.relatedSubjects ='%s'"
                + " AND time.question = '%s' AND time.value >= '%s' AND time.value < '%s'"
                + " option (index tag property)",
            session.getNode("/Questionnaires/Visit information").getIdentifier(),
            patientUuid,
            session.getNode("/Questionnaires/Visit information/time").getIdentifier(),
            JCR_DATE_FORMAT.format(startTime.getTime()),
            JCR_DATE_FORMAT.format(endTime.getTime()));
        final NodeIterator visits =
            session.getWorkspace().getQueryManager().createQuery(formQuery, "JCR-SQL2").execute().getNodes();
        // Should only be 0 or 1 patient with that identifier. Process it if found.
        while (visits.hasNext()) {
            final Node visitInformation = visits.nextNode();
            final String clinic = (String) this.formUtils.getValue(this.formUtils.getAnswer(visitInformation,
                session.getNode("/Questionnaires/Visit information/clinic")));
            if (this.clinicsToConsider.isEmpty() || this.clinicsToConsider.contains(clinic)) {
                deleteNode(this.formUtils.getSubject(visitInformation));
            }
        }
    }

    private String findSubject(final Map<String, String> input, final Session session) throws RepositoryException
    {
        final String subjectId = input.get("/SubjectTypes/Patient");
        String subjectMatchQuery = String.format(
            "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='%s' option (index tag property)",
            subjectId);
        session.refresh(true);
        final NodeIterator subjectResourceIter =
            session.getWorkspace().getQueryManager().createQuery(subjectMatchQuery, "JCR-SQL2").execute().getNodes();
        if (!subjectResourceIter.hasNext()) {
            return null;
        }
        return subjectResourceIter.nextNode().getIdentifier();
    }

    @SuppressWarnings("unchecked")
    private void deleteNode(final Node visit)
    {
        try {
            visit.getReferences().forEachRemaining(this::deleteForm);
            if (visit.hasNodes()) {
                visit.getNodes().forEachRemaining(o -> deleteNode((Node) o));
            }
            visit.remove();
            visit.getSession().save();
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to delete subject: {}", e.getMessage(), e);
        }
    }

    private void deleteForm(Object p)
    {
        try {
            ((Property) p).getParent().remove();
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to delete visit form: {}", e.getMessage(), e);
        }
    }

    private void atMidnight(final Calendar c)
    {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
