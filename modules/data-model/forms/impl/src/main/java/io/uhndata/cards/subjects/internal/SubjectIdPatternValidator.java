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
package io.uhndata.cards.subjects.internal;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultValidator;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;

import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * A {@link Validator} that prevents the creation of new subjects if an idPattern
 * is specified for their subject type but the provided identifier doesn’t match it.
 *
 * @version $Id$
 */
public class SubjectIdPatternValidator extends DefaultValidator
{
    private final SubjectTypeUtils subjectTypeUtils;

    private final SubjectUtils subjectUtils;


    public SubjectIdPatternValidator(final SubjectTypeUtils subjectTypeUtils, final SubjectUtils subjectUtils)
    {
        this.subjectTypeUtils = subjectTypeUtils;
        this.subjectUtils = subjectUtils;
    }

    @Override
    public Validator childNodeAdded(final String name, final NodeState after) throws CommitFailedException
    {
        if (this.subjectUtils.isSubject(after)) {
            validateIdPattern(after);
        }
        return this;
    }

    /**
     * Check that a subject id is validated by the associated subject type pattern regexp.
     * If not, a {@code CommitFailedException} is raised.
     *
     * @param subject the node to check
     * @throws CommitFailedException if the subject id is not validated by the subject type pattern
     */
    private void validateIdPattern(NodeState subject) throws CommitFailedException
    {
        try {
            final String id = subject.getProperty("id").getValue(Type.STRING);
            // Get the subject's type
            Node subjectType = this.subjectTypeUtils.getSubjectType(
                subject.getProperty("type").getValue(Type.REFERENCE));

            if (subjectType.hasProperty("idPattern")) {
                final String regexp = subjectType.getProperty("idPattern").getString();
                if (StringUtils.isNotBlank(regexp)) {
                    Pattern pattern = Pattern.compile(regexp);
                    if (!pattern.matcher(id).find()) {
                        throw new CommitFailedException(CommitFailedException.STATE, 400,
                            "This subject cannot be created because the id is not validated by subject type pattern");
                    }
                }
            }
        } catch (PatternSyntaxException e) {
            throw new CommitFailedException(CommitFailedException.STATE, 400,
                "This subject cannot be created because the subject type pattern is not valid");
        } catch (RepositoryException e) {
            // Should not happen
        }
    }
}
