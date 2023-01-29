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

import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.permissions.spi.RestrictionFactory;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * Factory for {@link SubjectRestrictionPattern}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class SubjectRestrictionFactory implements RestrictionFactory
{
    /** @see #getName */
    public static final String NAME = "cards:subject";

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Override
    public RestrictionPattern forValue(PropertyState value)
    {
        Session session = null;
        if (this.rrp.getThreadResourceResolver() != null) {
            session = this.rrp.getThreadResourceResolver().adaptTo(Session.class);
        }
        return new SubjectRestrictionPattern(value.getValue(Type.STRING), session);
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Type<?> getType()
    {
        // FIXME This should be Type.REFERENCE, but the current method of testing this restriction only works with
        // strings
        return Type.STRING;
    }
}
