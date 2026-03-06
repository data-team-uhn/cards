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
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;

import jakarta.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * An optional {@link ResourceJsonProcessor} applicable to any type of resource. This processor targets
 * nodes and excludes their properties if their value is the default value or an obvious non-value.
 * The name of this processor is {@code excludeDefaultProperties}.
 *
 * Property exclusion logic:
 * <ul>
 *   <li>If a property's value matches its JCR-defined default value, the property is excluded from the
 *       JSON output.</li>
 *   <li>If a property does not have a JCR-defined default value:
 *     <ul>
 *       <li>It is excluded if it's a boolean type property with a value of {@code false}.</li>
 *       <li>It is excluded if it's a String type property with an empty value ({@code ""}).</li>
 *     </ul>
 *   </li>
 *   <li>Otherwise, the property is included.</li>
 * </ul>
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
    public String getDescription()
    {
        return "Exclude properties if their value is the default value or an obvious non-value:\n"
            + " - If a property's value matches its defined default value\n"
            + " - If it's a `false` boolean\n"
            + " - If it's an empty string `\"\"`";
    }

    @Override
    public int getPriority()
    {
        return 56;
    }

    @Override
    public JsonValue processProperty(Node node, Property property, JsonValue jsonValue,
        Function<Node, JsonValue> function)
    {
        try {
            // Initialize defaults map for the node if not already done
            Map<String, Object[]> nodeDefaultsMap = getOrCreateDefaultsMap(node);

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
            if (!property.isMultiple() && defaultValues.length == 1) {
                // Excluding property
                return defaultValues[0].equals(actualValue) ? null : jsonValue;
            }

            // Property is multiple, check if all values match the default values
            Object[] actualValues = (Object[]) actualValue;
            // Excluding property if all values match the default values
            if (Arrays.equals(defaultValues, actualValues)) {
                return null;
            }
        } catch (RepositoryException e) {
            // Shouldn't happen
        }

        return jsonValue;
    }

    // If we haven't fetched the default values for this node yet, do it now and return
    private Map<String, Object[]> getOrCreateDefaultsMap(Node node) throws RepositoryException
    {
        Map<String, Object[]> nodeDefaultsMap = this.defaultsMap.get().get(node.getPrimaryNodeType());
        if (nodeDefaultsMap != null) {
            return nodeDefaultsMap;
        }

        nodeDefaultsMap = new HashMap<>();
        PropertyDefinition[] propDefs = node.getPrimaryNodeType().getPropertyDefinitions();

        for (PropertyDefinition def : propDefs) {
            final String propName = def.getName();
            final Value[] defaultJcrValues = def.getDefaultValues();

            if (defaultJcrValues != null && defaultJcrValues.length > 0) {
                Object[] result = Arrays.stream(defaultJcrValues)
                        .map(this.formUtils::getValue)
                        .toArray(Object[]::new);

                nodeDefaultsMap.putIfAbsent(propName, result);
            }
        }
        this.defaultsMap.get().put(node.getPrimaryNodeType(), nodeDefaultsMap);
        return nodeDefaultsMap;
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

    @Override
    public void end(Resource resource)
    {
        this.defaultsMap.remove();
    }
}
