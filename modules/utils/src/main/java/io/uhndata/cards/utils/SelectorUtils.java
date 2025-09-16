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
package io.uhndata.cards.utils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Utility class for parsing the selectors suffix from a resource into a list of selectors or a map of selector options.
 *
 * @version $Id$
 * @since 0.9.33
 */
public final class SelectorUtils
{
    private SelectorUtils()
    {
        // Prevent instantiation of a utility class
    }

    /**
     * Parse a selectors string into a list of selectors. This unescapes the URL-encoded string, and considers escaped
     * dots and escaped backslashes.
     *
     * @param resolutionPathInfo the resolution path info, as received from Sling, may be URL-encoded
     * @return a list of selectors, dots not included
     */
    public static List<String> parseSelectors(final String resolutionPathInfo)
    {
        if (StringUtils.isBlank(resolutionPathInfo)) {
            return Collections.emptyList();
        }
        // Parse the selectors string into individual selectors.
        // Split by unescaped dots. A backslash escapes a dot, but two backslashes are just one escaped backslash.
        // Match by:
        // - no preceding backslash, i.e. start counting at the first backslash, which means either any character other
        // than \, or the start of the string: ([^\]|^)
        // - an even number of backslashes, i.e. any number of groups of two backslashes (\\)*
        // - due to limitations of the lookbehind implementation in Java, instead of any number, we limit to at most 10
        // pairs: (\\){0,10}
        // - a literal dot \.
        // Each backslash, except the \., is escaped twice, once as a special escape char inside a Java string, and
        // once as a special escape char inside a RegExp. The one before the dot is escaped only once as a special
        // char inside a Java string, since it must retain its escaping meaning in the RegExp.
        return Arrays
            .asList(URLDecoder.decode(resolutionPathInfo, StandardCharsets.UTF_8)
                .split("(?<=([^\\\\]|^)(\\\\\\\\){0,10})\\."))
            .stream()
            // Also unescape escaped dots, if present
            // No preceding backslash, negative lookbehind for \: (?<!\)
            // As a regexp special character, each backslash must be escaped: (?<!\\)
            // As a Java string literal, each backslash must be escaped again: (?<!\\\\)
            // Escaped dot: odd number of \ followed by a dot, so (\\)*+\.
            // As a regexp special character, each backslash must be escaped: (\\\\)*+\\.
            // As a regexp special character, the dot must be escaped: (\\\\)*+\\\.
            // As a Java string literal, each backslash must be escaped again: (\\\\\\\\)*+\\\\\\.
            // Since we want to keep the backslashes in its selector, capture them in a group: ((\\\\\\\\)*+)
            .map(s -> s.replaceAll("(?<!\\\\)((\\\\\\\\)*+)\\\\\\.", "$1.")
                // Finally, unescape escaped backslashes
                .replace("\\\\", "\\"))
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
    }

    /**
     * Parse the options for a given prefix, for example from {@code .dataFilter:status=SUBMITTED} extracts the pair
     * {@code (status, SUBMITTED)}. This returns all matching pairs, with multiple instances for the same key included
     * if it is present more than once in the path info.
     *
     * @param optionPrefix the option prefix to look for
     * @param resolutionPathInfo the resolution path info, as received from Sling, may be URL-encoded
     * @return a list of extracted pairs of key=value
     */
    public static List<Pair<String, String>> parseOptions(final String optionPrefix, final String resolutionPathInfo)
    {
        if (StringUtils.isAnyBlank(optionPrefix, resolutionPathInfo)) {
            return Collections.emptyList();
        }
        final String prefix = Strings.CS.appendIfMissing(optionPrefix, ":");
        // First parse the selectors string into a list of selectors
        // Then parse the dataFilter selectors into key=value pairs
        return parseSelectors(resolutionPathInfo).stream()
            .filter(s -> Strings.CS.startsWith(s, prefix))
            .map(s -> StringUtils.substringAfter(s, prefix))
            .map(s -> {
                String[] bits = s.split("(?<=([^\\\\]|^)(\\\\\\\\){0,10})=", 2);
                if (bits.length == 2) {
                    return Pair.of(bits[0].replace("\\=", "="), bits[1].replace("\\=", "=").replace("\\\\", "\\"));
                }
                return Pair.of(bits[0].replace("\\=", "=").replace("\\\\", "\\"), "");
            })
            .collect(Collectors.toList());
    }

    /**
     * Parse the options for a given prefix, for example from {@code .dataFilter:status=SUBMITTED} extracts the pair
     * {@code (status, SUBMITTED)}. This returns only the last value for a given key, if multiple instances for the same
     * key are present in the path info.
     *
     * @param optionPrefix the option prefix to look for
     * @param resolutionPathInfo the resolution path info, as received from Sling, may be URL-encoded
     * @return a map of the extracted key=value
     */
    public static Map<String, String> parseOptionsToMap(final String optionPrefix, final String resolutionPathInfo)
    {
        List<Pair<String, String>> allOptions = parseOptions(optionPrefix, resolutionPathInfo);
        Map<String, String> result = new HashMap<>();
        allOptions.stream().forEach(pair -> result.put(pair.getKey(), pair.getValue()));
        return result;
    }
}
