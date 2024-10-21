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
package io.uhndata.cards.forms.api;

import javax.jcr.Node;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;

/**
 * Basic utilities for updating Form data.
 *
 * @version $Id$
 */
public interface FormUpdateUtils
{

    /**
     * Retrieve a NodeBuilder for the Answer or Answer section for the specified Question or Section.
     * - If a relevant Answer or Answer Section already exists, it will be returned.
     * - If not, a new Answer or Answer Section will be generated and returned
     *
     * @param formParent The {@code NodeBuilder} to check for a matching child Answer/AnswerSection or
     *                   to generate within. This should be either an AnswerSection or a Form.
     * @param questionnaireNode The Question or Section that needs an Answer or AnswerSection retrieved.
     * @return an Answer or AnswerSection {@code NodeBuilder} that is for the desired Question/Section.
     */
    NodeBuilder getOrGenerateChild(NodeBuilder formParent, Node questionnaireNode);

    /**
     * Retrieve a NodeBuilder for the Answer or Answer section for the specified path.
     * If anywhere along the path a needed AnswerSection or Answer does not exist, one will be generated.
     *
     * @param formNode The {@code NodeBuilder} to check follow the path from.
     * @param path The path to be followed.
     * @param questionnaireNode The Section or Form for the formNode
     * @return an Answer or AnswerSection {@code NodeBuilder} that is for the desired path.
     */
    NodeBuilder getOrGeneratePath(NodeBuilder formNode, String path, Node questionnaireNode);
}
