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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataStore;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

@Component(immediate = true, service = DataStore.class)
public class S3DataStore implements DataStore
{
    private static final Logger LOGGER = LoggerFactory.getLogger(S3DataStore.class);

    @Override
    public String getName()
    {
        return "s3";
    }

    @Override
    public void store(final InputStream contents, final long size, final String filename, final String mimetype,
        final ExportConfigDefinition config) throws IOException
    {
        final String s3EndpointUrl =
            env(getNamedParameter(config.storageParameters(), "endpoint", "%ENV%S3_ENDPOINT_URL"));
        final String s3EndpointRegion =
            env(getNamedParameter(config.storageParameters(), "region", "%ENV%S3_ENDPOINT_REGION"));
        final String s3BucketName = env(getNamedParameter(config.storageParameters(), "bucket", "%ENV%S3_BUCKET_NAME"));
        final String awsKey = env(getNamedParameter(config.storageParameters(), "accessKey", "%ENV%AWS_KEY"));
        final String awsSecret = env(getNamedParameter(config.storageParameters(), "secretKey", "%ENV%AWS_SECRET"));
        final S3Client s3 = S3Client.builder()
            .region(Region.of(s3EndpointRegion))
            .endpointOverride(URI.create(s3EndpointUrl))
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsKey, awsSecret)))
            .build();

        // Some s3 buckets may forbid uploading "applications", so let's pretend they're just plain text files
        final String safeMimetype =
            "true".equals(getNamedParameter(config.storageParameters(), "blockedApplicationMimeTypeWorkaround"))
                && mimetype.startsWith("application/") ? "text/plain" : mimetype;
        try {
            long partSize = getPartSize(config.storageParameters());
            if (size <= partSize) {
                simpleUpload(contents, size, s3, s3BucketName, filename, safeMimetype);
            } else {
                multipartUpload(contents, size, partSize, s3, s3BucketName, filename, safeMimetype);
            }
        } catch (Exception e) {
            throw new IOException("Failed to store file " + filename + " into S3 store " + getName(), e);
        }
    }

    private long getPartSize(final String[] parameters)
    {
        final String sizeStr = getNamedParameter(parameters, "chunkSizeInMB");
        if (!StringUtils.isBlank(sizeStr)) {
            try {
                final int size = Integer.parseInt(sizeStr);
                if (size > 0) {
                    return size * 1024 * 1024;
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid chink size configured for the S3 storage: {}", sizeStr);
            }
        }
        return 10 * 1024 * 1024;
    }

    private String env(final String value)
    {
        if (value != null && value.startsWith("%ENV%")) {
            return System.getenv(value.substring("%ENV%".length()));
        }
        return value;
    }

    private void simpleUpload(final InputStream contents, final long size, S3Client s3, String s3BucketName,
        String filename, String mimetype)
    {
        s3.putObject(PutObjectRequest.builder()
            .bucket(s3BucketName)
            .key(filename)
            .contentType(mimetype)
            .build(),
            RequestBody.fromInputStream(contents, size));
    }

    private void multipartUpload(final InputStream contents, final long size, final long maxSize, final S3Client s3,
        final String s3BucketName, final String filename, final String mimetype)
    {
        final String uploadId = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
            .bucket(s3BucketName)
            .key(filename)
            .contentType(mimetype)
            .build()).uploadId();

        final List<CompletedPart> parts = new ArrayList<>();

        long position = 0;
        long partSize = maxSize;
        for (int partNumber = 1; position < size; ++partNumber) {
            partSize = Math.min(maxSize, (size - position));

            UploadPartResponse uploadResult = s3.uploadPart(
                UploadPartRequest.builder().bucket(s3BucketName).key(filename).uploadId(uploadId)
                    .partNumber(partNumber).contentLength(partSize).build(),
                RequestBody.fromInputStream(contents, partSize));
            parts.add(CompletedPart.builder().partNumber(partNumber).eTag(uploadResult.eTag()).build());

            position += partSize;
        }
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder().parts(parts).build();
        s3.completeMultipartUpload(
            CompleteMultipartUploadRequest.builder().bucket(s3BucketName).key(filename).uploadId(uploadId)
                .multipartUpload(completedMultipartUpload).build());
    }
}
