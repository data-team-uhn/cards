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
     * Loads zip file from a url into a temporary zip file by making a http GET request.
     * @param path - url of the zip file
     * @param directory - directory in which the zip file is to be placed, relative to the VocabularyZipLoader
     * @param fileName - name of the temporary zip file
     * @throws VocabularyIndexException thrown upon failure of zip file to load
     */
    public void loadZipHttp(String path, String directory, String fileName)
            throws VocabularyIndexException
    {
        // GEt request
        HttpGet httpget = new HttpGet(path);
        httpget.setHeader("Content-Type", "application/json");

        CloseableHttpClient httpclient = HttpClientBuilder.create().build();
        CloseableHttpResponse httpresponse;

        try {
            // Execute GET request
            httpresponse = httpclient.execute(httpget);

            if (httpresponse.getStatusLine().getStatusCode() < 400) {
                // If the http request is successful

                // Create temporary zip file and an OutputStream for writing content to it
                File.createTempFile(fileName, ".zip");
                FileOutputStream fileOutput = new FileOutputStream(directory + fileName + ".zip");

                // Write all of the contents of the request to the OutputStream
                httpresponse.getEntity().writeTo(fileOutput);

                // Close the client and response
                httpresponse.close();
                httpclient.close();
            } else {
                // If http request is not successful, close the client and response and throw an exception
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
     * Loads a local zip file to a temporary zip file based on a path relative to the VocabularyZipLoader instance.
     * @param path - path of the local file to be accessed relative to the VocabularyZipLoader
     * @param directory - directory in which the zip file is to be placed, relative to the VocabularyZipLoader
     * @param fileName - name of the temporary zip file
     * @throws VocabularyIndexException thrown upon failure of zip file to load
     */
    public void loadZipLocal(String path, String directory, String fileName)
            throws VocabularyIndexException
    {
        try {
            // Create
            File.createTempFile(fileName, ".zip");

            // OutuputStreams for creating the temp file
            FileOutputStream fileOutputStream = new FileOutputStream(directory + fileName + ".zip");
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

            // InputStreams for reading the new file
            FileInputStream fileInputStream = new FileInputStream(path);
            ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);

            // Set InputStream at first zipped file
            zipInputStream.getNextEntry();

            try {
                int c;
                zipOutputStream.putNextEntry(new ZipEntry(fileName + ".txt"));

                // While there is still content to read, read in the content and write it to the OutputStream
                while ((c = zipInputStream.read()) != -1) {
                    zipOutputStream.write(c);
                }

                // Close ZipEntry
                zipOutputStream.closeEntry();
            } finally {
                // Close InputStreams and OutputStreams
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
