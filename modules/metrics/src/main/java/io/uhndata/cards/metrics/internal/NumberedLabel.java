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

package io.uhndata.cards.metrics.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * A metric label in the form configurations write it, {@code {007} Initial emails sent}: the number in braces orders
 * the metric among the others, and the rest is what is displayed. Configurations have always written labels this
 * way, so both defining a metric from a configuration and converting a metric stored before metrics had an order
 * read it through here.
 *
 * @version $Id$
 * @since 0.9.42
 */
final class NumberedLabel
{
    private static final Pattern FORMAT = Pattern.compile("^\\{(\\d{1,18})\\}\\s*(.*)$");

    private final String label;

    private final Long order;

    private NumberedLabel(final String label, final Long order)
    {
        this.label = label;
        this.order = order;
    }

    /**
     * Split a label into its order and the text to display.
     *
     * @param numberedLabel a label, optionally prefixed with a number in braces, may be {@code null}
     * @return the parsed label
     */
    static NumberedLabel parse(final String numberedLabel)
    {
        if (numberedLabel == null) {
            return new NumberedLabel(null, null);
        }
        final Matcher matcher = FORMAT.matcher(numberedLabel);
        if (matcher.matches()) {
            return new NumberedLabel(StringUtils.trimToNull(matcher.group(2)), Long.valueOf(matcher.group(1)));
        }
        return new NumberedLabel(StringUtils.trimToNull(numberedLabel), null);
    }

    /**
     * The text to display.
     *
     * @return the label without its number, or {@code null} if there is no text
     */
    String getLabel()
    {
        return this.label;
    }

    /**
     * The order the number requests.
     *
     * @return the number, or {@code null} if the label was not numbered
     */
    Long getOrder()
    {
        return this.order;
    }
}
