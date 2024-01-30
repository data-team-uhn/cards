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
package io.uhndata.cards.subjects.internal.serialize;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.RowIterator;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Adds number of corresponding subjects to the subject type json. The name of this processor is {@code instanceCount},
 * and it is enabled by default.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class SubjectTypeInstanceCountProcessor implements ResourceJsonProcessor
{
    private static final String NAME = "instanceCount";

    /** An original resource path. */
    private ThreadLocal<String> originalPath = new ThreadLocal<>();

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public int getPriority()
    {
        return 55;
    }

    @Override
    public void start(Resource resource)
    {
        this.originalPath.set(resource.getPath());
    }

    @Override
    public boolean isEnabledByDefault(Resource resource)
    {
        return false;
    }

    @Override
    public boolean canProcess(Resource resource)
    {
        // This only processes subject types
        return resource.isResourceType("cards/SubjectType");
    }

    @Override
    public void leave(Node node, JsonObjectBuilder json, Function<Node, JsonValue> serializeNode)
    {
        try {
            // Only the original subject type node will have its data appended
            if (!node.getPath().equals(this.originalPath.get())) {
                return;
            }
            Query queryObj = node.getSession().getWorkspace().getQueryManager().createQuery(generateDataQuery(node),
                "JCR-SQL2");
            RowIterator queryResult = queryObj.execute().getRows();
            long count = queryResult.getSize();
            if (count < 0) {
                // Getting the count directly fails for some index types, so we have to manually count the number of
                // items returned.
                AtomicLong atomicCount = new AtomicLong();
                Consumer<Object> consumer = i -> atomicCount.incrementAndGet();
                while (queryResult.hasNext()) {
                    consumer.accept(queryResult.next());
                }
                count = atomicCount.get();
            }
            json.add(NAME, count);
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
    }

    private String generateDataQuery(final Node node)
        throws RepositoryException
    {
        return String.format(
            "select [jcr:path] from [cards:Subject] as n where n.type = '%s' OPTION (index tag property)",
            node.getProperty("jcr:uuid").getString());
    }
}
