/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.forms.internal.parse;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Post-processing cleanup for generated markdown output.
 *
 * @version $Id$
 */
public final class MarkdownCleanup
{
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\n{3,}");

    private static final Pattern EMPTY_HEADING = Pattern.compile("^#{1,6}\\s*_?\\s*$");

    private static final Pattern GARBAGE_LINE = Pattern.compile("^(\\|{2,}|_{2,}|\\.{3,})\\s*$");

    private MarkdownCleanup()
    {
        // Utility class
    }

    /**
     * Clean up parsed markdown by collapsing excessive blank lines, removing empty headings,
     * and stripping decorative garbage lines.
     *
     * @param markdown raw markdown text
     * @return cleaned markdown text
     */
    public static String clean(final String markdown)
    {
        if (StringUtils.isBlank(markdown)) {
            return markdown == null ? "" : markdown;
        }
        final StringBuilder cleaned = new StringBuilder();
        for (final String line : markdown.split("\n", -1)) {
            if (EMPTY_HEADING.matcher(line).matches() || GARBAGE_LINE.matcher(line).matches()) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        return EXCESSIVE_NEWLINES.matcher(cleaned.toString()).replaceAll("\n\n").trim();
    }
}
