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
package io.uhndata.cards.subjects.api;

import javax.jcr.Node;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * Basic utilities for working with Subject Types.
 *
 * @version $Id$
 */
public interface SubjectTypeUtils
{
    /** The primary node type for a Subject Type. */
    String SUBJECT_TYPE_NODETYPE = "cards:SubjectType";

    /** The Sling resource type for a Subject Type. */
    String SUBJECT_TYPE_RESOURCE = "cards/SubjectType";

    /** The name of the property of a Subject Type node that holds its label. */
    String LABEL_PROPERTY = "label";

    /**
     * Check if the given node is a SubjectType node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:SubjectType}, {@code false}
     *         otherwise
     */
    boolean isSubjectType(Node node);

    /**
     * Check if the given node is a SubjectType node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:SubjectType}, {@code false}
     *         otherwise
     */
    boolean isSubjectType(NodeBuilder node);

    /**
     * Check if the given node is a SubjectType node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:SubjectType}, {@code false}
     *         otherwise
     */
    boolean isSubjectType(NodeState node);

    /**
     * Retrieve the SubjectType with the given UUID.
     *
     * @param identifier a UUID that references a SubjectType.
     * @return a Node, or {@code null} if the identifier does not point to a SubjectType
     */
    Node getSubjectType(String identifier);

    /**
     * Retrieve the label (human readable identifier) of the given SubjectType.
     *
     * @param subjectType a SubjectType node, may be {@code null}
     * @return a label, or {@code null} if the provided node is not a SubjectType
     */
    String getLabel(Node subjectType);
}
