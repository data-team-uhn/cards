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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import io.uhndata.cards.forms.internal.extraction.ProposalCatalog.Chunk;
import io.uhndata.cards.forms.internal.extraction.ProposalCatalog.TagMetadata;

/**
 * The synthetic single-chunk view of an unchunked proposal document. When the chunker records
 * {@code chunked: false} in {@code outline.json} (the document is under the active model's
 * {@code wholeDocumentTokenLimit}), there is no catalog and no chunk files — the intake and targeted-extraction
 * stages instead represent the whole document as this one synthetic chunk, reusing their catalog-driven payload
 * assembly and coverage tracking unchanged.
 *
 * @version $Id$
 */
public final class WholeDocument
{
    /** The chunk id under which the whole document is sent and tracked in {@code llm_call_tracker.jsonl}. */
    public static final String CHUNK_ID = "chunk001";

    private WholeDocument()
    {
        // Utility class, never instantiated.
    }

    /**
     * The synthetic catalog entry representing the whole document: id {@link #CHUNK_ID}, the document stem as
     * its heading, and no tagging metadata (there is no catalog to stamp).
     *
     * @param folder the proposal parse folder
     * @return the synthetic chunk
     */
    public static Chunk chunk(final ProposalParseFolder folder)
    {
        return new Chunk(CHUNK_ID, "", List.of(folder.stem()), List.of(),
            new TagMetadata(List.of(), "", 0.0, false, false, ""), List.of());
    }

    /**
     * Read the whole document's Markdown text.
     *
     * @param folder the proposal parse folder
     * @return the document Markdown
     * @throws IOException if the document markdown cannot be read — in whole-document mode the document is the
     *             only extraction input, so its absence is a hard error
     */
    public static String read(final ProposalParseFolder folder) throws IOException
    {
        return Files.readString(folder.documentMarkdown(), StandardCharsets.UTF_8);
    }
}
