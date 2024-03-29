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
package io.uhndata.cards.auth.token.impl.oak;

import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.spi.security.ConfigurationBase;
import org.apache.jackrabbit.oak.spi.security.SecurityConfiguration;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenConfiguration;
import org.apache.jackrabbit.oak.spi.security.authentication.token.TokenProvider;
import org.osgi.service.component.annotations.Component;

/**
 * Custom TokenConfiguration implementation which returns the CARDS-specific token provider.
 *
 * @version $Id$
 */
@Component(
    service = { TokenConfiguration.class, SecurityConfiguration.class },
    property = "service.ranking:Integer=0")
public class CardsTokenConfigurationImpl extends ConfigurationBase implements TokenConfiguration
{
    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public TokenProvider getTokenProvider(Root root)
    {
        return new CardsTokenProvider(root);
    }
}
