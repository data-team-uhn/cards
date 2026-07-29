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

package io.uhndata.cards.errortracking;

public final class ErrorLogger
{
    /** Written by the service component as it starts and stops, read from arbitrary threads. */
    private static volatile ErrorLoggerService errorLoggerService;

    // Hide the constructor
    private ErrorLogger()
    {
    }

    static void setService(ErrorLoggerService service)
    {
        errorLoggerService = service;
    }

    /*
     * Withdraws the service backing this facade, so that a stopped component, whose service references are gone,
     * is not called any more. A component that was already replaced by a newer one withdraws nothing.
     *
     * @param service the service that is going away
     */
    static void unsetService(ErrorLoggerService service)
    {
        if (errorLoggerService == service) {
            errorLoggerService = null;
        }
    }

    /*
     * Stores in the JCR, under /LoggedEvents, a nt:file node containing the passed stack trace.
     *
     * @param loggedError the Throwable containing the stack trace of the error that was thrown resulting
     * in the calling of this method
     */
    public static void logError(final Throwable loggedError)
    {
        if (errorLoggerService != null) {
            errorLoggerService.logError(loggedError);
        }
    }
}
