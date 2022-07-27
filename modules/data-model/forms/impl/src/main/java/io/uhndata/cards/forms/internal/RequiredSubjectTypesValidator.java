
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultValidator;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import io.uhndata.cards.forms.api.FormUtils;

/**
 * A {@link Validator} that ensures that a Form has a Subject compatible with the Questionnaire that the form answers.
 *
 * @version $Id$
 */
public class RequiredSubjectTypesValidator extends DefaultValidator
{
    private final ResourceResolverFactory rrf;

    private final NodeState currentNode;

    private final FormUtils formUtils;

    public RequiredSubjectTypesValidator(final ResourceResolverFactory rrf, final FormUtils formUtils,
        final NodeState currentNode)
    {
        this.rrf = rrf;
        this.formUtils = formUtils;
        this.currentNode = currentNode;
    }

    @Override
    public Validator childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        // Get the type of this node. Return immediately if it's not a cards:Form node
        final String childNodeType = after.getName("jcr:primaryType");
        if (!("cards:Form".equals(childNodeType))) {
            return new RequiredSubjectTypesValidator(this.rrf, this.formUtils, after);
        }

        return isFormValid(after);
    }

    @Override
    public Validator childNodeChanged(final String name, final NodeState before, final NodeState after)
    {
        return new RequiredSubjectTypesValidator(this.rrf, this.formUtils, after);
    }

    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        // Get the type of this node. Return immediately if it's not a cards:Form node
        final String childNodeType = this.currentNode.getName("jcr:primaryType");
        if ("cards:Form".equals(childNodeType) && "subject".equals(after.getName())) {
            isFormValid(this.currentNode);
        }
    }

    public Validator isFormValid(NodeState after) throws CommitFailedException
    {
        // Get the jcr:uuid values for the Form's associated Questionnaire and Subject
        final String questionnaireUUID = after.getProperty("questionnaire").getValue(Type.REFERENCE).toString();
        final String subjectUUID = after.getProperty("subject").getValue(Type.REFERENCE).toString();
        // Obtain a ResourceResolver for querying the JCR
        try (ResourceResolver serviceResolver = this.rrf
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "requiredSubjectTypesValidator"))) {
            // Get the Subject Resource associated with this Form
            final Resource subject = getResourceByUuid(serviceResolver, subjectUUID, "Subject");
            // Get the Questionnaire Resource associated with this Form
            final Resource questionnaire = getResourceByUuid(serviceResolver, questionnaireUUID, "Questionnaire");
            if (subject == null || questionnaire == null) {
                // Should not happen, these are mandatory properties. However, we cannot proceed without them, and since
                // we're already in the form, there's no need to go deeper with the validation.
                return null;
            }

            // Get the type value for the Subject
            final String type = subject.getValueMap().get("type", "");
            // Get the requiredSubjectTypes value for the Questionnaire
            final String[] allRequiredSubjectTypes = questionnaire.getValueMap().get("requiredSubjectTypes",
                String[].class);
            final List<String> allRequiredSubjectTypesList = Arrays.asList(allRequiredSubjectTypes != null
                ? allRequiredSubjectTypes : new String[0]);
            // No required subject types means that any subject type is allowed.
            // Otherwise, the subject type must be one of the required ones
            if (allRequiredSubjectTypesList.size() > 0 && !allRequiredSubjectTypesList.contains(type)) {
                throw new CommitFailedException(CommitFailedException.STATE, 400,
                    "The type is not listed by the associated Questionnaire’s requiredSubjectTypes property");
            }
            // We already processed the form, no need to go deeper
            return null;
        } catch (final LoginException e) {
            // Should not happen
        }
        return new RequiredSubjectTypesValidator(this.rrf, this.formUtils, after);
    }

    /**
     * Obtains the Resource that has a specified jcr:uuid and jcr:primaryType.
     *
     * @param serviceResolver a ResourceResolver that can be used for querying the JCR
     * @param uuid the jcr:uuid of the Resource which we wish to obtain
     * @param primaryType the jcr:primaryType of the Resource which we wish to obtain
     * @return the matching Resource or null if none can be found
     */
    private Resource getResourceByUuid(final ResourceResolver serviceResolver, final String uuid,
        final String primaryType)
    {
        final Iterator<Resource> resourceIterator = serviceResolver.findResources(
            "SELECT * FROM [cards:" + primaryType + "] AS q WHERE q.'jcr:uuid'='" + uuid + "'", "JCR-SQL2");

        if (!resourceIterator.hasNext()) {
            return null;
        }
        return resourceIterator.next();
    }
}
