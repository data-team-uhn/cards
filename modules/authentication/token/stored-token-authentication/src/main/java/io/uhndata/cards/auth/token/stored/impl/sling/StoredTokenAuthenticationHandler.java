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
package io.uhndata.cards.auth.token.stored.impl.sling;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sling.auth.core.spi.JakartaAuthenticationHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.auth.token.sling.AbstractTokenAuthenticationHandler;

/**
 * Implements the Sling part of node-based token authentication, reading authentication data from the request and
 * passing the extracted credentials to Oak for checking.
 *
 * @version $Id$
 */
@Component(service = JakartaAuthenticationHandler.class, immediate = true, property = {
    JakartaAuthenticationHandler.TYPE_PROPERTY + "=" + HttpServletRequest.FORM_AUTH,
    "path=/" })
public class StoredTokenAuthenticationHandler extends AbstractTokenAuthenticationHandler
{
    @Reference(target = "(component.name=io.uhndata.cards.auth.token.stored.impl.CardsStoredTokenManagerImpl)")
    private TokenManager tokenManager;

    @Override
    protected TokenManager getTokenManager()
    {
        return this.tokenManager;
    }

    @Override
    protected boolean isValidTokenString(final String token)
    {
        // Node tokens consist of a UUID and a hexadecimal key separated by an `_`
        return token != null && token.matches(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_[0-9a-f]+$");
    }

    @Override
    public String toString()
    {
        return "Stored Token Based Authentication Handler";
    }
}
