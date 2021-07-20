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
package io.uhndata.cards.vocabularies.spi;

/**
 * Exception thrown by classes in the vocabularies module.
 *
 * @version $Id$
 */
public class VocabularyIndexException extends Exception
{
    private static final long serialVersionUID = -2896534123721251531L;

    /**
     * Constructs a new instance of this class with <code>null</code> as the error message.
     */
    public VocabularyIndexException()
    {
        super();
    }

    /**
     * Constructs a new instance of this class with the specified error message.
     *
     * @param message error message of the exception
     */
    public VocabularyIndexException(String message)
    {
        super(message);
    }

    /**
     * Constructs a new instance of this class with the specified message and cause.
     *
     * @param message error message for the exception
     * @param cause cause of the exception
     */
    public VocabularyIndexException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructs a new instance of this class with the specified cause for exception.
     *
     * @param cause cause of the exception
     */
    public VocabularyIndexException(Throwable cause)
    {
        super(cause);
    }
}
