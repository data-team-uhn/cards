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
package io.uhndata.cards.clarity.importer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Helpers shared by the tests of this module.
 *
 * @version $Id$
 */
public final class TestUtils
{
    private TestUtils()
    {
        // Prevent instantiation of a utility class
    }

    /**
     * Set a field that OSGi would normally populate through an {@code @Reference} annotation.
     *
     * @param target the object to set the field on
     * @param name the name of the field
     * @param value the value to set
     */
    public static void setField(final Object target, final String name, final Object value)
    {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot set " + name, e);
            }
        }
        throw new IllegalStateException("No field named " + name + " on " + target.getClass());
    }

    /**
     * Build a Clarity row out of alternating keys and values.
     *
     * @param keysAndValues the column names and their values, in pairs
     * @return a mutable row
     */
    public static Map<String, String> row(final String... keysAndValues)
    {
        final Map<String, String> row = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }
}
