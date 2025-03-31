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
package io.uhndata.cards.export.spi;

import java.io.IOException;
import java.io.InputStream;

import io.uhndata.cards.export.ExportConfigDefinition;

/**
 * The last step of the data export is storing the resource representations someplace. Implementations of this service
 * will do that, streaming {@link ResourceRepresentation#getRepresentation() the contents of resource representations}
 * to an external location, e.g. a file on disk or on a remote server.
 *
 * @version $Id$
 * @since 0.9.26
 */
public interface DataStore extends DataPipelineStep
{
    /**
     * Transfer the resource representation to the external storage.
     *
     * @param contents the resource representation to store
     * @param size the size of the input stream, if known, or {@code -1} otherwise
     * @param filename the desired file name for the resource
     * @param mimetype the MIME type of the representation
     * @param config the export process configuration, which may hold further customization for the storage process in
     *            the {@link ExportConfigDefinition#storageParameters()} settings
     * @throws IOException if storing the data fails
     */
    void store(InputStream contents, long size, String filename, String mimetype, ExportConfigDefinition config)
        throws IOException;
}
