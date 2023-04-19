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
package ca.sickkids.ccm.lfs.permissions.internal;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;

/**
 * A restriction that makes a permissions entry only be valid on a node of type Answer for a specific Question.
 *
 * @version $Id$
 */
public class QuestionRestrictionPattern implements RestrictionPattern
{
    private final String targetQuestion;

    /**
     * Constructor which receives the configured restriction.
     *
     * @param value the identifier (UUID) of a specific question
     */
    public QuestionRestrictionPattern(String value)
    {
        this.targetQuestion = value;
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        // This restriction only applies to Answers.
        // If this is not an Answer node, we do not care.
        if (!tree.hasProperty("sling:resourceSuperType")
            || !tree.getProperty("sling:resourceSuperType").getValue(Type.STRING).equals("lfs/Answer")) {
            return false;
        }
        // Check if the question for this answer is the same as the one specified in the restriction
        boolean result =
            StringUtils.equals(tree.getProperty("question").getValue(Type.REFERENCE),
                this.targetQuestion);
        return result;
    }

    @Override
    public boolean matches(String path)
    {
        // This method doesn't seem to be called, the one above is used instead
        return false;
    }

    @Override
    public boolean matches()
    {
        // This is not a repository-wide restriction, it only applies to specific nodes
        return false;
    }
}