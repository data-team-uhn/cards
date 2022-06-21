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
package io.uhndata.cards.heracles.internal.serialize;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Obfuscate all date fields by serializing them as a relative offset from a specific reference date. The reference date
 * is defined in an environment variable called {@code REFERENCE_DATE}. The name of this processor is
 * {@code relativeDates}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DateObfuscationProcessor implements ResourceJsonProcessor
{
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final SimpleDateFormat MONTH_YEAR_DATE_FORMAT = new SimpleDateFormat("MM-yyyy");

    private static final Logger LOGGER = LoggerFactory.getLogger(DateObfuscationProcessor.class);

    private final ThreadLocal<Map<String, Long>> dates = ThreadLocal.withInitial(HashMap::new);

    private final ThreadLocal<Boolean> bareExport = new ThreadLocal<>();

    private final ThreadLocal<String> rootNode = new ThreadLocal<>();

    private Instant baseDate;

    @Override
    public String getName()
    {
        return "relativeDates";
    }

    @Override
    public int getPriority()
    {
        return 100;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        return true;
    }

    @Activate
    public void activate()
    {
        String env = System.getenv("REFERENCE_DATE");
        if (env != null) {
            try {
                this.baseDate = DATE_FORMAT.parse(env).toInstant();
            } catch (ParseException e) {
                LOGGER.error("Reference date is invalid, expected format is yyyy-MM-dd, found {}", env);
            }
        } else {
            LOGGER.error("No reference date is set, dates will not be included in the JSON export");
        }

    }

    @Override
    public void start(Resource resource)
    {
        // It is not nice to explicitly overlap another JSON processor, but this one is very special:
        // if this is a bare export, we must replace the "created" field added by the bare processor with a differential
        this.bareExport.set(
            Arrays.asList(resource.getResourceMetadata().getResolutionPathInfo().split("(?<!\\\\)(?:\\\\\\\\)*\\."))
                .contains("bare"));
        this.rootNode.set(resource.getPath());
    }

    @Override
    public JsonValue processProperty(Node node, Property property, JsonValue input,
        Function<Node, JsonValue> serializeNode)
    {
        try {
            if (input != null && property.getType() == PropertyType.DATE && property.getDate() != null) {
                // The date of birth gets special treatment: instead of obfuscating it, just output month and year
                if (node.hasProperty("question") && "/Questionnaires/Participant Status/Demographics/date of birth"
                    .equals(node.getProperty("question").getNode().getPath()) && "value".equals(property.getName())) {
                    return Json.createValue(MONTH_YEAR_DATE_FORMAT.format(property.getDate().getTime()));
                }

                if (this.baseDate != null) {
                    this.dates.get().put(property.getPath(),
                        this.baseDate.until(property.getDate().toInstant(), ChronoUnit.DAYS));
                } else {
                    this.dates.get().put(property.getPath(), null);
                }
                return null;
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to access property {}: {}", property, e.getMessage(), e);
        }
        return input;
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            final PropertyIterator properties = node.getProperties();
            while (properties.hasNext()) {
                Property property = properties.nextProperty();
                if (property.getType() == PropertyType.DATE && this.dates.get().containsKey(property.getPath())) {
                    Long value = this.dates.get().get(property.getPath());
                    json.add("@" + property.getName() + "_differential",
                        value != null ? Json.createValue(value) : JsonValue.NULL);
                }
            }
            json.remove("created");
        } catch (RepositoryException e) {
            LOGGER.warn("Failed to access properties of {}: {}", node, e.getMessage(), e);
        }
    }

    @Override
    public void end(Resource resource)
    {
        this.dates.remove();
        this.rootNode.remove();
        this.bareExport.remove();
    }
}
