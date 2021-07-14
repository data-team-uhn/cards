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
package io.uhndata.cards.permissions.spi;

import java.security.Principal;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.security.Privilege;

/**
 * Service interface used by {@link io.uhndata.cards.permissions.internal.PermissionsManagerService} to alter
 * permissions on JCR nodes.
 *
 * @version $Id$
 */
public interface PermissionsManager
{
    /**
     * Adds a new ACE (Access Control Entry) to the policy on the {@code target} node with the given specifications.
     * @param target the target node to alter permissions for
     * @param isAllow whether the request is to allow (true) or deny (false) access
     * @param principal the Principal for the rule (i.e. target users to affect)
     * @param privileges a comma-delimited list of privileges
     * @param restrictions the restrictions to apply
     * @param session the {@code javax.jcr.Session} to commit changes to.
     * @throws RepositoryException if an error occurs while obtaining repository entries, or
     *     illegal arguments occur
     */
    void addAccessControlEntry(String target, boolean isAllow, Principal principal, Privilege[] privileges,
            Map<String, Value> restrictions, Session session) throws RepositoryException;

    /**
     * Removes the ACE matching the parameters from the {@code target} node, if any such ACE exists.
     * @param target the target node to alter permissions for
     * @param isAllow whether the request is to allow (true) or deny (false) access
     * @param principal the Principal for the rule (i.e. target users to affect)
     * @param privileges a comma-delimited list of privileges
     * @param restrictions the restrictions to apply
     * @param session the {@code javax.jcr.Session} to commit changes to.
     * @throws RepositoryException if an error occurs while obtaining repository entries, or
     *     illegal arguments occur
     */
    void removeAccessControlEntry(String target, boolean isAllow, Principal principal, Privilege[] privileges,
            Map<String, Value> restrictions, Session session) throws RepositoryException;
}
