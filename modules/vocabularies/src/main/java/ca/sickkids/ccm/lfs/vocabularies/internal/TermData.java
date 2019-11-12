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
package ca.sickkids.ccm.lfs.vocabularies.internal;

import java.util.Collection;
import java.util.Collections;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

/**
 * A datatype that contains a LinkedHashMap object which maps: String -> Collection[String].
 * It is used to store the information of a Vocabulary Term prior to ancestor propagation.
 * The below is copied from OboParser.java .
 * @version $Id$
 */
public class TermData
{
    /** Key for fetching ID. */
    public static final String ID_FIELD_NAME = "id";

    /** Key for fetching Label. */
    public static final String LABEL_FIELD_NAME = "name";

    /** Key for fetching Parents. */
    public static final String PARENT_FIELD_NAME = "is_a";

    /** Key for fetching Ancestors. */
    public static final String TERM_CATEGORY_FIELD_NAME = "term_category";

    private String id;

    private String label;

    private MultiValuedMap<String, String> properties;

    /**
     * Blank Constructor. Allocates space for properties.
     */
    public TermData()
    {
        this.properties = new ArrayListValuedHashMap<String, String>();
    }

    /**
     * Obtain the vocabulary ID of this term.
     *
     * @return id
     */
    public String getId()
    {
        return this.id;
    }

    /**
     * Obtain the label, as provided by the vocabulary, of this term.
     *
     * @return label
     */
    public String getLabel()
    {
        return this.label;
    }

    /**
     * Return all properties of this term.
     *
     * @return MultiValuedMap[String: String] that has all the properties of the VocabularyTerm represented by this.
     */
    public MultiValuedMap<String, String> getAllProperties()
    {
        return this.properties;
    }

    /**
     * Add a value to the collection of Strings corresponding to the key.
     *
     * @param key - for value to be assigned.
     * @param value - that is to be assigned to key.
     * @return Boolean that indicates whether or not the add was successful.
     */
    public boolean addTo(String key, String value)
    {
        if (ID_FIELD_NAME.equals(key)) {
            this.id = value;
        } else if (LABEL_FIELD_NAME.equals(key)) {
            this.label = value;
        }
        return this.properties.put(key, value);
    }

    /**
     * Add a Collection of Strings to the collection of Strings corresponding to the key.
     *
     * @param key - for value to be assigned.
     * @param values - that are to be assigned to key.
     * @return Boolean that indicates whether or not the add was successful.
     */
    public boolean addTo(String key, Collection<String> values)
    {
        boolean result = true;
        for (String value : values) {
            result &= this.addTo(key, value);
        }
        return result;
    }

    /**
     * Return Collection of Strings associated with key.
     *
     * @param key - whose collection is to be returned
     * @return the Collection associated with the key
     */
    public Collection<String> getCollection(String key)
    {
        if (this.properties.containsKey(key)) {
            return this.properties.get(key);
        }
        return Collections.emptySet();
    }

    /**
     * Checks if the key exists in its properties.
     *
     * @param key - to be checked
     * @return Boolean True if the key exists in properties
     */
    public Boolean hasKey(String key)
    {
        return this.properties.containsKey(key);
    }
}
