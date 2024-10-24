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

package io.uhndata.cards.s3export;

import java.io.IOException;
import java.io.InputStream;

import org.osgi.service.component.annotations.Component;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataStore;

@Component(immediate = true, service = DataStore.class)
public class S3DataStore implements DataStore
{
    private String env(final String value)
    {
        if (value != null && value.startsWith("%ENV%")) {
            return System.getenv(value.substring("%ENV%".length()));
        }
        return value;
    }

    @Override
    public String getName()
    {
        return "s3";
    }

    @Override
    public void store(final InputStream contents, final String filename, final String mimetype,
        final ExportConfigDefinition config) throws IOException
    {
        final String s3EndpointUrl =
            env(getNamedParameter(config.storageParameters(), "endpoint", "%ENV%S3_ENDPOINT_URL"));
        final String s3EndpointRegion =
            env(getNamedParameter(config.storageParameters(), "region", "%ENV%S3_ENDPOINT_REGION"));
        final String s3BucketName = env(getNamedParameter(config.storageParameters(), "bucket", "%ENV%S3_BUCKET_NAME"));
        final String awsKey = env(getNamedParameter(config.storageParameters(), "accessKey", "%ENV%AWS_KEY"));
        final String awsSecret = env(getNamedParameter(config.storageParameters(), "secretKey", "%ENV%AWS_SECRET"));
        final EndpointConfiguration endpointConfig =
            new EndpointConfiguration(s3EndpointUrl, s3EndpointRegion);
        final AWSCredentials credentials = new BasicAWSCredentials(awsKey, awsSecret);
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(endpointConfig)
            .withPathStyleAccessEnabled(true)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();
        try {
            final ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(mimetype);
            s3.putObject(s3BucketName, filename, contents, meta);
        } catch (Exception e) {
            throw new IOException("Failed to store file " + filename + " into S3 store " + getName(), e);
        }
    }
}
