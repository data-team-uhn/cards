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

package io.uhndata.cards.export.internal.stores;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataStore;

@Component(immediate = true, service = DataStore.class)
public class FilesystemDataStore implements DataStore
{
    @Override
    public String getName()
    {
        return "filesystem";
    }

    @Override
    public void store(final InputStream contents, final String filename, final String mimetype,
        final ExportConfigDefinition config) throws IOException
    {
        final File targetFile =
            new File(getNamedParameter(config.storageParameters(), "savePath") + File.separatorChar + filename);
        targetFile.getCanonicalFile().getParentFile().mkdirs();
        targetFile.createNewFile();
        try (OutputStream outputStream = new FileOutputStream(targetFile)) {
            IOUtils.copy(contents, outputStream);
        }
    }
}
