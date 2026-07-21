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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Tests for {@link DocxListOutlineRiskDetector}.
 *
 * @version $Id$
 */
public class DocxListOutlineRiskDetectorTest
{
    private final DocxListOutlineRiskDetector detector = new DocxListOutlineRiskDetector();

    @Test
    public void testStudySh2OutlineAsListIsDetected() throws IOException
    {
        final Path docx = resolveRepoFile("Utilities/Parsing/24-5450-studysh2.docx");
        Assume.assumeTrue("Fixture DOCX not present: " + docx, Files.isRegularFile(docx));
        final byte[] bytes = Files.readAllBytes(docx);
        Assert.assertTrue(this.detector.hasListOutlineRisk(bytes, docx.getFileName().toString()));
    }

    private static Path resolveRepoFile(final String relative)
    {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 8; depth++) {
            final Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
            if (current == null) {
                break;
            }
        }
        return Path.of(relative);
    }
}
