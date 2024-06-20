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
package io.uhndata.cards.scripting;

import javax.script.Bindings;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link StatusCodeSetter}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class StatusCodeSetterTest
{
    private static final int LOCKED = 423;
    @InjectMocks
    private StatusCodeSetter statusCodeSetter;

    @Mock
    private SlingHttpServletResponse response;

    @Test
    public void initGetsResponseKeyFromBindings()
    {
        Bindings bindings = mock(Bindings.class);
        this.statusCodeSetter.init(bindings);
        verify(bindings, times(1)).get("response");
    }

    @Test
    public void okSetsOkStatus()
    {
        this.statusCodeSetter.ok();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void createdSetsCreatedStatus()
    {
        this.statusCodeSetter.created();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_CREATED);
    }

    @Test
    public void acceptedSetsAcceptedStatus()
    {
        this.statusCodeSetter.accepted();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    @Test
    public void noContentNoContentSetsStatus()
    {
        this.statusCodeSetter.noContent();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    public void badRequestSetsBadRequestStatus()
    {
        this.statusCodeSetter.badRequest();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    public void unauthorizedSetsUnauthorizedStatus()
    {
        this.statusCodeSetter.unauthorized();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void forbiddenSetsForbiddenStatus()
    {
        this.statusCodeSetter.forbidden();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void notFoundSetsNotFoundStatus()
    {
        this.statusCodeSetter.notFound();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void methodNotAllowedSetsMethodNotAllowedStatus()
    {
        this.statusCodeSetter.methodNotAllowed();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    public void notAcceptableSetsNotAcceptableStatus()
    {
        this.statusCodeSetter.notAcceptable();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
    }

    @Test
    public void conflictSetsConflictStatus()
    {
        this.statusCodeSetter.conflict();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_CONFLICT);
    }

    @Test
    public void lockedSetsLockedStatus()
    {
        this.statusCodeSetter.locked();
        verify(this.response, times(1)).setStatus(LOCKED);
    }

    @Test
    public void internalServerErrorSetsInternalServerErrorStatus()
    {
        this.statusCodeSetter.internalServerError();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void notImplementedSetsNotImplementedStatus()
    {
        this.statusCodeSetter.notImplemented();
        verify(this.response, times(1)).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
    }

}
