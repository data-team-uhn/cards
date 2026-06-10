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
package io.uhndata.cards.auth.token;

import java.util.Calendar;

import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenInfo;

/**
 * Custom extension of {@link TokenInfo} with support for retrieving the token expiration time.
 *
 * @version $Id$
 */
public interface CardsToken extends TokenInfo
{
    /**
     * Obtain the expiration time from this token.
     *
     * @return the expiration date, or {@code null} if there's no expiration date set
     */
    Calendar getExpirationTime();
}
