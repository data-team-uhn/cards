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
package io.uhndata.cards.examples;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.commit.DefaultValidator;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * A sample {@link Validator} that restricts a (long) value within a range.
 *
 * @version $Id$
 */
public class ValueValidator extends DefaultValidator
{
    // Called when a new property is added
    @Override
    public void propertyAdded(PropertyState after) throws CommitFailedException
    {
        validateAge(after);
    }

    // Called when a property value gets changed
    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        validateAge(after);
    }

    // When something changes in a node deep in the content tree, the validator is invoked starting with the root node,
    // descending to the actually changed node through subsequent calls to childNodeChanged. The default behavior of
    // DefaultValidator is to stop the validation at the root, so we must override the following two methods in order
    // for the validator to be invoked on non-root nodes.
    @Override
    public Validator childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        return this;
    }

    @Override
    public Validator childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        return this;
    }

    private void validateAge(PropertyState state) throws CommitFailedException
    {
        if (!("voterAge".equals(state.getName()) && state.getType() == Type.LONG)) {
            return;
        }
        final long value = state.getValue(Type.LONG);
        if (value < 18) {
            throw new CommitFailedException(CommitFailedException.CONSTRAINT, 1, "No voting under 18 years!");
        }
    }
}
