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
package io.uhndata.cards.forms.internal.extraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates the on-disk parse artifacts of one proposal document under an answer's parse folder. The chunker
 * always writes a single {@code Chunks/} folder holding {@code outline.json} — plus, for chunked documents,
 * {@code catalog.json} and the {@code Chunk-*.md} files — beside the whole-document {@code <stem>.md} at the
 * answer-folder root. The outline's recorded {@code chunked} flag is the pipeline's binary routing decision:
 * small documents are deliberately left unchunked ({@link #isChunked()} is {@code false}) and every extraction
 * stage sends the whole document instead of selecting catalog chunks. The MVP processes a single protocol per
 * answer, so there is exactly one {@code Chunks/} folder to resolve; the document's stem is read from
 * {@code outline.json}'s {@code fileId} rather than a per-stem subfolder name.
 *
 * @version $Id$
 */
public final class ProposalParseFolder
{
    /** Name of the answer subfolder holding the chunk files, catalog and outline. */
    static final String CHUNKS_DIRNAME = "Chunks";

    /** The outline file name inside the chunks folder. */
    static final String OUTLINE_NAME = "outline.json";

    /** The catalog file name inside the chunks folder. */
    static final String CATALOG_NAME = "catalog.json";

    /** The append-only per-call coverage tracker written beside the catalog. */
    static final String TRACKER_NAME = "llm_call_tracker.jsonl";

    private final Path answerDir;

    private final String stem;

    private final boolean chunked;

    private ProposalParseFolder(final Path parseAnswerDir, final String documentStem, final boolean documentChunked)
    {
        this.answerDir = parseAnswerDir;
        this.stem = documentStem;
        this.chunked = documentChunked;
    }

    /**
     * Locate the proposal's chunk folder under an answer's parse folder. Resolution requires the chunker's
     * {@code Chunks/outline.json}; a chunked outline additionally requires {@code catalog.json} (its absence
     * means an incomplete parse). An unchunked outline resolves without a catalog — the document is served
     * whole.
     *
     * @param answerDir the absolute parse output folder of the answer (may be {@code null})
     * @return the resolved parse folder, or empty when the chunker has not (yet) written its outline
     */
    public static Optional<ProposalParseFolder> locate(final Path answerDir)
    {
        if (answerDir == null) {
            return Optional.empty();
        }
        final Path chunksDir = answerDir.resolve(CHUNKS_DIRNAME);
        final Path outlineFile = chunksDir.resolve(OUTLINE_NAME);
        if (!Files.isRegularFile(outlineFile)) {
            return Optional.empty();
        }
        try {
            final ParseOutline outline = ParseOutline.read(outlineFile);
            final boolean incomplete = outline.fileId().isEmpty()
                || (outline.chunked() && !Files.isRegularFile(chunksDir.resolve(CATALOG_NAME)));
            return incomplete ? Optional.empty()
                : Optional.of(
                    new ProposalParseFolder(answerDir, stripExtension(outline.fileId()), outline.chunked()));
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    private static String stripExtension(final String fileName)
    {
        final int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * The document's stem (the base name of the whole-document markdown, without extension).
     *
     * @return the stem
     */
    public String stem()
    {
        return this.stem;
    }

    /**
     * The chunker's recorded routing decision for this document: {@code true} when it was split into catalog
     * chunks, {@code false} when it was small enough to be deliberately left unchunked, in which case every
     * extraction stage must send the whole document ({@link #documentMarkdown()}) instead of catalog chunks.
     *
     * @return whether the document has a chunk catalog
     */
    public boolean isChunked()
    {
        return this.chunked;
    }

    /**
     * The answer's absolute parse output folder (the parent of {@link #chunksDir()}).
     *
     * @return the absolute answer parse folder
     */
    public Path answerDir()
    {
        return this.answerDir;
    }

    /**
     * The chunks folder {@code Chunks/}.
     *
     * @return the absolute chunks folder path
     */
    public Path chunksDir()
    {
        return this.answerDir.resolve(CHUNKS_DIRNAME);
    }

    /**
     * The {@code outline.json} inside the chunks folder.
     *
     * @return the absolute outline path
     */
    public Path outlineFile()
    {
        return this.chunksDir().resolve(OUTLINE_NAME);
    }

    /**
     * The {@code catalog.json} inside the chunks folder.
     *
     * @return the absolute catalog path
     */
    public Path catalogFile()
    {
        return this.chunksDir().resolve(CATALOG_NAME);
    }

    /**
     * The append-only {@code llm_call_tracker.jsonl} beside the catalog.
     *
     * @return the absolute tracker path
     */
    public Path trackerFile()
    {
        return this.chunksDir().resolve(TRACKER_NAME);
    }

    /**
     * The whole-document markdown {@code <stem>.md} at the answer-folder root.
     *
     * @return the absolute document markdown path
     */
    public Path documentMarkdown()
    {
        return this.answerDir.resolve(this.stem + ".md");
    }
}
