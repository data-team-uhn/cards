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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the proposal-pipeline prompt and JSON-schema resources bundled under {@code /prompts} on the classpath.
 * The prompt text and structured-output schemas are the source of truth for the gate and intake calls; they
 * ship inside this bundle so the running instance never depends on the {@code Utilities/Parsing} working copy.
 * Loaded resources are cached, since they are immutable for the life of the bundle.
 *
 * @version $Id$
 */
public final class PipelinePrompts
{
    /** The gate (Stage 0.5) system prompt. */
    public static final String IS_PROTOCOL_SYSTEM = "/prompts/is_protocol_system.md";

    /** The gate (Stage 0.5) structured-output JSON schema. */
    public static final String IS_PROTOCOL_SCHEMA = "/prompts/is_protocol_schema.json";

    /** The B.1–B.17 protocol-structure glossary shared by the gate and intake calls. */
    public static final String PROTOCOL_STRUCTURE_GLOSSARY = "/prompts/protocol_structure_glossary.md";

    /** The intake (Stage 1.1) system prompt. */
    public static final String STEP1_INTAKE_SYSTEM = "/prompts/step1_intake_system.md";

    /** The intake (Stage 1.1) structured-output JSON schema. */
    public static final String STEP1_INTAKE_SCHEMA = "/prompts/step1_intake_schema.json";

    /** The targeted-extraction (Stage 1.2) system prompt. */
    public static final String STEP2_EXTRACTION_SYSTEM = "/prompts/step2_extraction_system.md";

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PipelinePrompts()
    {
        // Utility class, never instantiated.
    }

    /**
     * Load a bundled resource as a UTF-8 string, caching the result. The resources are build-time artifacts, so a
     * missing or unreadable one is a packaging error and is surfaced as an unchecked exception.
     *
     * @param resourcePath the absolute classpath resource path, e.g. {@link #IS_PROTOCOL_SYSTEM}
     * @return the resource content, trailing whitespace preserved
     */
    public static String load(final String resourcePath)
    {
        return CACHE.computeIfAbsent(resourcePath, PipelinePrompts::read);
    }

    private static String read(final String resourcePath)
    {
        try (InputStream in = PipelinePrompts.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Bundled prompt resource not found on the classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not load bundled prompt resource " + resourcePath, e);
        }
    }
}
