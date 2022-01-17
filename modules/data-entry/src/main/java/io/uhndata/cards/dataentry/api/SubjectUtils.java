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
package io.uhndata.cards.dataentry.api;

import javax.jcr.Node;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * Basic utilities for working with Subjects.
 *
 * @version $Id$
 */
public interface SubjectUtils
{
    /** The primary node type for a Subject, an entity about which data is collected. */
    String SUBJECT_NODETYPE = "cards:Subject";

    /** The Sling resource type for a Subject. */
    String SUBJECT_RESOURCE = "cards/Subject";

    /** The name of the property of a Subject node that links to the Subject Type. */
    String TYPE_PROPERTY = "type";

    /** The name of the property of a Subject node that holds its label. */
    String LABEL_PROPERTY = "identifier";

    /**
     * Check if the given node is a Subject node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Subject}, {@code false}
     *         otherwise
     */
    boolean isSubject(Node node);

    /**
     * Check if the given node is a Subject node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Subject}, {@code false}
     *         otherwise
     */
    boolean isSubject(NodeBuilder node);

    /**
     * Check if the given node is a Subject node.
     *
     * @param node the node to check, a JCR Node, may be {@code null}
     * @return {@code true} if the node is not {@code null} and is of type {@code cards:Subject}, {@code false}
     *         otherwise
     */
    boolean isSubject(NodeState node);

    /**
     * Retrieve the Subject with the given UUID.
     *
     * @param identifier a UUID that references a Subject.
     * @return a Node
     */
    Node getSubject(String identifier);

    /**
     * Retrieve the Subject Type of the given subject.
     *
     * @param subject a Subject node, may be {@code null}
     * @return a SubjectType node, or {@code null} if the provided node is not a Subject
     */
    Node getType(Node subject);

    /**
     * Retrieve the label (human readable identifier) of the given Subject.
     *
     * @param subject a Subject node, may be {@code null}
     * @return a label, or {@code null} if the provided node is not a Subject
     */
    String getLabel(Node subject);
}
