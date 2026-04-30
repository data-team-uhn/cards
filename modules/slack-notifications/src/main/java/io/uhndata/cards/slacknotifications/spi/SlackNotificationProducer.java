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

package io.uhndata.cards.slacknotifications.spi;

import java.util.List;
import java.util.Map;

import jakarta.json.JsonObject;

/**
 * Service interface for producing messages to post to Slack. When it's time to send a status message, each
 * implementation's {@link #prepareMessages} will be invoked, and the results will be aggregated in a single Slack post.
 *
 * @version $Id$
 * @since 0.9.38
 */
public interface SlackNotificationProducer
{
    /** The name of the JSON property holding the title of the message. */
    String TITLE = "title";

    /** The name of the JSON property holding the text part of a message. */
    String TEXT = "text";

    /** The name of the JSON property defining the side color of an attachment. */
    String COLOR = "color";

    /** The color used for neutral/informational messages. */
    String INFO = "999";

    /** The color used for success messages. */
    String SUCCESS = "393";

    /** The color used for warning messages. */
    String WARNING = "BA0";

    /** The color used for error messages. */
    String ERROR = "900";

    /**
     * The name of this producer, used to identify it, and to enable/disable it for specific jobs.
     *
     * @return a simple string
     */
    String getName();

    /**
     * Prepare a message, as a valid Slack "attachment" object.
     *
     * @param extraParameters optional extra parameters configured for the Slack notification, which may influence how
     *            the message is prepared
     * @return a list of JSON objects respecting the attachment API, that will be added to the attachments list of the
     *         message
     */
    List<JsonObject> prepareMessages(Map<String, String> extraParameters);
}
