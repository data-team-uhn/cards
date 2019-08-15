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
package ca.sickkids.ccm.lfs.vocabularies;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Utility class to load zip files.
 * @version $Id$
 */
public class VocabularyZipLoader
{
    /**
     * Loads zip file from a url into a temporary zip file.
     * @param path - url of the zip file
     * @param directory - directory in which the temporary zip file is to be loaded
     * @param fileName - name of the temporary zip file
     * @throws VocabularyIndexException thrown upon failure of zip file to load
     */
    public void loadZipHttp(String path, String directory, String fileName)
            throws VocabularyIndexException
    {
        HttpGet httpget = new HttpGet(path);
        httpget.setHeader("Content-Type", "application/json");

        CloseableHttpClient httpclient = HttpClientBuilder.create().build();
        CloseableHttpResponse httpresponse;

        try {
            httpresponse = httpclient.execute(httpget);

            if (httpresponse.getStatusLine().getStatusCode() < 400) {
                File.createTempFile(fileName, ".zip");
                FileOutputStream fileOutput = new FileOutputStream(directory + fileName + ".zip");

                httpresponse.getEntity().writeTo(fileOutput);

                httpresponse.close();
                httpclient.close();
            } else {
                String message = "Failed to load zip: " + httpresponse.getStatusLine().getStatusCode() + " http error";
                httpresponse.close();
                httpclient.close();
                throw new VocabularyIndexException(message);
            }
        } catch (ClientProtocolException e) {
            String message = "Failed to load zip: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        } catch (IOException e) {
            String message = "Failed to load zip: " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }

    /**
     * Loads a zip file from a local file location to a temporary zip file.
     * @param path - location of the local file to be accessed
     * @param directory - directory in which the temporary zip file is to be loaded
     * @param fileName - name of the temporary zip file
     * @throws VocabularyIndexException thrown upon failure of zip file to load
     */
    public void loadZipLocal(String path, String directory, String fileName)
            throws VocabularyIndexException
    {
        // String source = "./flat_NCIT_type_testcase.zip";
        try {
            File.createTempFile(fileName, ".zip");
            FileOutputStream fileOutputStream = new FileOutputStream(directory + fileName + ".zip");
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

            FileInputStream fileInputStream = new FileInputStream(path);
            ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

            zipInputStream.getNextEntry();

            try {
                int c;
                zipOutputStream.putNextEntry(new ZipEntry(fileName + ".txt"));
                while ((c = zipInputStream.read()) != -1) {
                    zipOutputStream.write(c);
                }
                zipOutputStream.closeEntry();
            } finally {
                fileInputStream.close();
                zipInputStream.close();
                zipOutputStream.close();
                fileOutputStream.close();
            }
        } catch (IOException e) {
            String message = "Error: Failed to load zip vocabulary locally. " + e.getMessage();
            throw new VocabularyIndexException(message, e);
        }
    }
}
