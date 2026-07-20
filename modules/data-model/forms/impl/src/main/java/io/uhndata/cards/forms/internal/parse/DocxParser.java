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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for DOCX files. Delegates orchestration to {@link SimpleDocumentParser} and supplies
 * {@link DoclingMarkdownGenerator} as the primary generator, with
 * {@link DocxMarkdownGenerator} (Apache POI) as the fallback.
 * <p>
 * Before running Docling, {@link DocxListOutlineRiskDetector} inspects the document: when it numbers
 * its section outline with a shared Word multi-level list (which Docling mis-groups), the Apache POI
 * generator is used as the primary path instead, since POI renders numbered paragraphs inline in
 * document order.
 * </p>
 *
 * @version $Id$
 */
public class DocxParser extends SimpleDocumentParser
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DocxParser.class);

    private final DocxMarkdownGenerator poiGenerator = new DocxMarkdownGenerator();

    private final DocxListOutlineRiskDetector outlineRiskDetector = new DocxListOutlineRiskDetector();

    @Override
    protected String runPrimaryGenerator(final byte[] content, final String fileName)
    {
        if (this.outlineRiskDetector.hasListOutlineRisk(content, fileName)) {
            LOGGER.info("Routing DOCX '{}' to Apache POI to avoid Docling list-grouping scramble", fileName);
            return this.runPoiGenerator(content, fileName);
        }
        return super.runPrimaryGenerator(content, fileName);
    }

    @Override
    protected String runFallbackGenerator(final byte[] content, final String fileName)
    {
        return this.runPoiGenerator(content, fileName);
    }

    private String runPoiGenerator(final byte[] content, final String fileName)
    {
        try {
            return this.poiGenerator.toMarkdown(new ByteArrayInputStream(content), fileName);
        } catch (IOException | LinkageError e) {
            LOGGER.warn("Apache POI DOCX generation failed for '{}': {}", fileName, e.getMessage());
            return "";
        }
    }
}
