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
package io.uhndata.cards.permissions.internal;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.value.StringValue;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link PermissionsManagerService}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class PermissionsManagerServiceTest
{
    private static final String ROOT_PATH = "/";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private PermissionsManagerService permissionsManagerService;

    @Test
    public void addAccessControlEntryWithStringPrivileges() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String[] privileges = new String[]{Privilege.JCR_WRITE};
        this.permissionsManagerService.addAccessControlEntry(ROOT_PATH, false, () -> "admin", privileges,
                new HashMap<>(), session);

        AccessControlManager acm = session.getAccessControlManager();
        AccessControlPolicy[] policies = acm.getPolicies(ROOT_PATH);
        assertEquals(1, policies.length);
        assertTrue(policies[0] instanceof JackrabbitAccessControlList);
        assertEquals(1, ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries().length);

        AccessControlEntry accessControlEntry =
                ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries()[0];
        Privilege[] addedPrivileges = accessControlEntry.getPrivileges();
        assertEquals(1, addedPrivileges.length);
        assertEquals("jcr:write", addedPrivileges[0].getName());
    }

    @Test
    public void addAccessControlEntryWithPrivileges() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        AccessControlManager acm = session.getAccessControlManager();
        Privilege[] privileges = new Privilege[]{acm.privilegeFromName(Privilege.JCR_WRITE)};
        this.permissionsManagerService.addAccessControlEntry(ROOT_PATH, false, () -> "admin", privileges,
                new HashMap<>(), session);

        AccessControlPolicy[] policies = acm.getPolicies(ROOT_PATH);
        assertEquals(1, policies.length);
        assertTrue(policies[0] instanceof JackrabbitAccessControlList);
        assertEquals(1, ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries().length);

        AccessControlEntry accessControlEntry =
                ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries()[0];
        Privilege[] addedPrivileges = accessControlEntry.getPrivileges();
        assertEquals(1, addedPrivileges.length);
        assertEquals("jcr:write", addedPrivileges[0].getName());
    }

    @Test
    public void removeAccessControlEntryForObjectPrivilegesAndMatchedData() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        Privilege[] privileges = new Privilege[]{
                session.getAccessControlManager().privilegeFromName(Privilege.JCR_WRITE)
        };
        this.permissionsManagerService.addAccessControlEntry(ROOT_PATH, false, () -> "admin", privileges, Map.of(),
                session);
        AccessControlPolicy[] policies = session.getAccessControlManager().getPolicies(ROOT_PATH);
        AccessControlEntry[] accessControlEntries =
                ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries();
        assertEquals(1, accessControlEntries.length);

        this.permissionsManagerService.removeAccessControlEntry(ROOT_PATH, false,
                accessControlEntries[0].getPrincipal(), privileges, Map.of(), session);
        policies = session.getAccessControlManager().getPolicies(ROOT_PATH);
        assertEquals(0, ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries().length);
    }

    @Test
    public void removeAccessControlEntryForStringPrivilegesAndMatchedData() throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String[] privileges = new String[]{Privilege.JCR_WRITE};
        this.permissionsManagerService.addAccessControlEntry(ROOT_PATH, false, () -> "admin", privileges, Map.of(),
                session);
        AccessControlPolicy[] policies = session.getAccessControlManager().getPolicies(ROOT_PATH);
        AccessControlEntry[] accessControlEntries =
                ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries();
        assertEquals(1, accessControlEntries.length);

        this.permissionsManagerService.removeAccessControlEntry(ROOT_PATH, false,
                accessControlEntries[0].getPrincipal(), privileges, Map.of(), session);
        policies = session.getAccessControlManager().getPolicies(ROOT_PATH);
        assertEquals(0, ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries().length);
    }

    @Test
    public void removeAccessControlEntryForUnmatchedPrivilegesArraysThrowsRepositoryException()
            throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String[] privileges = new String[]{Privilege.JCR_WRITE};
        this.permissionsManagerService.addAccessControlEntry(ROOT_PATH, false, () -> "admin", privileges, Map.of(),
                session);
        AccessControlPolicy[] policies = session.getAccessControlManager().getPolicies(ROOT_PATH);
        AccessControlEntry[] accessControlEntries =
                ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries();
        assertEquals(1, accessControlEntries.length);

        String[] privilegesToRemove = new String[]{Privilege.JCR_READ};
        assertThrows(RepositoryException.class, () -> this.permissionsManagerService.removeAccessControlEntry(ROOT_PATH,
                false, accessControlEntries[0].getPrincipal(), privilegesToRemove, Map.of(), session));
    }

    @Test
    public void removeAccessControlEntryForUnmatchedPrivilegesArraysSizeThrowsRepositoryException()
            throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String[] privileges = new String[]{Privilege.JCR_WRITE};
        this.permissionsManagerService.addAccessControlEntry(ROOT_PATH, false, () -> "admin", privileges, Map.of(),
                session);
        AccessControlPolicy[] policies = session.getAccessControlManager().getPolicies(ROOT_PATH);
        AccessControlEntry[] accessControlEntries =
                ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries();
        assertEquals(1, accessControlEntries.length);

        String[] privilegesToRemove = new String[]{Privilege.JCR_WRITE, Privilege.JCR_READ};
        assertThrows(RepositoryException.class, () -> this.permissionsManagerService.removeAccessControlEntry(ROOT_PATH,
                false, accessControlEntries[0].getPrincipal(), privilegesToRemove, Map.of(), session));
    }

    @Test
    public void removeAccessControlEntryForUnmatchedRestrictionsArraysThrowsRepositoryException()
            throws RepositoryException
    {
        Session session = this.context.resourceResolver().adaptTo(Session.class);
        String[] privileges = new String[]{Privilege.JCR_WRITE};
        this.permissionsManagerService.addAccessControlEntry(ROOT_PATH, false, () -> "admin", privileges, Map.of(),
                session);
        AccessControlPolicy[] policies = session.getAccessControlManager().getPolicies(ROOT_PATH);
        AccessControlEntry[] accessControlEntries =
                ((JackrabbitAccessControlList) policies[0]).getAccessControlEntries();
        assertEquals(1, accessControlEntries.length);

        Map<String, Value> restrictions = Map.of("name", new StringValue("value"));
        assertThrows(RepositoryException.class, () -> this.permissionsManagerService.removeAccessControlEntry(ROOT_PATH,
                false, accessControlEntries[0].getPrincipal(), privileges, restrictions, session));
    }

}
