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

package io.uhndata.cards.status.spi;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * A bit of information about the status of the system.
 *
 * @version $Id$
 * @since 0.9.38
 */
public class StatusReport
{
    /** A category for this status. */
    public enum Status
    {
        /** A debug report, excluded unless explicitly requested. */
        DEBUG,
        /** An information report. */
        INFO,
        /** A report about a successful status. */
        SUCCESS,
        /** A report containing a warning message that should be looked at. */
        WARNING,
        /** A report containing an error message that signals a critical problem that must be investigated. */
        ERROR
    }

    private final String name;

    private final Status status;

    private final String text;

    public StatusReport(final String name, final Status status, final String text)
    {
        this.name = name;
        this.status = status;
        this.text = text;
    }

    /**
     * The name of this report.
     *
     * @return a simple string
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * The status of this report.
     *
     * @return one of the {@link Status} values
     */
    public Status getStatus()
    {
        return this.status;
    }

    /**
     * The body of the report.
     *
     * @return a piece of text
     */
    public String getText()
    {
        return this.text;
    }

    @Override
    public String toString()
    {
        return getName() + ": " + getStatus();
    }

    /**
     * Serialize this report as a JSON object.
     *
     * @return a JSON object with the following keys: {@code name}, {@code status}, {@code text}
     */
    public JsonObject toJson()
    {
        return Json.createObjectBuilder()
            .add("name", getName())
            .add("status", getStatus().toString())
            .add("text", getText())
            .build();
    }
}
