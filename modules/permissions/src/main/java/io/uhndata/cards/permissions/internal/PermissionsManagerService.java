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
package io.uhndata.cards.permissions.internal;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.permissions.spi.PermissionsManager;

/**
 * Permission changing service for altering/creating ACLs on arbitrary nodes.
 *
 * @version $Id$
 */
@Component(service = { PermissionsManager.class })
public class PermissionsManagerService implements PermissionsManager
{
    @Override
    public void addAccessControlEntry(String target, boolean isAllow, Principal principal, String[] privileges,
        Map<String, Value> restrictions, Session session) throws RepositoryException
    {
        AccessControlManager acm = session.getAccessControlManager();
        addAccessControlEntry(target, isAllow, principal, parsePrivileges(privileges, acm), restrictions, session);
    }

    @Override
    public void addAccessControlEntry(String target, boolean isAllow, Principal principal, Privilege[] privileges,
        Map<String, Value> restrictions, Session session) throws RepositoryException
    {
        AccessControlManager acm = session.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acm, target);

        // Provided we could obtain the AccessControlList, add a new entry
        if (acl != null) {
            acl.addEntry(principal, privileges, isAllow, restrictions);
            acm.setPolicy(target, acl);
        }
    }

    @Override
    public void removeAccessControlEntry(String target, boolean isAllow, Principal principal, String[] privileges,
        Map<String, Value> restrictions, Session session) throws RepositoryException
    {
        AccessControlManager acm = session.getAccessControlManager();
        removeAccessControlEntry(target, isAllow, principal, parsePrivileges(privileges, acm), restrictions, session);
    }

    @Override
    public void removeAccessControlEntry(String target, boolean isAllow, Principal principal, Privilege[] privileges,
        Map<String, Value> restrictions, Session session) throws RepositoryException
    {
        // Find the necessary ACL to remove
        AccessControlManager acm = session.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acm, target);
        if (acl != null) {
            // Find the necessary AccessControlEntry to remove
            JackrabbitAccessControlEntry[] entries = (JackrabbitAccessControlEntry[]) acl.getAccessControlEntries();
            JackrabbitAccessControlEntry toRemove = null;
            for (JackrabbitAccessControlEntry entry : entries) {
                if (entryHasDetails(entry, isAllow, principal, privileges, restrictions)) {
                    // We've found the correct entry, make a note of it
                    toRemove = entry;
                    break;
                }
            }

            // Remove it if it was found
            if (toRemove != null) {
                acl.removeAccessControlEntry(toRemove);
                acm.setPolicy(target, acl);
            } else {
                throw new RepositoryException("Target ACL does not exist");
            }
        }
    }

    @Override
    public void removeAccessControlEntry(String target, boolean isAllow, Principal principal, Set<String> restrictions,
        Session session) throws RepositoryException
    {
        // Find the necessary ACL to remove
        AccessControlManager acm = session.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acm, target);
        if (acl != null) {
            // Find the necessary AccessControlEntry to remove
            JackrabbitAccessControlEntry[] entries = (JackrabbitAccessControlEntry[]) acl.getAccessControlEntries();
            JackrabbitAccessControlEntry toRemove = null;
            for (JackrabbitAccessControlEntry entry : entries) {
                if (entryHasDetails(entry, isAllow, principal, restrictions)) {
                    // We've found the correct entry, make a note of it
                    toRemove = entry;
                    break;
                }
            }

            // Remove it if it was found
            if (toRemove != null) {
                acl.removeAccessControlEntry(toRemove);
                acm.setPolicy(target, acl);
            } else {
                throw new RepositoryException("Target ACL does not exist");
            }
        }
    }

    /**
     * Determine if the given JackrabbitAccessControlEntry fits the given criteria.
     *
     * @param entry entry to determine sameness of
     * @param isAllow whether the request is to allow (true) or deny (false) access
     * @param principal the Principal for the rule (i.e. target users to affect)
     * @param privileges a comma-delimited list of privileges
     * @param restrictions the restrictions to apply
     * @return true if the JackrabbitAccessControlEntry fits the given criteria.
     * @throws RepositoryException if an error occurs while obtaining restrictions from the entry
     */
    private boolean entryHasDetails(JackrabbitAccessControlEntry entry, boolean isAllow,
        Principal principal, Privilege[] privileges, Map<String, Value> restrictions) throws RepositoryException
    {
        // Ensure the principal and rules match
        // Note that using getPrivileges().equals() fails to detect matches
        return (entry.isAllow() == isAllow
            && entry.getPrincipal().equals(principal)
            && entryHasPrivileges(entry, privileges)
            && entryHasRestrictions(entry, restrictions));
    }

    /**
     * Determine if the given JackrabbitAccessControlEntry fits the given criteria.
     *
     * @param entry entry to determine sameness of
     * @param isAllow whether the request is to allow (true) or deny (false) access
     * @param principal the Principal for the rule (i.e. target users to affect)
     * @param privileges a comma-delimited list of privileges
     * @param restrictions the restriction names to check for
     * @return true if the JackrabbitAccessControlEntry fits the given criteria.
     * @throws RepositoryException if an error occurs while obtaining restrictions from the entry
     */
    private boolean entryHasDetails(JackrabbitAccessControlEntry entry, boolean isAllow, Principal principal,
        Set<String> restrictions) throws RepositoryException
    {
        // Ensure the principal and rules match
        // Note that using getPrivileges().equals() fails to detect matches
        return (entry.isAllow() == isAllow
            && entry.getPrincipal().equals(principal)
            && entryHasRestrictions(entry, restrictions));
    }

    /**
     * Check that the given privilege array is present in the given entry.
     *
     * @param entry entry to check
     * @param privileges privileges to check
     * @return true if the given entry's privileges are exactly equal to the given ones.
     */
    private boolean entryHasPrivileges(JackrabbitAccessControlEntry entry, Privilege[] privileges)
    {
        List<Privilege> entryPrivileges = Arrays.asList(entry.getPrivileges());

        // Length check
        if (entryPrivileges.size() != privileges.length) {
            return false;
        }

        // Entry check
        for (Privilege privilege : privileges) {
            if (!entryPrivileges.contains(privilege)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check that the given restrictions map is present in the given entry.
     *
     * @param entry entry to check
     * @param restrictions map of restrictions names to {@code javax.jcr.Value} to check
     * @return true if the entry's restrictions are exactly equal to the given restrictions.
     * @throws RepositoryException if the restrictions could not be obtained from the entry
     */
    private boolean entryHasRestrictions(JackrabbitAccessControlEntry entry, Map<String, Value> restrictions)
        throws RepositoryException
    {
        // Note that we cannot easily obtain the restriction map from the entry
        String[] entryRestrictions = entry.getRestrictionNames();

        // Length check
        if (entryRestrictions.length != restrictions.size()) {
            return false;
        }

        // Key & entry check
        for (String key : entryRestrictions) {
            if (!restrictions.containsKey(key) || !entry.getRestriction(key).equals(restrictions.get(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check that the set of given restriction names is present in the given entry.
     *
     * @param entry entry to check
     * @param restrictions set of restrictions names to check
     * @return true if the entry's restrictions are all contained in the given set
     * @throws RepositoryException if the restrictions could not be obtained from the entry
     */
    private boolean entryHasRestrictions(JackrabbitAccessControlEntry entry, Set<String> restrictions)
        throws RepositoryException
    {
        // Note that we cannot easily obtain the restriction map from the entry
        String[] entryRestrictions = entry.getRestrictionNames();

        // Key & entry check
        for (String key : entryRestrictions) {
            if (!restrictions.contains(key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse privileges from their names.
     *
     * @param privilegeNames a list of privilege names
     * @param acm a reference to the AccessControlManager
     * @return an array of Privileges
     * @throws AccessControlException if no privilege with the specified name exists.
     * @throws RepositoryException if another error occurs
     */
    private static Privilege[] parsePrivileges(String[] privilegeNames, AccessControlManager acm)
        throws AccessControlException, RepositoryException
    {
        Privilege[] retval = new Privilege[privilegeNames.length];
        for (int i = 0; i < privilegeNames.length; i++) {
            retval[i] = acm.privilegeFromName(privilegeNames[i]);
        }
        return retval;
    }
}
