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

import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.commit.ValidatorProvider;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sample {@link ValidatorProvider} that returns a {@link Validator} that simply logs all method calls.
 *
 * @version $Id$
 */
@Component(name = "DebugLoggingValidatorProvider", service = EditorProvider.class)
public class DebugLoggingValidatorProvider extends ValidatorProvider
{
    private Logger logger = LoggerFactory.getLogger(DebugLoggingValidatorProvider.class);

    @Override
    protected Validator getRootValidator(NodeState before, NodeState after, CommitInfo info)
    {
        this.logger.debug("LoggingValidatorProvider.getRootEditor({}, {}, {}", before, after, info);
        return new DebugLoggingValidator();
    }
}
