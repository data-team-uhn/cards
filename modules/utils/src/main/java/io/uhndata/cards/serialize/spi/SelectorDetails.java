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

package io.uhndata.cards.serialize.spi;

/**
 * The details about a selector that can be used to filter or modify what data is returned.
 * This is intended to be displayed to users looking to use selectors to help format data,
 * so should contain information about:
 * - How to call this selector (it's name)
 * - What the selector does
 * - What type of data the selector runs on (if it is restricted to certain data types)
 * - How this selector is intended to be used
 *     - Is it selector for a specific type of output?
 *     - Is it selector to be used with certain other selectors?
 * - Any additional options that can be used to configure this selector,
 *   how to specify them and what impact these options have
 *
 * @version $Id$
 */
public class SelectorDetails
{
    private String name;

    private String description;

    private Boolean enabledByDefault;

    private SelectorOption[] options;

    public SelectorDetails(String name, String description)
    {
        this(name, description, false);
    }

    public SelectorDetails(String name, String description, Boolean enabledByDefault)
    {
        this.name = name;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
        this.options = new SelectorOption[0];
    }

    public SelectorDetails(String name, String description, SelectorOption[] options)
    {
        this(name, description, false, options);
    }

    public SelectorDetails(String name, String description, Boolean enabledByDefault, SelectorOption[] options)
    {
        this.name = name;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
        this.options = options;
    }

    public SelectorDetails(String name, String description, String... options)
    {
        this(name, description, false, options);
    }

    public SelectorDetails(String name, String description, Boolean enabledByDefault, String... options)
    {
        this.name = name;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
        if (options.length >= 2) {
            int numOptions = Math.floorDiv(options.length, 2);
            this.options = new SelectorOption[numOptions];
            for (int i = 0; i < numOptions; i++) {
                this.options[i] = new SelectorOption(options[2 * i], options[2 * i + 1]);
            }
        }
    }

    /**
     * The name under which this selector is invoked in a request.
     *
     * @return The name of this selector.
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * A human-readable explanation of this selector, intended to be displayed to users.
     *
     * @return A description of what this selector does.
     */
    public String getDescription()
    {
        return this.description;
    }

    /**
     * The extra options that can be used to further configure this selector.
     *
     * @return A list of any extra options that may apply to this selector. May be {@code null}.
     */
    public SelectorOption[] getOptions()
    {
        return this.options;
    }

    public Boolean isEnabledByDefault()
    {
        return this.enabledByDefault;
    }

    public class SelectorOption
    {
        private String name;

        private String description;

        SelectorOption(String name, String description)
        {
            this.name = name;
            this.description = description;
        }

        SelectorOption(String name)
        {
            this.name = name;
            this.description = "";
        }

        public String getName()
        {
            return this.name;
        }

        public String getDescription()
        {
            return this.description;
        }
    }
}
