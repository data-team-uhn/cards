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
package io.uhndata.cards.internal;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Validator} that ensures that the number of created Forms of
 * a specifc type does not exceed the maximum value allowed for a
 * Subject.
 *
 * @version $Id$
 */
public class MaxFormsOfTypePerSubjectValidator implements Validator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MaxFormsOfTypePerSubjectValidator.class);

    private static final String END = "'";
    private final ResourceResolverFactory rrf;

    public MaxFormsOfTypePerSubjectValidator(final ResourceResolverFactory rrf)
    {
        this.rrf = rrf;
    }

    @Override
    public void enter(NodeState before, NodeState after) throws CommitFailedException
    {
    }

    @Override
    public void leave(NodeState before, NodeState after) throws CommitFailedException
    {
    }

    @Override
    public void propertyAdded(PropertyState after) throws CommitFailedException
    {
    }

    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
    }

    @Override
    public void propertyDeleted(PropertyState before) throws CommitFailedException
    {
    }

    @Override
    public Validator childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        String childNodeType = after.getName("jcr:primaryType");
        if (!("cards:Form".equals(childNodeType))) {
            return this;
        }
        String questionnaireUUID = after.getProperty("questionnaire").getValue(Type.REFERENCE).toString();
        String subjectUUID = after.getProperty("subject").getValue(Type.REFERENCE).toString();
        final Map<String, Object> parameters =
            Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "maxFormsOfTypePerSubjectValidator");
        try (ResourceResolver serviceResolver = this.rrf.getServiceResourceResolver(parameters)) {
            Resource questionnaire = getQuestionnaireResourceByUuid(serviceResolver, questionnaireUUID);
            if (questionnaire == null) {
                return this;
            }
            long maxPerSubject = questionnaire.getValueMap().get("maxPerSubject", -1);
            if (maxPerSubject > 0) {
                long formNumber = countFormsPerSubject(subjectUUID, questionnaireUUID, serviceResolver) + 1;
                LOGGER.warn("The number of existing forms is {} and allowed is {}", formNumber, maxPerSubject);
                if (formNumber > maxPerSubject) {
                    throw new CommitFailedException(CommitFailedException.STATE, 400,
                            "The number of created forms is bigger then is allowed");
                }
            }
        } catch (LoginException e) {
            // Should not happen
        }
        return this;
    }

    /**
     * Counts the number of created forms per subject with specific questionnaire title.
     *
     * @param subjectUUID subject to be checked id
     * @param questionnaireUUID type of questionnaire to be count per subject
     * @return a long
     */
    private long countFormsPerSubject(String subjectUUID, String questionnaireUUID,
                                      ResourceResolver serviceResolver)
    {
        long count = 0;
        Iterator<Resource> results = serviceResolver.findResources(
                "SELECT f.* FROM [cards:Form] AS f WHERE f.'subject'='" + subjectUUID + END
                        + " AND f.'questionnaire'='" + questionnaireUUID + END,
                "JCR-SQL2"
        );

        while (results.hasNext()) {
            count += 1;
            results.next();
        }
        return count;
    }

    private Resource getQuestionnaireResourceByUuid(ResourceResolver serviceResolver, String uuid)
    {
        Iterator<Resource> resourceIterator = serviceResolver.findResources(
                "SELECT * FROM [cards:Questionnaire] as q WHERE q.'jcr:uuid'='" + uuid + END, "JCR-SQL2");
        if (!resourceIterator.hasNext()) {
            return null;
        }
        return resourceIterator.next();
    }

    @Override
    public Validator childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return this;
    }

    @Override
    public Validator childNodeDeleted(String name, NodeState before) throws CommitFailedException
    {
        return this;
    }
}
