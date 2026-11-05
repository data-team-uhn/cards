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

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.junit.Before;
import org.junit.Test;

import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link SubjectIdPatternValidatorProvider}.
 *
 * @version $Id$
 */
public class SubjectIdPatternValidatorProviderTest
{
    private final SubjectIdPatternValidatorProvider provider = new SubjectIdPatternValidatorProvider();

    @Before
    public void setUp() throws IllegalAccessException
    {
        FieldUtils.writeField(this.provider, "subjectTypeUtils", mock(SubjectTypeUtils.class), true);
        FieldUtils.writeField(this.provider, "subjectUtils", mock(SubjectUtils.class), true);
    }

    @Test
    public void getRootValidatorCreatesSubjectIdPatternValidator()
    {
        Validator validator = this.provider.getRootValidator(mock(NodeState.class), mock(NodeState.class),
            CommitInfo.EMPTY);
        assertNotNull(validator);
        assertTrue(validator instanceof SubjectIdPatternValidator);
    }
}
