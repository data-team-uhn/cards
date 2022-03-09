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
package io.uhndata.cards.dataentry.internal.serialize;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.dataentry.api.FormUtils;
import io.uhndata.cards.dataentry.api.QuestionnaireUtils;
import io.uhndata.cards.dataentry.api.SubjectUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * A processor that copies the values of certain answers from forms to the root of the subject JSON
 * for easy access from, for ex., the dashboard through the pagination servlet. The answers to copy are configured in
 * {@code /apps/cards/config/CopySubjectAnswers/[subject type]/} as properties with the desired name as the key,
 * and a references to a question as the value. The name of this processor is {@code answerCopy} and it is enabled by
 * default.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class SubjectAnswerCopyProcessor extends AbstractAnswerCopyProcessor implements ResourceJsonProcessor
{
    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Override
    public String getName()
    {
        return "answerCopy";
    }

    @Override
    public int getPriority()
    {
        return 95;
    }

    @Override
    public boolean isEnabledByDefault(final Resource resource)
    {
        return true;
    }

    @Override
    public boolean canProcess(final Resource resource)
    {
        // This only works on subjects
        return resource.isResourceType("cards/Subject");
    }

    @Override
    public void start(final Resource resource)
    {
        startProcessor(resource);
        ResourceJsonProcessor.super.start(resource);
    }

    @Override
    public void leave(final Node node, final JsonObjectBuilder json, final Function<Node, JsonValue> serializeNode)
    {
        try {
            if (this.answersToCopy.get() != null && node.isNodeType(SubjectUtils.SUBJECT_NODETYPE)) {
                copyAnswers(node, json);
            }
        } catch (final RepositoryException e) {
            // Should not happen
        }
    }

    @Override
    public void end(final Resource resource)
    {
        this.answersToCopy.remove();
    }

    @Override
    String getResourceName(final Resource resource) throws RepositoryException
    {
        final Property subjectNameProp =
                resource.getValueMap().get(SubjectUtils.TYPE_PROPERTY, Property.class);
        if (subjectNameProp != null) {
            return subjectNameProp.getNode().getIdentifier();
        } else {
            return null;
        }
    }

    @Override
    Node getAnswer(Node form, Node question)
    {
        return this.formUtils.getAnswer(form, question);
    }

    @Override
    Node getSubject(Node source)
    {
        return this.formUtils.getSubject(source);
    }

    @Override
    Node getQuestionnaire(Node form)
    {
        return this.formUtils.getQuestionnaire(form);
    }

    @Override
    boolean isSubject(Node source)
    {
        return this.subjectUtils.isSubject(source);
    }

    @Override
    Node getOwnerQuestionnaire(Node question)
    {
        return this.questionnaireUtils.getOwnerQuestionnaire(question);
    }

    @Override
    Object getValue(Node answer)
    {
        return this.formUtils.getValue(answer);
    }
}
