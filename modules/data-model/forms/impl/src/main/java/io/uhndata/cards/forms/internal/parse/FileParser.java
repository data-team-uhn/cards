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

import java.io.InputStream;

/**
 * Parse text out of a document stream.
 *
 * @version $Id$
 */
public interface FileParser
{
    /**
     * Parse text from the provided stream, saving the resulting markdown under the default output
     * directory (no per-answer subfolder).
     *
     * @param stream the input document stream
     * @param fileName source file name
     * @return parsed markdown content
     * @throws DocumentParseException when the document stream cannot be read
     */
    default String parse(InputStream stream, String fileName)
    {
        return parse(stream, fileName, null);
    }

    /**
     * Parse text from the provided stream, saving the resulting markdown into the given subfolder of
     * the output directory.
     *
     * @param stream the input document stream
     * @param fileName source file name
     * @param outputSubfolder subfolder of the output directory to save the markdown into (typically
     *            the owning answer's UUID); when {@code null} or blank, the output directory root is used
     * @return parsed markdown content
     * @throws DocumentParseException when the document stream cannot be read
     */
    String parse(InputStream stream, String fileName, String outputSubfolder);
}
