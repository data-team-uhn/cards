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

import java.security.Principal;
import java.util.UUID;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.Privilege;

import jakarta.servlet.ServletException;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.request.builder.SlingHttpServletRequestBuilder;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.uhndata.cards.permissions.spi.PermissionsManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PermissionsManagerServlet}.
 *
 * @version $Id$
 */
public class PermissionsManagerServletTest
{
    private static final String RULE_PARAMETER = ":rule";

    private static final String PRIVILEGES_PARAMETER = ":privileges";

    private static final String PRINCIPAL_PARAMETER = ":principal";

    private static final String RESTRICTION_PARAMETER = ":restriction";

    private static final String REMOVE_PARAMETER = ":remove";

    private static final String TARGET_PATH = "/Forms/f1";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private final PermissionsManagerServlet permissionsManagerServlet = new PermissionsManagerServlet();

    private final PermissionsManager permissionsManager = mock(PermissionsManager.class);

    private final SlingJakartaHttpServletResponse response = mock(SlingJakartaHttpServletResponse.class);

    @Before
    public void setUp() throws IllegalAccessException
    {
        FieldUtils.writeField(this.permissionsManagerServlet, "permissionsChangeServiceHandler",
            this.permissionsManager, true);
    }

    @Test
    public void doPostWithoutRemoveParameterAddsEntry() throws RepositoryException, ServletException
    {
        SlingJakartaHttpServletRequest request = requestBuilder()
            .withParameter(RULE_PARAMETER, "allow")
            .withParameter(PRIVILEGES_PARAMETER, Privilege.JCR_ALL)
            .withParameter(PRINCIPAL_PARAMETER, "admin")
            .withParameter(RESTRICTION_PARAMETER, "cards:answer=" + UUID.randomUUID())
            .buildJakartaRequest();
        this.permissionsManagerServlet.doPost(request, this.response);
        verify(this.permissionsManager).addAccessControlEntry(eq(TARGET_PATH), eq(true), any(Principal.class),
            eq(new String[] { Privilege.JCR_ALL }), anyMap(), any(Session.class));
    }

    @Test
    public void doPostWithRemoveParameterRemovesEntry() throws RepositoryException, ServletException
    {
        SlingJakartaHttpServletRequest request = requestBuilder()
            .withParameter(RULE_PARAMETER, "deny")
            .withParameter(PRIVILEGES_PARAMETER, Privilege.JCR_ALL)
            .withParameter(PRINCIPAL_PARAMETER, "admin")
            .withParameter(RESTRICTION_PARAMETER, "cards:answer=" + UUID.randomUUID())
            .withParameter(REMOVE_PARAMETER, "true")
            .buildJakartaRequest();
        this.permissionsManagerServlet.doPost(request, this.response);
        verify(this.permissionsManager).removeAccessControlEntry(eq(TARGET_PATH), eq(false), any(Principal.class),
            eq(new String[] { Privilege.JCR_ALL }), anyMap(), any(Session.class));
    }

    @Test
    public void doPostWithoutRuleThrowsIllegalArgumentException()
    {
        SlingJakartaHttpServletRequest request = requestBuilder().buildJakartaRequest();
        Exception e = assertThrows(IllegalArgumentException.class,
            () -> this.permissionsManagerServlet.doPost(request, this.response));
        assertEquals("Required parameter \":rule\" missing", e.getMessage());
    }

    @Test
    public void doPostWithIllegalValueOfRuleThrowsIllegalArgumentException()
    {
        SlingJakartaHttpServletRequest request = requestBuilder()
            .withParameter(RULE_PARAMETER, "rule")
            .buildJakartaRequest();
        Exception e = assertThrows(IllegalArgumentException.class,
            () -> this.permissionsManagerServlet.doPost(request, this.response));
        assertEquals("\":rule\" must be either 'allow' or 'deny'", e.getMessage());
    }

    @Test
    public void doPostWithoutRestrictionThrowsIllegalArgumentException()
    {
        SlingJakartaHttpServletRequest request = requestBuilder()
            .withParameter(RULE_PARAMETER, "deny")
            .withParameter(PRIVILEGES_PARAMETER, Privilege.JCR_ALL)
            .buildJakartaRequest();
        Exception e = assertThrows(IllegalArgumentException.class,
            () -> this.permissionsManagerServlet.doPost(request, this.response));
        assertEquals("Required parameter \":restriction\" missing", e.getMessage());
    }

    @Test
    public void doPostWithoutPrivilegesThrowsIllegalArgumentException()
    {
        SlingJakartaHttpServletRequest request = requestBuilder()
            .withParameter(RULE_PARAMETER, "deny")
            .withParameter(RESTRICTION_PARAMETER, "cards:answer=" + UUID.randomUUID())
            .buildJakartaRequest();
        Exception e = assertThrows(IllegalArgumentException.class,
            () -> this.permissionsManagerServlet.doPost(request, this.response));
        assertEquals("Required parameter \":privileges\" missing", e.getMessage());
    }

    @Test
    public void doPostCatchesRepositoryException() throws RepositoryException, ServletException
    {
        SlingJakartaHttpServletRequest request = requestBuilder()
            .withParameter(RULE_PARAMETER, "allow")
            .withParameter(PRIVILEGES_PARAMETER, Privilege.JCR_ALL)
            .withParameter(PRINCIPAL_PARAMETER, "admin")
            .withParameter(RESTRICTION_PARAMETER, "cards:answer=" + UUID.randomUUID())
            .buildJakartaRequest();
        doThrow(new RepositoryException()).when(this.permissionsManager).addAccessControlEntry(
            anyString(), anyBoolean(), any(Principal.class), any(String[].class), anyMap(),
            any(Session.class));
        this.permissionsManagerServlet.doPost(request, this.response);
    }

    private SlingHttpServletRequestBuilder requestBuilder()
    {
        Resource resource = mock(Resource.class);
        when(resource.getResourceResolver()).thenReturn(this.context.resourceResolver());
        when(resource.getPath()).thenReturn(TARGET_PATH);
        return Builders.newRequestBuilder(resource).withExtension("json");
    }
}
