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
import javax.jcr.Value;
import javax.jcr.security.Privilege;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.builder.impl.SlingHttpServletRequestImpl;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.uhndata.cards.permissions.spi.PermissionsManager;

import static org.junit.Assert.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PermissionsManagerServlet}.
 *
 * @version $Id $
 */
@RunWith(MockitoJUnitRunner.class)
public class PermissionsManagerServletTest
{
    private static final String RULE_PARAMETER = ":rule";
    private static final String PRIVILEGES_PARAMETER = ":privileges";
    private static final String PRINCIPAL_PARAMETER = ":principal";
    private static final String RESTRICTION_PARAMETER = ":restriction";
    private static final String REMOVE_PARAMETER = ":remove";

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @InjectMocks
    private PermissionsManagerServlet permissionsManagerServlet;

    @Mock
    private PermissionsManager permissionsChangeServiceHandler;

    @Test
    public void doPostWithoutRemoveParameterAndAllowRule() throws ServletException
    {
        Resource resource = mock(Resource.class);
        when(resource.getResourceResolver()).thenReturn(this.context.resourceResolver());
        when(resource.getPath()).thenReturn("/Forms/f1.json");
        SlingHttpServletRequestImpl request = new SlingHttpServletRequestImpl(resource);
        request.withParameter(RULE_PARAMETER, "allow");
        request.withParameter(PRIVILEGES_PARAMETER, Privilege.JCR_ALL);
        request.withParameter(PRINCIPAL_PARAMETER, "admin");
        request.withParameter(RESTRICTION_PARAMETER, "cards:answer=" + UUID.randomUUID());
        this.permissionsManagerServlet.doPost(request.build(), mock(SlingHttpServletResponse.class));
    }

    @Test
    public void doPostWithRemoveParameterAndDenyRule() throws ServletException
    {
        Resource resource = mock(Resource.class);
        when(resource.getResourceResolver()).thenReturn(this.context.resourceResolver());
        when(resource.getPath()).thenReturn("/Forms/f1.json");
        SlingHttpServletRequestImpl request = new SlingHttpServletRequestImpl(resource);
        request.withParameter(RULE_PARAMETER, "deny");
        request.withParameter(PRIVILEGES_PARAMETER, Privilege.JCR_ALL);
        request.withParameter(PRINCIPAL_PARAMETER, "admin");
        request.withParameter(RESTRICTION_PARAMETER, "cards:answer=" + UUID.randomUUID());
        request.withParameter(REMOVE_PARAMETER, "true");
        this.permissionsManagerServlet.doPost(request.build(), mock(SlingHttpServletResponse.class));
    }

    @Test
    public void doPostWithoutRuleThrowsIllegalArgumentException()
    {
        Resource resource = mock(Resource.class);
        when(resource.getResourceResolver()).thenReturn(this.context.resourceResolver());
        when(resource.getPath()).thenReturn("/Forms/f1.json");
        SlingHttpServletRequestImpl request = new SlingHttpServletRequestImpl(resource);
        assertThrows("Required parameter \":rule\" missing", IllegalArgumentException.class,
                () -> this.permissionsManagerServlet.doPost(request.build(), mock(SlingHttpServletResponse.class)));
    }

    @Test
    public void doPostWithIllegalValueOfRuleThrowsIllegalArgumentException()
    {
        Resource resource = mock(Resource.class);
        when(resource.getResourceResolver()).thenReturn(this.context.resourceResolver());
        when(resource.getPath()).thenReturn("/Forms/f1.json");
        SlingHttpServletRequestImpl request = new SlingHttpServletRequestImpl(resource);
        request.withParameter(RULE_PARAMETER, "rule");
        assertThrows("\":rule\" must be either 'allow' or 'deny'", IllegalArgumentException.class,
                () -> this.permissionsManagerServlet.doPost(request.build(), mock(SlingHttpServletResponse.class)));
    }

    @Test
    public void doPostWithoutRestrictionThrowsIllegalArgumentException()
    {
        Resource resource = mock(Resource.class);
        when(resource.getResourceResolver()).thenReturn(this.context.resourceResolver());
        when(resource.getPath()).thenReturn("/Forms/f1.json");
        SlingHttpServletRequestImpl request = new SlingHttpServletRequestImpl(resource);
        request.withParameter(RULE_PARAMETER, "deny");
        request.withParameter(PRIVILEGES_PARAMETER, Privilege.JCR_ALL);
        assertThrows("Required parameter \":restriction\" missing", IllegalArgumentException.class,
                () -> this.permissionsManagerServlet.doPost(request.build(), mock(SlingHttpServletResponse.class)));
    }

    @Test
    public void doPostWithoutPrivilegesThrowsIllegalArgumentException()
    {
        Resource resource = mock(Resource.class);
        when(resource.getResourceResolver()).thenReturn(this.context.resourceResolver());
        when(resource.getPath()).thenReturn("/Forms/f1.json");
        SlingHttpServletRequestImpl request = new SlingHttpServletRequestImpl(resource);
        request.withParameter(RULE_PARAMETER, "deny");
        request.withParameter(RESTRICTION_PARAMETER, "cards:answer=" + UUID.randomUUID());
        assertThrows("Required parameter \":privileges\" missing", IllegalArgumentException.class,
                () -> this.permissionsManagerServlet.doPost(request.build(), mock(SlingHttpServletResponse.class)));
    }

    @Test
    public void doPostCatchesRepositoryException() throws ServletException, RepositoryException
    {
        Resource resource = mock(Resource.class);
        when(resource.getResourceResolver()).thenReturn(this.context.resourceResolver());
        when(resource.getPath()).thenReturn("/Forms/f1.json");
        SlingHttpServletRequestImpl request = new SlingHttpServletRequestImpl(resource);
        request.withParameter(RULE_PARAMETER, "allow");
        request.withParameter(PRIVILEGES_PARAMETER, Privilege.JCR_ALL);
        request.withParameter(PRINCIPAL_PARAMETER, "admin");
        request.withParameter(RESTRICTION_PARAMETER, "cards:answer=" + UUID.randomUUID());
        doThrow(new RepositoryException()).when(this.permissionsChangeServiceHandler).addAccessControlEntry(
            anyString(), anyBoolean(), any(Principal.class), any(String[].class), anyMapOf(String.class, Value.class),
            any(Session.class));
        this.permissionsManagerServlet.doPost(request.build(), mock(SlingHttpServletResponse.class));
    }

}
