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
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sample {@link Validator} that simply logs all method calls.
 *
 * @version $Id$
 */
public class DebugLoggingValidator implements Validator
{
    private final Logger logger = LoggerFactory.getLogger(DebugLoggingValidator.class);

    @Override
    public void enter(NodeState before, NodeState after) throws CommitFailedException
    {
        this.logger.debug("DebugLoggingValidator.enter [{} -> {}]", before, after);
    }

    @Override
    public void leave(NodeState before, NodeState after) throws CommitFailedException
    {
        this.logger.debug("DebugLoggingValidator.leave [{} -> {}]", before, after);
    }

    @Override
    public void propertyAdded(PropertyState after) throws CommitFailedException
    {
        this.logger.debug("DebugLoggingValidator.propertyAdded [{}]", after);
    }

    @Override
    public void propertyChanged(PropertyState before, PropertyState after) throws CommitFailedException
    {
        this.logger.debug("DebugLoggingValidator.propertyChanged [{} -> {}]", before, after);
    }

    @Override
    public void propertyDeleted(PropertyState before) throws CommitFailedException
    {
        this.logger.debug("DebugLoggingValidator.propertyDeleted [{}]", before);
    }

    @Override
    public Validator childNodeAdded(String name, NodeState after) throws CommitFailedException
    {
        this.logger.debug("DebugLoggingValidator.childNodeAdded [{} = {}]", name, after);
        return this;
    }

    @Override
    public Validator childNodeChanged(String name, NodeState before, NodeState after) throws CommitFailedException
    {
        this.logger.debug("DebugLoggingValidator.childNodeChanged [{} = {} -> {}]", name, before, after);
        return this;
    }

    @Override
    public Validator childNodeDeleted(String name, NodeState before) throws CommitFailedException
    {
        this.logger.debug("DebugLoggingValidator.childNodeDeleted [{} = {}]", name, before);
        return this;
    }
}
