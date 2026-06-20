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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores parsed markdown on disk and aggregates the markdown produced for a single answer.
 * <p>
 * Each parsed document is written as an individual {@code .md} file. All files belonging to one
 * answer are grouped in a per-answer subfolder (typically named after the answer's UUID). The output
 * directory defaults to {@code <user.dir>/cards-parsed-markdown} and can be overridden with the
 * {@code cards.parse.output.dir} system property.
 * </p>
 *
 * @version $Id$
 */
public final class ParsedMarkdownStore
{
    /** Name of the aggregated markdown file written into each answer's subfolder. */
    public static final String AGGREGATED_FILE_NAME = "aggregated.md";

    private static final Logger LOGGER = LoggerFactory.getLogger(ParsedMarkdownStore.class);

    private static final String OUTPUT_DIR_PROPERTY = "cards.parse.output.dir";

    private static final String DEFAULT_OUTPUT_SUBDIR = "cards-parsed-markdown";

    private static final String SEPARATOR = "\n\n";

    private ParsedMarkdownStore()
    {
        // Utility class, never instantiated.
    }

    /**
     * Persist a single parsed document as a {@code .md} file. Any existing file with the same name in
     * the same subfolder is deleted first, so re-parsing the same file always replaces stale output. A
     * failure to save never throws — it is logged and swallowed.
     *
     * @param outputSubfolder subfolder to save into (typically the owning answer's UUID); when
     *            {@code null} or blank, the output directory root is used
     * @param fileName source file name; its extension is replaced with {@code .md}
     * @param markdown the markdown content to persist
     */
    public static void save(final String outputSubfolder, final String fileName, final String markdown)
    {
        try {
            final Path outputDir = resolveOutputDir(outputSubfolder);
            Files.createDirectories(outputDir);
            final Path outputFile = outputDir.resolve(buildOutputFileName(fileName));
            if (Files.deleteIfExists(outputFile)) {
                LOGGER.info("Deleted previous parse result for '{}' at {}", fileName, outputFile);
            }
            Files.writeString(outputFile, markdown, StandardCharsets.UTF_8);
            LOGGER.info("Saved parse result for '{}' to {}", fileName, outputFile);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not save parse result for '{}': {}", fileName, e.getMessage());
        }
    }

    /**
     * Join every parsed {@code .md} file in an answer's subfolder into a single
     * {@value #AGGREGATED_FILE_NAME}, ordered from the largest file to the smallest. The aggregated
     * file itself is excluded from the inputs and overwritten on each run. A failure never throws — it
     * is logged and swallowed.
     *
     * @param outputSubfolder the answer's subfolder; aggregation is skipped when {@code null} or blank
     */
    public static void writeAggregate(final String outputSubfolder)
    {
        if (sanitizeSubfolder(outputSubfolder) == null) {
            LOGGER.debug("Skipping markdown aggregation: no answer subfolder provided");
            return;
        }
        // Resolve via the exact same method used by save() so the aggregate always lands in the same
        // folder as the per-file markdown.
        final Path dir = resolveOutputDir(outputSubfolder);
        try {
            if (!Files.isDirectory(dir)) {
                LOGGER.warn("Cannot aggregate markdown: directory {} does not exist", dir.toAbsolutePath());
                return;
            }
            final List<Path> mdFiles = listMarkdownFiles(dir);
            if (mdFiles.isEmpty()) {
                LOGGER.warn("No parsed markdown files to aggregate in {}", dir.toAbsolutePath());
                return;
            }
            mdFiles.sort(Comparator.comparingLong(ParsedMarkdownStore::fileSize).reversed()
                .thenComparing(path -> path.getFileName().toString()));
            final Path target = dir.resolve(AGGREGATED_FILE_NAME);
            Files.writeString(target, joinFiles(mdFiles), StandardCharsets.UTF_8);
            LOGGER.info("Wrote aggregated markdown from {} file(s) to {}",
                mdFiles.size(), target.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not write aggregated markdown in {}: {}", dir.toAbsolutePath(), e.getMessage());
        }
    }

    private static List<Path> listMarkdownFiles(final Path dir)
        throws IOException
    {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                .filter(Files::isRegularFile)
                .filter(ParsedMarkdownStore::isAggregatableMarkdown)
                .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private static boolean isAggregatableMarkdown(final Path path)
    {
        final String name = path.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".md")
            && !AGGREGATED_FILE_NAME.equals(name.toLowerCase(Locale.ROOT));
    }

    private static String joinFiles(final List<Path> files)
        throws IOException
    {
        final List<String> contents = new ArrayList<>(files.size());
        for (final Path file : files) {
            contents.add(Files.readString(file, StandardCharsets.UTF_8));
        }
        return String.join(SEPARATOR, contents);
    }

    private static long fileSize(final Path path)
    {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static Path resolveOutputDir(final String outputSubfolder)
    {
        final Path baseDir = baseOutputDir();
        final String sanitizedSubfolder = sanitizeSubfolder(outputSubfolder);
        if (sanitizedSubfolder != null) {
            return baseDir.resolve(sanitizedSubfolder);
        }
        return baseDir;
    }

    private static Path baseOutputDir()
    {
        final String configured = System.getProperty(OUTPUT_DIR_PROPERTY);
        if (StringUtils.isNotBlank(configured)) {
            return Paths.get(configured);
        }
        return Paths.get(System.getProperty("user.dir"), DEFAULT_OUTPUT_SUBDIR);
    }

    private static String sanitizeSubfolder(final String outputSubfolder)
    {
        if (StringUtils.isBlank(outputSubfolder)) {
            return null;
        }
        String sanitized = outputSubfolder.trim();
        final int separator = Math.max(sanitized.lastIndexOf('/'), sanitized.lastIndexOf('\\'));
        if (separator >= 0) {
            sanitized = sanitized.substring(separator + 1);
        }
        if (StringUtils.isBlank(sanitized) || ".".equals(sanitized) || "..".equals(sanitized)) {
            return null;
        }
        return sanitized;
    }

    private static String buildOutputFileName(final String fileName)
    {
        String baseName = fileName;
        final int separator = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (separator >= 0) {
            baseName = baseName.substring(separator + 1);
        }
        final int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        if (StringUtils.isBlank(baseName)) {
            baseName = "document";
        }
        return baseName + ".md";
    }
}
