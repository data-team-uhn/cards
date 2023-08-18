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
package io.uhndata.cards.forms.internal;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultValidator;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

/**
 * A {@link Validator} that ensures that the number of created Forms of a specific type does not exceed the maximum
 * value allowed for a Subject.
 *
 * @version $Id$
 */
public class MaxFormsOfTypePerSubjectValidator extends DefaultValidator
{
    private final ResourceResolverFactory rrf;

    public MaxFormsOfTypePerSubjectValidator(final ResourceResolverFactory rrf)
    {
        this.rrf = rrf;
    }

    @Override
    public Validator childNodeAdded(final String name, final NodeState after) throws CommitFailedException
    {
        // Get the type of this node. Return immediately if it's not a cards:Form node
        final String childNodeType = after.getName("jcr:primaryType");
        if (!("cards:Form".equals(childNodeType))) {
            return this;
        }

        // Get the jcr:uuid values for the Form's associated Questionnaire and Subject
        final String questionnaireUUID = after.getProperty("questionnaire").getValue(Type.REFERENCE).toString();
        final String subjectUUID = after.getProperty("subject").getValue(Type.REFERENCE).toString();

        // Obtain a ResourceResolver for querying the JCR
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "maxFormsOfTypePerSubjectValidator");
        try (ResourceResolver serviceResolver = this.rrf.getServiceResourceResolver(parameters)) {

            // Get the Questionnaire Resource associated with this Form
            final Resource questionnaire = getQuestionnaireResourceByUuid(serviceResolver, questionnaireUUID);
            if (questionnaire == null) {
                return this;
            }

            // Get the maxPerSubject value for the Questionnaire
            final long maxPerSubject = questionnaire.getValueMap().get("maxPerSubject", -1);

            // Should this commit be allowed or does it exceed the maxPerSubject constraint?
            if (maxPerSubject > 0) {
                final long formNumber = countFormsPerSubject(subjectUUID, questionnaireUUID, serviceResolver) + 1;
                if (formNumber > maxPerSubject) {
                    throw new CommitFailedException(CommitFailedException.STATE, 400,
                        "The number of created forms is bigger than is allowed");
                }
            }
        } catch (final LoginException e) {
            // Should not happen
        }
        return this;
    }

    @Override
    public Validator childNodeChanged(final String name, final NodeState before, final NodeState after)
        throws CommitFailedException
    {
        return this;
    }

    /**
     * Counts the number of created Forms per Subject with a specific Questionnaire UUID.
     *
     * @param subjectUUID subject UUID that must match with the Form's subject
     * @param questionnaireUUID questionnaire that must match with the Form's questionnaire
     * @param ResourceResolver a ResourceResolver that can be used for querying the JCR
     * @return a long-typed number of the number of Forms with the specified questionnaire and subject
     */
    private long countFormsPerSubject(final String subjectUUID, final String questionnaireUUID,
        final ResourceResolver serviceResolver)
    {
        long count = 0;
        final Iterator<Resource> results = serviceResolver.findResources(
            "SELECT f.* FROM [cards:Form] AS f WHERE f.'subject'='" + subjectUUID + "'"
                + " AND f.'questionnaire'='" + questionnaireUUID + "' OPTION (index tag property)",
            "JCR-SQL2");

        while (results.hasNext()) {
            count += 1;
            results.next();
        }
        return count;
    }

    /**
     * Obtains the Questionnaire Resource that has a specified jcr:uuid.
     *
     * @param ResourceResolver a ResourceResolver that can be used for querying the JCR
     * @param uuid the jcr:uuid of the Questionnaire Resource which we wish to obtain
     * @return the matching Questionnaire Resource or null if none can be found
     */
    private Resource getQuestionnaireResourceByUuid(final ResourceResolver serviceResolver, final String uuid)
    {
        final Iterator<Resource> resourceIterator = serviceResolver.findResources(
            "SELECT * FROM [cards:Questionnaire] as q WHERE q.'jcr:uuid'='" + uuid + "'", "JCR-SQL2");
        if (!resourceIterator.hasNext()) {
            return null;
        }
        return resourceIterator.next();
    }
}
