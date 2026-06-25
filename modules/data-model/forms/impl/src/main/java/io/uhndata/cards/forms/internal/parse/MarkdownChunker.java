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

package io.uhndata.cards.forms.internal.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits a large Markdown document (such as those produced by Docling) into a series of smaller
 * {@code chunk_NNN.md} files, each targeting a configurable maximum token size, without breaking
 * paragraphs or table rows when it can be avoided.
 * <p>
 * Token counts are approximated as {@code characterCount / 4}, and a safety margin is applied so that
 * the real target is {@code maxTokens * 0.85}. The document is streamed line by line and grouped into
 * logical blocks (headings, tables and paragraphs); blocks accumulate into a chunk until adding the
 * next one would exceed the target, at which point a new chunk is started. The current heading
 * hierarchy is tracked and prepended to every continuation chunk so the model keeps its context.
 * </p>
 * <p>
 * A single block that is itself larger than the target is split further: paragraphs by sentence (and,
 * as a last resort, by character count) and tables by row, keeping the header row with every part.
 * After all chunks are written, a {@code chunks_index.json} array listing the chunk file names is
 * written into the same directory.
 * </p>
 *
 * @version $Id$
 */
public final class MarkdownChunker
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownChunker.class);

    /** A single line break. */
    private static final String NEWLINE = "\n";

    /** Blank line used to separate blocks within a chunk. */
    private static final String SEPARATOR = NEWLINE + NEWLINE;

    /** {@link String#format} pattern for chunk file names. */
    private static final String CHUNK_NAME_FORMAT = "chunk_%03d.md";

    /** Name of the index file listing all chunk file names. */
    private static final String INDEX_FILE_NAME = "chunks_index.json";

    /** Fraction of the requested maximum used as the real target, leaving a safety margin. */
    private static final double SAFETY_MARGIN = 0.85;

    /** Approximate number of characters per token. */
    private static final double CHARS_PER_TOKEN = 4.0;

    /** The deepest ATX heading level recognized ({@code ######}). */
    private static final int MAX_HEADING_LEVEL = 6;

    /** Number of leading lines (header row plus separator row) that make up a table header. */
    private static final int TABLE_HEADER_LINES = 2;

    /** Smallest budget, in characters, that a chunk body is ever allowed to target. */
    private static final long MIN_BUDGET_CHARS = 1;

    /** The directory chunk files and the index are written into. */
    private final Path outputDirectory;

    /** The maximum number of characters to aim for per chunk. */
    private final long targetChars;

    /** The names of the chunk files written so far, in order. */
    private final List<String> chunkNames = new ArrayList<>();

    /** The heading lines currently in scope, from the shallowest to the deepest. */
    private final List<String> headingStack = new ArrayList<>();

    /** The chunk currently being assembled. */
    private final StringBuilder current = new StringBuilder();

    /** The reader for the input document, set for the duration of {@link #run(Path)}. */
    private BufferedReader reader;

    /** A single line read ahead of the current block and pushed back for the next read. */
    private String pendingLine;

    /** The number of chunks written so far, used for naming. */
    private int chunkIndex;

    /**
     * Create a chunker bound to an output directory and a target size.
     *
     * @param outputDir the directory chunk files and the index are written into
     * @param targetCharCount the maximum number of characters to aim for per chunk
     */
    private MarkdownChunker(final Path outputDir, final long targetCharCount)
    {
        this.outputDirectory = outputDir;
        this.targetChars = targetCharCount;
    }

    /**
     * Split a Markdown file into chunk files under the given directory.
     *
     * @param inputMarkdown path to the Markdown document to split
     * @param outputDirectory directory to write the chunk files and the index into; created if missing
     * @param maxTokens approximate maximum number of tokens per chunk; must be positive
     * @return the names of the chunk files that were written, in order
     * @throws IOException if the input cannot be read or a chunk cannot be written
     */
    public static List<String> chunk(final Path inputMarkdown, final Path outputDirectory, final int maxTokens)
        throws IOException
    {
        if (inputMarkdown == null || outputDirectory == null) {
            throw new IllegalArgumentException("Input and output paths must not be null");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        final long startTimestamp = System.currentTimeMillis();
        LOGGER.info("Markdown chunking started for '{}'", inputMarkdown);
        try {
            final MarkdownChunker worker = new MarkdownChunker(outputDirectory, computeTargetChars(maxTokens));
            return worker.run(inputMarkdown);
        } finally {
            final long endTimestamp = System.currentTimeMillis();
            LOGGER.info("Markdown chunking finished for '{}' at {} (total {} ms)",
                inputMarkdown, endTimestamp, endTimestamp - startTimestamp);
        }
    }

    /**
     * Approximate the number of tokens in a piece of text as its character count divided by four.
     *
     * @param text the text to measure; may be {@code null}
     * @return the estimated token count, or 0 when the text is {@code null}
     */
    public static long estimateTokens(final String text)
    {
        if (text == null) {
            return 0;
        }
        return (long) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Read the input document, write all chunk files and the index.
     *
     * @param inputMarkdown path to the Markdown document to split
     * @return the names of the chunk files that were written, in order
     * @throws IOException if the input cannot be read or a chunk cannot be written
     */
    private List<String> run(final Path inputMarkdown)
        throws IOException
    {
        Files.createDirectories(this.outputDirectory);
        this.clearPreviousOutput();
        try (BufferedReader bufferedReader = Files.newBufferedReader(inputMarkdown, StandardCharsets.UTF_8)) {
            this.reader = bufferedReader;
            Block block = this.nextBlock();
            while (block != null) {
                this.processBlock(block);
                block = this.nextBlock();
            }
        }
        this.flush();
        this.writeIndex();
        return this.chunkNames;
    }

    /**
     * Remove chunk files and the index left over from a previous run so stale content cannot be read.
     *
     * @throws IOException if a stale file cannot be deleted
     */
    private void clearPreviousOutput()
        throws IOException
    {
        if (!Files.isDirectory(this.outputDirectory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(this.outputDirectory)) {
            for (final Path entry : entries.toList()) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                final String name = entry.getFileName().toString();
                if (INDEX_FILE_NAME.equals(name) || isChunkFileName(name)) {
                    Files.delete(entry);
                }
            }
        }
    }

    /**
     * Determine whether a file name matches the {@value #CHUNK_NAME_FORMAT} pattern.
     *
     * @param name the file name to inspect
     * @return {@code true} if the name is a chunk file
     */
    private static boolean isChunkFileName(final String name)
    {
        return name.startsWith("chunk_") && name.endsWith(".md") && name.length() == "chunk_000.md".length();
    }

    /**
     * Route a single block to either the normal accumulation path or, when it is too large to fit a
     * chunk on its own, the oversized-block path.
     *
     * @param block the block to process
     * @throws IOException if a chunk cannot be written
     */
    private void processBlock(final Block block)
        throws IOException
    {
        if (block.type() == BlockType.HEADING) {
            this.updateStack(block.lines().get(0));
        }
        final String text = block.text();
        if (text.length() > this.targetChars) {
            this.processOversized(block, text);
            return;
        }
        this.appendBlock(block, text);
    }

    /**
     * Add a block that fits within the target to the current chunk, starting a new chunk first if it
     * would otherwise overflow.
     *
     * @param block the block being added, used to decide the heading context of a fresh chunk
     * @param text the rendered text of the block
     * @throws IOException if a chunk cannot be written
     */
    private void appendBlock(final Block block, final String text)
        throws IOException
    {
        if (this.current.length() > 0 && this.overflow(text)) {
            this.flush();
        }
        if (this.current.length() == 0) {
            this.startChunk(block);
        }
        this.appendText(text);
    }

    /**
     * Split a block that is larger than the target across one or more dedicated chunks.
     *
     * @param block the oversized block
     * @param text the rendered text of the block
     * @throws IOException if a chunk cannot be written
     */
    private void processOversized(final Block block, final String text)
        throws IOException
    {
        this.flush();
        if (block.type() == BlockType.TABLE) {
            this.processOversizedTable(block);
        } else {
            this.processOversizedText(text);
        }
    }

    /**
     * Split oversized free text into pieces, by sentence and then by character count, writing each
     * piece as its own chunk prefixed with the current heading context.
     *
     * @param text the text to split
     * @throws IOException if a chunk cannot be written
     */
    private void processOversizedText(final String text)
        throws IOException
    {
        final List<String> context = this.fullStack();
        final long budget = this.bodyBudget(context);
        for (final String piece : splitText(text, budget)) {
            this.emitPieceAsChunk(piece, context);
        }
    }

    /**
     * Split an oversized table by row, repeating the header row in every part, and writing each part
     * as its own chunk prefixed with the current heading context.
     *
     * @param block the table block
     * @throws IOException if a chunk cannot be written
     */
    private void processOversizedTable(final Block block)
        throws IOException
    {
        final List<String> lines = block.lines();
        final String header = String.join(NEWLINE, lines.subList(0, TABLE_HEADER_LINES));
        final List<String> body = lines.subList(TABLE_HEADER_LINES, lines.size());
        final List<String> context = this.fullStack();
        final long budget = this.tableBudget(context, header);
        for (final List<String> group : packRows(body, budget)) {
            this.emitPieceAsChunk(composeTable(header, group), context);
        }
    }

    /**
     * Write a single piece, prefixed with the given heading context, as a standalone chunk.
     *
     * @param body the body text of the piece
     * @param context the heading lines to prepend
     * @throws IOException if the chunk cannot be written
     */
    private void emitPieceAsChunk(final String body, final List<String> context)
        throws IOException
    {
        this.appendContext(context);
        this.appendText(body);
        this.flush();
    }

    /**
     * Prepend the appropriate heading context to a freshly started chunk: a heading block gets its
     * ancestors (it will write itself), any other block gets the full hierarchy.
     *
     * @param block the first block of the new chunk
     */
    private void startChunk(final Block block)
    {
        if (block.type() == BlockType.HEADING) {
            this.appendContext(this.ancestors());
        } else {
            this.appendContext(this.fullStack());
        }
    }

    /**
     * Append the heading hierarchy to the current chunk as a single context block.
     *
     * @param headings the heading lines to append
     */
    private void appendContext(final List<String> headings)
    {
        if (headings.isEmpty()) {
            return;
        }
        this.appendText(String.join(NEWLINE, headings));
    }

    /**
     * Append a piece of text to the current chunk, separating it from any existing content with a
     * blank line.
     *
     * @param text the text to append
     */
    private void appendText(final String text)
    {
        if (this.current.length() > 0) {
            this.current.append(SEPARATOR);
        }
        this.current.append(text);
    }

    /**
     * Determine whether adding the given text would push the current chunk past the target size.
     *
     * @param text the text that would be added
     * @return {@code true} if appending the text would exceed the target
     */
    private boolean overflow(final String text)
    {
        return this.current.length() + SEPARATOR.length() + text.length() > this.targetChars;
    }

    /**
     * Update the heading stack for a newly seen heading, dropping any sibling or deeper headings.
     *
     * @param headingLine the heading line
     */
    private void updateStack(final String headingLine)
    {
        final int level = headingLevel(headingLine);
        while (!this.headingStack.isEmpty()
            && headingLevel(this.headingStack.get(this.headingStack.size() - 1)) >= level) {
            this.headingStack.remove(this.headingStack.size() - 1);
        }
        this.headingStack.add(headingLine);
    }

    /**
     * The heading hierarchy above the deepest current heading.
     *
     * @return a copy of the heading stack without its last entry, or an empty list
     */
    private List<String> ancestors()
    {
        if (this.headingStack.size() <= 1) {
            return List.of();
        }
        return new ArrayList<>(this.headingStack.subList(0, this.headingStack.size() - 1));
    }

    /**
     * The full current heading hierarchy.
     *
     * @return a copy of the heading stack
     */
    private List<String> fullStack()
    {
        return new ArrayList<>(this.headingStack);
    }

    /**
     * The number of characters available for a chunk body once the heading context is accounted for.
     *
     * @param context the heading lines that will be prepended
     * @return the remaining character budget, never below {@link #MIN_BUDGET_CHARS}
     */
    private long bodyBudget(final List<String> context)
    {
        final long budget = this.targetChars - String.join(NEWLINE, context).length() - SEPARATOR.length();
        return Math.max(budget, MIN_BUDGET_CHARS);
    }

    /**
     * The number of characters available for table rows once the heading context and the repeated
     * table header are accounted for.
     *
     * @param context the heading lines that will be prepended
     * @param header the table header text repeated in every part
     * @return the remaining character budget, never below {@link #MIN_BUDGET_CHARS}
     */
    private long tableBudget(final List<String> context, final String header)
    {
        final long used = String.join(NEWLINE, context).length() + header.length() + 2L * SEPARATOR.length();
        return Math.max(this.targetChars - used, MIN_BUDGET_CHARS);
    }

    /**
     * Write the current chunk to disk, if it holds any content, and reset for the next one.
     *
     * @throws IOException if the chunk file cannot be written
     */
    private void flush()
        throws IOException
    {
        if (this.current.length() == 0) {
            return;
        }
        this.chunkIndex++;
        final String name = String.format(Locale.ROOT, CHUNK_NAME_FORMAT, this.chunkIndex);
        final String body = this.current.toString();
        final String content = body.endsWith(NEWLINE) ? body : body + NEWLINE;
        Files.writeString(this.outputDirectory.resolve(name), content, StandardCharsets.UTF_8);
        this.chunkNames.add(name);
        this.current.setLength(0);
    }

    /**
     * Write the {@code chunks_index.json} array of chunk file names.
     *
     * @throws IOException if the index file cannot be written
     */
    private void writeIndex()
        throws IOException
    {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        for (final String name : this.chunkNames) {
            builder.add(name);
        }
        final JsonWriterFactory factory =
            Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE));
        final Path indexPath = this.outputDirectory.resolve(INDEX_FILE_NAME);
        try (Writer writer = Files.newBufferedWriter(indexPath, StandardCharsets.UTF_8);
            JsonWriter jsonWriter = factory.createWriter(writer)) {
            jsonWriter.writeArray(builder.build());
        }
    }

    /**
     * Read the next logical block from the input, skipping blank lines between blocks.
     *
     * @return the next block, or {@code null} at end of input
     * @throws IOException if the input cannot be read
     */
    private Block nextBlock()
        throws IOException
    {
        String line = this.readRaw();
        while (line != null && line.isBlank()) {
            line = this.readRaw();
        }
        if (line == null) {
            return null;
        }
        if (isHeading(line)) {
            return new Block(BlockType.HEADING, List.of(line));
        }
        final String peek = this.readRaw();
        if (isTableRow(line) && peek != null && isTableSeparator(peek)) {
            return this.readTable(line, peek);
        }
        if (peek != null) {
            this.pushBack(peek);
        }
        return this.readParagraph(line);
    }

    /**
     * Read a table block: the header row, its separator and all following row lines.
     *
     * @param first the table header row
     * @param separator the table separator row
     * @return the table block
     * @throws IOException if the input cannot be read
     */
    private Block readTable(final String first, final String separator)
        throws IOException
    {
        final List<String> lines = new ArrayList<>();
        lines.add(first);
        lines.add(separator);
        String line = this.readRaw();
        while (line != null && isTableRow(line)) {
            lines.add(line);
            line = this.readRaw();
        }
        if (line != null && !line.isBlank()) {
            this.pushBack(line);
        }
        return new Block(BlockType.TABLE, lines);
    }

    /**
     * Read a paragraph block: consecutive non-blank lines up to the next blank line or heading.
     *
     * @param first the first line of the paragraph
     * @return the paragraph block
     * @throws IOException if the input cannot be read
     */
    private Block readParagraph(final String first)
        throws IOException
    {
        final List<String> lines = new ArrayList<>();
        lines.add(first);
        String line = this.readRaw();
        while (line != null && !line.isBlank() && !isHeading(line)) {
            lines.add(line);
            line = this.readRaw();
        }
        if (line != null && isHeading(line)) {
            this.pushBack(line);
        }
        return new Block(BlockType.OTHER, lines);
    }

    /**
     * Read a line, returning the pushed-back line first if one is pending.
     *
     * @return the next line, or {@code null} at end of input
     * @throws IOException if the input cannot be read
     */
    private String readRaw()
        throws IOException
    {
        if (this.pendingLine != null) {
            final String line = this.pendingLine;
            this.pendingLine = null;
            return line;
        }
        return this.reader.readLine();
    }

    /**
     * Push a single line back so the next read returns it.
     *
     * @param line the line to push back
     */
    private void pushBack(final String line)
    {
        this.pendingLine = line;
    }

    /**
     * Compute the target chunk size in characters from the requested token maximum, applying the
     * safety margin.
     *
     * @param maxTokens the requested maximum tokens per chunk
     * @return the target size in characters, never below {@link #MIN_BUDGET_CHARS}
     */
    private static long computeTargetChars(final int maxTokens)
    {
        final long target = (long) Math.floor(maxTokens * SAFETY_MARGIN * CHARS_PER_TOKEN);
        return Math.max(target, MIN_BUDGET_CHARS);
    }

    /**
     * The ATX heading level of a line, i.e. the number of leading {@code #} characters.
     *
     * @param line the line to inspect
     * @return the count of leading {@code #} characters, or 0 when there are none
     */
    private static int headingLevel(final String line)
    {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '#') {
            count++;
        }
        return count;
    }

    /**
     * Determine whether a line is an ATX heading ({@code #} through {@code ######} followed by a space
     * or the end of the line).
     *
     * @param line the line to inspect
     * @return {@code true} if the line is a heading
     */
    private static boolean isHeading(final String line)
    {
        final int level = headingLevel(line);
        if (level < 1 || level > MAX_HEADING_LEVEL) {
            return false;
        }
        return level == line.length() || line.charAt(level) == ' ';
    }

    /**
     * Determine whether a line could be a table row, i.e. it is non-blank and contains a pipe.
     *
     * @param line the line to inspect
     * @return {@code true} if the line looks like a table row
     */
    private static boolean isTableRow(final String line)
    {
        return !line.isBlank() && line.indexOf('|') >= 0;
    }

    /**
     * Determine whether a line is a Markdown table separator (only dashes, colons, pipes and spaces,
     * with at least one dash and one pipe).
     *
     * @param line the line to inspect
     * @return {@code true} if the line is a table separator
     */
    private static boolean isTableSeparator(final String line)
    {
        final String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        boolean dash = false;
        boolean pipe = false;
        for (int i = 0; i < trimmed.length(); i++) {
            final char character = trimmed.charAt(i);
            if (character == '-') {
                dash = true;
            } else if (character == '|') {
                pipe = true;
            } else if (character != ':' && character != ' ') {
                return false;
            }
        }
        return dash && pipe;
    }

    /**
     * Split oversized text into pieces that each fit the budget, breaking at sentence boundaries and
     * falling back to a hard character split for any single sentence that is still too long.
     *
     * @param text the text to split
     * @param maxChars the maximum size of each piece, in characters
     * @return the list of pieces
     */
    private static List<String> splitText(final String text, final long maxChars)
    {
        final List<String> pieces = new ArrayList<>();
        final StringBuilder buffer = new StringBuilder();
        for (final String sentence : splitSentences(text)) {
            packSentence(pieces, buffer, sentence, maxChars);
        }
        flushBuilder(pieces, buffer);
        return pieces;
    }

    /**
     * Split text into sentences at sentence-ending punctuation followed by whitespace.
     *
     * @param text the text to split
     * @return the sentences, in order
     */
    private static List<String> splitSentences(final String text)
    {
        return Arrays.asList(text.split("(?<=[.!?])\\s+"));
    }

    /**
     * Add one sentence to the running piece buffer, flushing the buffer first if the sentence would
     * not fit and hard-splitting the sentence if it is itself larger than the budget.
     *
     * @param pieces the accumulated pieces
     * @param buffer the running piece buffer
     * @param sentence the sentence to add
     * @param maxChars the maximum size of each piece, in characters
     */
    private static void packSentence(final List<String> pieces, final StringBuilder buffer,
        final String sentence, final long maxChars)
    {
        if (sentence.length() > maxChars) {
            flushBuilder(pieces, buffer);
            pieces.addAll(hardSplit(sentence, maxChars));
            return;
        }
        if (buffer.length() > 0 && buffer.length() + 1 + sentence.length() > maxChars) {
            flushBuilder(pieces, buffer);
        }
        if (buffer.length() > 0) {
            buffer.append(' ');
        }
        buffer.append(sentence);
    }

    /**
     * Move the contents of the buffer into the piece list, if any, and clear the buffer.
     *
     * @param pieces the accumulated pieces
     * @param buffer the running piece buffer
     */
    private static void flushBuilder(final List<String> pieces, final StringBuilder buffer)
    {
        if (buffer.length() > 0) {
            pieces.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    /**
     * Split text into fixed-size pieces by character count, as a last resort for content with no
     * usable break points.
     *
     * @param text the text to split
     * @param maxChars the maximum size of each piece, in characters
     * @return the list of pieces
     */
    private static List<String> hardSplit(final String text, final long maxChars)
    {
        final List<String> pieces = new ArrayList<>();
        final int size = (int) Math.max(1, Math.min(maxChars, Integer.MAX_VALUE - 1));
        int index = 0;
        while (index < text.length()) {
            final int end = Math.min(index + size, text.length());
            pieces.add(text.substring(index, end));
            index = end;
        }
        return pieces;
    }

    /**
     * Group table body rows into chunks that each fit the budget, never splitting a single row.
     *
     * @param rows the table body rows
     * @param budget the per-group character budget
     * @return the row groups; at least one group, possibly empty when there are no rows
     */
    private static List<List<String>> packRows(final List<String> rows, final long budget)
    {
        final List<List<String>> groups = new ArrayList<>();
        List<String> currentGroup = new ArrayList<>();
        long currentLength = 0;
        for (final String row : rows) {
            final long rowLength = row.length() + 1L;
            if (!currentGroup.isEmpty() && currentLength + rowLength > budget) {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentLength = 0;
            }
            currentGroup.add(row);
            currentLength += rowLength;
        }
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        if (groups.isEmpty()) {
            groups.add(new ArrayList<>());
        }
        return groups;
    }

    /**
     * Build a table part from the repeated header and a group of body rows.
     *
     * @param header the table header text
     * @param rows the body rows for this part
     * @return the rendered table part
     */
    private static String composeTable(final String header, final List<String> rows)
    {
        if (rows.isEmpty()) {
            return header;
        }
        return header + NEWLINE + String.join(NEWLINE, rows);
    }

    /**
     * The kind of a logical Markdown block.
     */
    private enum BlockType
    {
        /** A single ATX heading line. */
        HEADING,
        /** A GitHub-flavored Markdown table. */
        TABLE,
        /** Any other blank-line-delimited block, such as a paragraph or list. */
        OTHER
    }

    /**
     * A logical Markdown block: its kind and the raw lines that make it up.
     *
     * @param type the kind of block
     * @param lines the raw lines of the block, in order
     */
    private record Block(BlockType type, List<String> lines)
    {
        /**
         * The block rendered back to text, with its lines rejoined by newlines.
         *
         * @return the block text
         */
        String text()
        {
            return String.join(NEWLINE, this.lines);
        }
    }
}
