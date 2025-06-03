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
package io.uhndata.cards.serialize.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * An optional {@link ResourceJsonProcessor} that processes {@code cards/Questionnaire} resources.
 * This processor targets nodes within the questionnaire and excludes properties if their value is the default value
 * or obvious non-values. The name of this processor is {@code excludeDefaultProperties}.
 *
 * Property exclusion logic for a node properties:
 * <p>
 * <ul>
 *   <li>If a property's value matches its JCR-defined default value, the property is excluded from JSON output.</li>
 *   <li>If a property does not have a JCR-defined default value:
 *     <ul>
 *       <li>It is excluded if it's a boolean type property with a value of {@code false}.</li>
 *       <li>It is excluded if it's a String type property with an empty value ({@code ""}).</li>
 *     </ul>
 *   </li>
 *   <li>Otherwise, the property is included.</li>
 * </ul>
 * </p>
 *
 *  @version $Id$
**/
@Component(immediate = true)
public class ExcludeDefaultPropertiesProcessor implements ResourceJsonProcessor
{
    @Reference
    protected FormUtils formUtils;

    private ThreadLocal<Map<NodeType, Map<String, Object[]>>> defaultsMap = ThreadLocal.withInitial(HashMap::new);

    @Override
    public String getName()
    {
        return "excludeDefaultProperties";
    }

    @Override
    public int getPriority()
    {
        return 56;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        return resource.isResourceType("cards/Questionnaire");
    }

    @Override
    public JsonValue processProperty(Node node, Property property, JsonValue jsonValue,
        Function<Node, JsonValue> function)
    {
        try {
            // Initialize defaults map for the node if not already done
            initializeDefaultsMap(node);

            Map<String, Object[]> nodeDefaultsMap = this.defaultsMap.get().get(node.getPrimaryNodeType());
            final String propertyName = property.getName();
            Object[] defaultValues = nodeDefaultsMap.get(propertyName);

            // No JCR default defined for this property, apply special exclusion rules
            if (defaultValues == null) {
                return handleNoDefaultValues(property, jsonValue);
            }

            Object actualValue = this.formUtils.getValue(property);
            if (actualValue == null) {
                // If the property has no value, we can exclude it
                return null;
            }

            // Property is single valued, compare the actual value with the default value
            if (!property.isMultiple()) {
                // Excluding property
                return defaultValues[0].equals(actualValue) ? null : jsonValue;
            }

            // Property is multiple, check if all values match the default values
            Object[] actualValues = (Object[]) actualValue;
            // Excluding property if all values match the default values
            if (areArraysEqualAsSets(defaultValues, actualValues)) {
                return null;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }

        return jsonValue;
    }

    // If we haven't fetched the default values for this node yet, do it now
    private void initializeDefaultsMap(Node node) throws RepositoryException
    {
        if (this.defaultsMap.get().get(node.getPrimaryNodeType()) != null) {
            return;
        }

        Map<String, Object[]> nodeDefaultsMap = new HashMap<>();
        PropertyDefinition[] propDefs = node.getPrimaryNodeType().getPropertyDefinitions();

        for (PropertyDefinition def : propDefs) {
            final String propName = def.getName();
            final Value[] defaultJcrValues = def.getDefaultValues();

            if (defaultJcrValues != null && defaultJcrValues.length > 0) {
                Object[] result = Arrays.stream(defaultJcrValues)
                        .map(this.formUtils::getValue)
                        .toArray(Object[]::new);

                nodeDefaultsMap.put(propName, result);
            }
        }
        this.defaultsMap.get().put(node.getPrimaryNodeType(), nodeDefaultsMap);
    }

    private JsonValue handleNoDefaultValues(Property property, JsonValue jsonValue) throws RepositoryException
    {
        // If the property is single-valued, we can exclude it based on its type and value
        if (!property.isMultiple()) {
            // Exclude false boolean with no default
            if (property.getType() == PropertyType.BOOLEAN && !property.getBoolean()) {
                return null;
            }
            // Exclude empty string with no default
            if (property.getType() == PropertyType.STRING && property.getString().isEmpty()) {
                return null;
            }
        }
        return jsonValue;
    }

    private boolean areArraysEqualAsSets(Object[] array1, Object[] array2)
    {
        if (array1.length != array2.length) {
            return false;
        }

        return new HashSet<>(Arrays.asList(array1)).equals(new HashSet<>(Arrays.asList(array2)));
    }

    @Override
    public void end(Resource resource)
    {
        this.defaultsMap = ThreadLocal.withInitial(HashMap::new);
    }
}
