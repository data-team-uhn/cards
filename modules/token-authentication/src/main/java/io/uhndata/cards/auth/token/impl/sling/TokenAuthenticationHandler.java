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
package io.uhndata.cards.auth.token.impl.sling;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jackrabbit.api.security.authentication.token.TokenCredentials;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenInfo;
import org.apache.sling.auth.core.AuthConstants;
import org.apache.sling.auth.core.AuthUtil;
import org.apache.sling.auth.core.spi.AuthenticationHandler;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.auth.core.spi.DefaultAuthenticationFeedbackHandler;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.auth.token.TokenManager;
import io.uhndata.cards.auth.token.impl.CardsTokenImpl;

/**
 * Implements the Sling part of token authentication, reading authentication data from the request and passing the
 * extracted credentials to Oak for checking.
 *
 * @version $Id$
 */
@Component(service = AuthenticationHandler.class, immediate = true, property = {
    AuthenticationHandler.TYPE_PROPERTY + "=" + HttpServletRequest.FORM_AUTH,
    "path=/",
    "sling.auth.requirements=-/Expired" })
public class TokenAuthenticationHandler extends DefaultAuthenticationFeedbackHandler implements AuthenticationHandler
{
    /** The name of the request parameter that may hold a login token. */
    private static final String TOKEN_REQUEST_PARAMETER = "auth_token";

    /** The name of the cookie that may hold a login token. */
    private static final String TOKEN_COOKIE_NAME = "cards_auth_token";

    @Reference
    private TokenManager tokenManager;

    @Override
    public AuthenticationInfo extractCredentials(HttpServletRequest request, HttpServletResponse response)
    {
        AuthenticationInfo info = null;

        // 1. Try credentials from request parameters
        info = this.extractRequestParameterAuthentication(request, response);

        // 2. Try credentials from a cookie
        if (info == null) {
            info = this.extractCookieAuthentication(request, response);
        }

        return info;
    }

    @Override
    public boolean requestCredentials(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        // False means that this module cannot request credentials, let another one do it
        return false;
    }

    @Override
    public void dropCredentials(HttpServletRequest request, HttpServletResponse response)
    {
        if (request.getCookies() == null) {
            return;
        }
        final Cookie existingCookie = Arrays.asList(request.getCookies())
            .stream()
            .filter(cookie -> TOKEN_COOKIE_NAME.equals(cookie.getName()))
            .findFirst()
            .orElse(null);
        if (existingCookie != null) {
            response.reset();
            final Cookie eraseCookie = new Cookie(TOKEN_COOKIE_NAME, "");
            eraseCookie.setMaxAge(0);
            eraseCookie.setHttpOnly(true);
            final String ctxPath = request.getContextPath();
            final String cookiePath = (ctxPath == null || ctxPath.length() == 0) ? "/" : ctxPath;
            eraseCookie.setPath(cookiePath);
            response.addCookie(eraseCookie);
        }
    }

    @Override
    public boolean authenticationSucceeded(HttpServletRequest request, HttpServletResponse response,
        AuthenticationInfo authInfo)
    {
        // This method is called if this handler provided credentials which successfully logged in into the repository.
        // Store the token as a cookie for future requests.
        final TokenCredentials credentials =
            (TokenCredentials) authInfo.get(JcrResourceConstants.AUTHENTICATION_INFO_CREDENTIALS);
        final Cookie cookie = new Cookie(TOKEN_COOKIE_NAME, credentials.getToken());
        final String ctxPath = request.getContextPath();
        final String cookiePath = (ctxPath == null || ctxPath.length() == 0) ? "/" : ctxPath;
        final Calendar cookieExpiration = getTokenExpirationDate(credentials.getToken());
        cookie.setPath(cookiePath);
        cookie.setHttpOnly(true);
        if (cookieExpiration != null) {
            cookie.setMaxAge((int) ChronoUnit.SECONDS.between(Instant.now(), cookieExpiration.toInstant()));
        }
        response.addCookie(cookie);

        // True means that the request is complete, no further processing needed;
        // False means that we just validated the authentication, let the request be processed as usual by the rest of
        // the platform
        return false;
    }

    /**
     * Given a login token, determine its expiration date by examining the identified cards:Token object.
     *
     * @param loginToken the login token to parse
     * @return a {@code Calendar} object representing the time that the cookie will expire, or {@code null} otherwise
     */
    private Calendar getTokenExpirationDate(final String loginToken)
    {
        final TokenInfo token = this.tokenManager.parse(loginToken);
        if (token == null || !(token instanceof CardsTokenImpl)) {
            return null;
        }
        final CardsTokenImpl cardsToken = (CardsTokenImpl) token;
        return cardsToken.getExpirationTime();
    }

    @Override
    public void authenticationFailed(HttpServletRequest request, HttpServletResponse response,
        AuthenticationInfo authInfo)
    {
        showError(request, response);
    }

    @Override
    public String toString()
    {
        return "Token Based Authentication Handler";
    }

    /**
     * Look for a login token sent as a request parameter, process it into a token object, and store it in the request
     * for Oak to use when creating a user session.
     *
     * @param request the current user request
     * @return an AuthenticationInfo with TokenCredentials in it, if a valid login token was sent in the request, or
     *         {@code null} otherwise
     */
    private AuthenticationInfo extractRequestParameterAuthentication(final HttpServletRequest request,
        final HttpServletResponse response)
    {
        // The login token may be sent as a request parameter
        final String loginToken = request.getParameter(TOKEN_REQUEST_PARAMETER);
        return processLoginToken(loginToken, true, request, response);
    }

    /**
     * Look for a login token sent as a cookie, process it into a token object, and store it in the request for Oak to
     * use when creating a user session.
     *
     * @param request the current user request
     * @return an AuthenticationInfo with TokenCredentials in it, if a valid login token was sent in the request, or
     *         {@code null} otherwise
     */
    private AuthenticationInfo extractCookieAuthentication(final HttpServletRequest request,
        final HttpServletResponse response)
    {
        // The login token may be sent as a request cookie, let's look for it
        final Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        final String loginToken = Arrays.asList(cookies)
            .stream()
            .filter(cookie -> TOKEN_COOKIE_NAME.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);

        return processLoginToken(loginToken, false, request, response);
    }

    /**
     * Process a login token string into a token object, and, if valid, store new TokenCredentials in the request.
     *
     * @param loginToken a string with a login token, may be {@code null}
     * @param isLogin whether this is an initial login or a subsequent request
     * @param request the current request
     * @return an AuthenticationInfo with TokenCredentials in it, if a valid login token was sent in the request, or
     *         {@code null} otherwise
     */
    private AuthenticationInfo processLoginToken(final String loginToken, final boolean isLogin,
        final HttpServletRequest request, final HttpServletResponse response)
    {
        // Try to parse it
        final TokenInfo token = this.tokenManager.parse(loginToken);
        // If it is not a valid token, then trying to parse it would return null
        if (token == null) {
            if (loginToken != null) {
                // A token was present, but not valid; let the user know that the link expired
                showError(request, response);
            }
            return null;
        }
        // Looks like a valid token, let's pass it to Oak for a proper check, including checking the secret key
        final AuthenticationInfo info = new AuthenticationInfo(HttpServletRequest.FORM_AUTH, token.getUserId());
        info.put(JcrResourceConstants.AUTHENTICATION_INFO_CREDENTIALS, new TokenCredentials(loginToken));
        if (isLogin) {
            // The presence of AUTH_INFO_LOGIN marks that this is an initial login, and will cause Sling to send a
            // TOPIC_LOGIN event
            info.put(AuthConstants.AUTH_INFO_LOGIN, new Object());
        }
        AuthUtil.setLoginResourceAttribute(request, request.getContextPath());
        return info;
    }

    /**
     * If the token sent in the request is not valid, let the user know that it expired by redirecting to a special
     * page. The response is configured as a redirect and finalized.
     *
     * @param request the current request
     * @param response the current response
     */
    private void showError(final HttpServletRequest request, final HttpServletResponse response)
    {
        // Signal the reason for login failure
        request.setAttribute(FAILURE_REASON, "Invalid token");
        dropCredentials(request, response);
        try {
            response.sendRedirect(request.getContextPath() + "/Expired.html");
        } catch (IOException e) {
            // Should not happen, but is not critical anyway
        }
    }
}
