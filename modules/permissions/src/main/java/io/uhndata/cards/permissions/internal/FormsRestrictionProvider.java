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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.jackrabbit.oak.api.PropertyState;
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
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.permissions.spi.RestrictionFactory;

/**
 * A restrictions provider specific to the CARDS data model, which allows to specify global, fine-grained permissions
 * for forms as a whole, or just to specific answers on a form.
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
        @Reference(policyOption = ReferencePolicyOption.GREEDY) List<RestrictionFactory> factories)
    {
        super(factories.stream()
            .map(f -> new RestrictionDefinitionImpl(f.getName(), f.getType(), false))
            .collect(Collectors.toMap(RestrictionDefinitionImpl::getName, Function.identity())));
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
        if (oakPath == null) {
            return RestrictionPattern.EMPTY;
        }

        // NB: tree.getProperties() returns more than just restrictions
        // processRestriction should return null on non-restrictions e.g.
        // PropertyState jcr:primaryType
        return CompositePattern.create(
            StreamSupport.stream(tree.getProperties().spliterator(), false)
                .map(this::processRestriction)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
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
            LOGGER.debug("Ignoring unsupported restriction {}", name);
        }
        return null;
    }

    private RestrictionPattern processRestriction(PropertyState property)
    {
        String name = property.getName();
        RestrictionFactory factory = getFactory(name);
        if (factory != null) {
            return factory.forValue(property);
        } else {
            LOGGER.debug("Ignoring unsupported restriction {}", name);
        }
        return RestrictionPattern.EMPTY;
    }
}
