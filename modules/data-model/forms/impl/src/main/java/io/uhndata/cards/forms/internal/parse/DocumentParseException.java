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

/**
 * Raised when a document cannot be read for parsing. The formatted note produced by
 * {@link #toNote(String, String)} is stored on the proposal answer so the frontend can
 * display the failure to the user.
 *
 * @version $Id$
 */
public class DocumentParseException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private static final String NOTE_PREFIX = "<!-- parse_error: ";

    private static final String NOTE_SUFFIX = " -->";

    /**
     * Create a new parse exception.
     *
     * @param message description of the failure
     * @param cause underlying I/O or processing error
     */
    public DocumentParseException(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Format a proposal answer note that the frontend recognizes as a parse error.
     *
     * @param fileName source file name
     * @param message error description
     * @return note text to persist on the answer
     */
    public static String toNote(final String fileName, final String message)
    {
        return NOTE_PREFIX + fileName + ": " + message + NOTE_SUFFIX;
    }

    /**
     * Determine whether a note value encodes a parse error.
     *
     * @param note persisted answer note
     * @return {@code true} when the note is a parse error marker
     */
    public static boolean isParseErrorNote(final String note)
    {
        return note != null && note.startsWith(NOTE_PREFIX);
    }
}
