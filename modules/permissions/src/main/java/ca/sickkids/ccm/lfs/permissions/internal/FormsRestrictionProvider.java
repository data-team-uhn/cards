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
package ca.sickkids.ccm.lfs.permissions.internal;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.AbstractRestrictionProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.CompositePattern;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.Restriction;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionDefinitionImpl;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.sickkids.ccm.lfs.permissions.spi.RestrictionFactory;

/**
 * A restrictions provider specific to the LFS data model, which allows to specify global, fine-grained permissions for
 * forms as a whole, or just to specific answers on a form.
 *
 * @version $Id$
 */
@Component(service = { RestrictionProvider.class })
public class FormsRestrictionProvider extends AbstractRestrictionProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FormsRestrictionProvider.class);

    /**
     * Automatically injected list of all available restriction factories.
     */
    private List<RestrictionFactory> factories;

    /**
     * Default constructor.
     *
     * @param factories the list of known restriction factories, provided by the component manager
     */
    @Activate
    public FormsRestrictionProvider(
        @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE) List<RestrictionFactory> factories)
    {
        // FIXME this isn't dynamic at all. This is created after the first RestrictionFactory becomes available,
        // ignoring all others. The problem is that AbstractRestrictionProvider can only work with the definitions
        // provided in the constructor at the moment, but that can easily change. To fix this, instead of extending
        // AbstractRestrictionProvider copy the code from that class in here, let #factories be a dynamic reference, and
        // make sure that all the code from AbstractRestrictionProvider is adapted to work with this dynamic list.
        super(factories.stream()
            .map(f -> new RestrictionDefinitionImpl(f.getName(), f.getType(), false))
            .collect(Collectors.toMap(RestrictionDefinitionImpl::getName, f -> f)));
        this.factories = factories;
    }

    @Override
    public RestrictionPattern getPattern(String oakPath, Set<Restriction> restrictions)
    {
        if (oakPath == null || restrictions.isEmpty()) {
            return RestrictionPattern.EMPTY;
        } else {
            return CompositePattern.create(
                restrictions.stream()
                    .map(this::processRestriction)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
    }

    @Override
    public RestrictionPattern getPattern(String oakPath, Tree tree)
    {
        // This method is never actually called, since up in the call stack all potential calls are transformed into
        // calls to the other getPattern method above.
        return RestrictionPattern.EMPTY;
    }

    private RestrictionFactory getFactory(String restrictionName)
    {
        return this.factories.stream().filter(f -> f.getName().equals(restrictionName)).findFirst()
            .orElse(null);
    }

    private RestrictionPattern processRestriction(Restriction restriction)
    {
        String name = restriction.getDefinition().getName();
        RestrictionFactory factory = getFactory(name);
        if (factory != null) {
            return factory.forValue(restriction.getProperty());
        } else {
            LOGGER.error("Ignoring unsupported restriction {}", name);
        }
        return null;
    }
}
