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

    /** Name of the subfolder, within an answer's subfolder, that holds the markdown chunks. */
    public static final String CHUNKS_SUBDIR = "chunks";

    /** Name of the answer subfolder holding the per-source-file section trees. */
    public static final String SECTIONS_SUBDIR = "Sections";

    private static final Logger LOGGER = LoggerFactory.getLogger(ParsedMarkdownStore.class);

    private static final String OUTPUT_DIR_PROPERTY = "cards.parse.output.dir";

    private static final String DEFAULT_OUTPUT_SUBDIR = "cards-parsed-markdown";

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
     * Delete field-extraction chunks and per-source section folders so stale output is not
     * served after a failed parse. A failure never throws — it is logged and swallowed.
     *
     * @param outputSubfolder the answer's subfolder; clearing is skipped when {@code null} or blank
     */
    public static void clearChunks(final String outputSubfolder)
    {
        if (sanitizeSubfolder(outputSubfolder) == null) {
            return;
        }
        final Path answerDir = resolveOutputDir(outputSubfolder);
        clearFieldExtractionChunks(answerDir);
        clearSectionOutput(answerDir);
    }

    /**
     * Resolve the base directory where per-answer parse output is stored. Matches the location used by
     * {@link #resolveOutputDir(String)} before a subfolder is appended.
     *
     * @return the absolute base parse output directory
     */
    public static Path resolveBaseOutputDir()
    {
        return baseOutputDir().toAbsolutePath();
    }

    /**
     * Delete an answer's {@value #SECTIONS_SUBDIR} tree written by the section splitter. Used when a
     * stale asynchronous chunk job completes after a parse failure. A failure never throws — it is
     * logged and swallowed.
     *
     * @param answerDir the absolute answer parse folder; ignored when {@code null}
     */
    public static void clearSectionOutput(final Path answerDir)
    {
        if (answerDir == null) {
            return;
        }
        final Path sectionsRoot = answerDir.resolve(SECTIONS_SUBDIR);
        try {
            if (Files.isDirectory(sectionsRoot)) {
                deleteRecursively(sectionsRoot);
                LOGGER.info("Cleared section tree {}", sectionsRoot.toAbsolutePath());
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not clear section output in {}: {}", answerDir.toAbsolutePath(), e.getMessage());
        }
    }

    private static void deleteRecursively(final Path dir) throws IOException
    {
        try (Stream<Path> entries = Files.walk(dir)) {
            final List<Path> paths = entries.sorted(Comparator.reverseOrder())
                .collect(Collectors.toCollection(ArrayList::new));
            for (final Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Check whether a parsed {@code .md} file already exists for a given source file in an answer's subfolder.
     *
     * @param outputSubfolder the answer's subfolder; treated as absent when {@code null} or blank
     * @param fileName the source file name whose parsed output is looked up
     * @return {@code true} if the corresponding {@code .md} file exists
     */
    public static boolean hasParsedFile(final String outputSubfolder, final String fileName)
    {
        if (sanitizeSubfolder(outputSubfolder) == null || StringUtils.isBlank(fileName)) {
            return false;
        }
        return Files.isRegularFile(resolveOutputDir(outputSubfolder).resolve(buildOutputFileName(fileName)));
    }

    /**
     * Delete the parsed {@code .md} file for a single source file. A failure never throws — it is logged and
     * swallowed.
     *
     * @param outputSubfolder the answer's subfolder; deletion is skipped when {@code null} or blank
     * @param fileName the source file name whose parsed output should be removed
     */
    public static void deleteParsedFile(final String outputSubfolder, final String fileName)
    {
        if (sanitizeSubfolder(outputSubfolder) == null || StringUtils.isBlank(fileName)) {
            return;
        }
        final Path file = resolveOutputDir(outputSubfolder).resolve(buildOutputFileName(fileName));
        try {
            if (Files.deleteIfExists(file)) {
                LOGGER.info("Deleted parsed markdown for '{}' at {}", fileName, file.toAbsolutePath());
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not delete parsed markdown for '{}': {}", fileName, e.getMessage());
        }
    }

    /**
     * Delete an answer's entire parse output subfolder, including the aggregate and chunks. Used when the
     * owning answer or all of its files are removed. A failure never throws — it is logged and swallowed.
     *
     * @param outputSubfolder the answer's subfolder; deletion is skipped when {@code null} or blank
     */
    public static void deleteFolder(final String outputSubfolder)
    {
        if (sanitizeSubfolder(outputSubfolder) == null) {
            return;
        }
        final Path dir = resolveOutputDir(outputSubfolder);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(dir)) {
            final List<Path> paths = entries.sorted(Comparator.reverseOrder())
                .collect(Collectors.toCollection(ArrayList::new));
            for (final Path path : paths) {
                Files.deleteIfExists(path);
            }
            LOGGER.info("Deleted parse output folder {}", dir.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not delete parse output folder {}: {}", dir.toAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Read the aggregated markdown for an answer.
     *
     * @param outputSubfolder the answer's subfolder; ignored when {@code null} or blank
     * @return the aggregated markdown content, or {@code null} when no answer subfolder is provided or the
     *         {@value #AGGREGATED_FILE_NAME} file does not exist or cannot be read
     */
    public static String readAggregate(final String outputSubfolder)
    {
        if (sanitizeSubfolder(outputSubfolder) == null) {
            return null;
        }
        final Path aggregated = resolveOutputDir(outputSubfolder).resolve(AGGREGATED_FILE_NAME);
        try {
            if (!Files.isRegularFile(aggregated)) {
                return null;
            }
            return Files.readString(aggregated, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read aggregated markdown in {}: {}", aggregated.toAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /**
     * Read the last-modified time of an answer's aggregated markdown file. Callers use this to decide whether
     * the document was re-parsed after a previous extraction.
     *
     * @param outputSubfolder the answer's subfolder; ignored when {@code null} or blank
     * @return the file's last-modified time in epoch milliseconds, or {@code 0} when no answer subfolder is
     *         provided or the {@value #AGGREGATED_FILE_NAME} file does not exist or cannot be read
     */
    public static long aggregateLastModified(final String outputSubfolder)
    {
        if (sanitizeSubfolder(outputSubfolder) == null) {
            return 0L;
        }
        final Path aggregated = resolveOutputDir(outputSubfolder).resolve(AGGREGATED_FILE_NAME);
        try {
            if (!Files.isRegularFile(aggregated)) {
                return 0L;
            }
            return Files.getLastModifiedTime(aggregated).toMillis();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read modified time of aggregated markdown in {}: {}",
                aggregated.toAbsolutePath(), e.getMessage());
            return 0L;
        }
    }

    /**
     * Read every chunk file for an answer, in chunk-index order. When no chunks have been written yet, the
     * aggregated markdown is returned as a single element so callers always have something to send.
     *
     * @param outputSubfolder the answer's subfolder; ignored when {@code null} or blank
     * @return the ordered chunk contents, or an empty list when nothing is available
     */
    public static List<String> readChunks(final String outputSubfolder)
    {
        if (sanitizeSubfolder(outputSubfolder) == null) {
            return List.of();
        }
        final Path chunksDir = resolveOutputDir(outputSubfolder).resolve(CHUNKS_SUBDIR);
        final List<String> chunks = readChunkFiles(chunksDir);
        if (!chunks.isEmpty()) {
            return chunks;
        }
        final String aggregated = readAggregate(outputSubfolder);
        return aggregated == null ? List.of() : List.of(aggregated);
    }

    /**
     * Resolve the absolute parse output directory for an answer, the folder that holds its per-file
     * markdown, {@value #AGGREGATED_FILE_NAME} and chunk output. Callers that hand the path to an
     * external process (such as the Docling chat chunker) use this so they target the exact same
     * folder this store reads and writes.
     *
     * @param outputSubfolder the answer's subfolder; treated as absent when {@code null} or blank
     * @return the absolute answer directory, or {@code null} when no valid subfolder is provided
     */
    public static Path resolveAnswerDir(final String outputSubfolder)
    {
        if (sanitizeSubfolder(outputSubfolder) == null) {
            return null;
        }
        return resolveOutputDir(outputSubfolder).toAbsolutePath();
    }

    private static void clearFieldExtractionChunks(final Path answerDir)
    {
        final Path chunksDir = answerDir.resolve(CHUNKS_SUBDIR);
        try {
            if (!Files.isDirectory(chunksDir)) {
                return;
            }
            try (Stream<Path> entries = Files.list(chunksDir)) {
                for (final Path entry : entries.toList()) {
                    Files.deleteIfExists(entry);
                }
            }
            LOGGER.info("Cleared markdown chunks in {}", chunksDir.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not clear markdown chunks in {}: {}", chunksDir.toAbsolutePath(), e.getMessage());
        }
    }

    private static List<String> readChunkFiles(final Path chunksDir)
    {
        if (!Files.isDirectory(chunksDir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(chunksDir)) {
            final List<Path> chunkFiles = entries
                .filter(Files::isRegularFile)
                .filter(path -> isChunkFile(path.getFileName().toString()))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .collect(Collectors.toCollection(ArrayList::new));
            final List<String> contents = new ArrayList<>(chunkFiles.size());
            for (final Path chunkFile : chunkFiles) {
                contents.add(Files.readString(chunkFile, StandardCharsets.UTF_8));
            }
            return contents;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read markdown chunks in {}: {}", chunksDir.toAbsolutePath(), e.getMessage());
            return List.of();
        }
    }

    private static boolean isChunkFile(final String name)
    {
        return name.startsWith("chunk_") && name.toLowerCase(Locale.ROOT).endsWith(".md");
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
