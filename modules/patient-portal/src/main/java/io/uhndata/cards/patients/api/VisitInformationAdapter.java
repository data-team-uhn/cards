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
package io.uhndata.cards.patients.api;

import javax.jcr.Node;

/**
 * A service that converts JCR nodes of type {@code cards:Form} of type {@code Visit Information} into a data model Java
 * object.
 *
 * @version $Id$
 * @since 0.9.25
 */
public interface VisitInformationAdapter
{
    /**
     * Convert the provided JCR node into a data model object.
     *
     * @param form a JCR node, either a {@code Visit Information} {@code cards:Form}, or a {@code Visit} subject
     * @return a {@link VisitInformation} object, or {@code null} if the provided node is not a Visit Information form,
     *         or a Visit subject, or it cannot be accessed
     */
    VisitInformation toVisitInformation(Node form);
}
