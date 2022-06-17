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

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;
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
        if ("cards:Form".equals(childNodeType)) {
            String questionnaireUUID = after.getProperty("questionnaire").getValue(Type.REFERENCE).toString();
            String subjectUUID = after.getProperty("subject").getValue(Type.REFERENCE).toString();
            LOGGER.warn("Added this --> {}", after);
            LOGGER.warn("A cards:Form node was just added with questionnaire={} and subject={} !!!",
                questionnaireUUID,
                subjectUUID
            );
        }
        return this;
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
