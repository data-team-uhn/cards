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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import io.uhndata.cards.export.ExportConfigDefinition;
import io.uhndata.cards.export.spi.DataStore;

/**
 * Stores files on a remote S3 server. The connection to the S3 store is configured using the following
 * {@link ExportConfigDefinition#storageParameters() storage configuration parameter}: {@code endpoint}, {@code region},
 * {@code bucket}, {@code accessKey}, {@code secretKey}. If the S3 store forbids uploading files with an
 * {@code application/*} MIME type, it is possible to use {@code text/plain} as the MIME type to work around that
 * restriction by using {@code blockedApplicationMimeTypeWorkaround=true} as a storage configuration. For large files,
 * it is possible to use chunked uploads by specifying {@code chunkSizeInMB=5} as a storage configuration.
 *
 * @version $Id$
 * @since 0.9.26
 */
@Component(immediate = true, service = DataStore.class)
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
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
        final EndpointConfiguration endpointConfig =
            new EndpointConfiguration(s3EndpointUrl, s3EndpointRegion);
        final AWSCredentials credentials = new BasicAWSCredentials(awsKey, awsSecret);
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withPayloadSigningEnabled(true)
            .withEndpointConfiguration(endpointConfig)
            .withPathStyleAccessEnabled(true)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();

        String uploadId = null;
        try {
            final ObjectMetadata meta = new ObjectMetadata();
            // Some s3 buckets may forbid uploading "applications", so let's pretend that they're just plain text files
            meta.setContentType(
                "true".equals(getNamedParameter(config.storageParameters(), "blockedApplicationMimeTypeWorkaround"))
                    && mimetype.startsWith("application/") ? "text/plain" : mimetype);
            if (size >= 0) {
                meta.setContentLength(size);
            }

            final List<PartETag> partETags = new ArrayList<>();

            final InitiateMultipartUploadRequest initRequest =
                new InitiateMultipartUploadRequest(s3BucketName, filename).withObjectMetadata(meta);
            final InitiateMultipartUploadResult initResponse = s3.initiateMultipartUpload(initRequest);
            uploadId = initResponse.getUploadId();
            long position = 0;
            long partSize = getPartSize(config.storageParameters());
            for (int partNumber = 1; position < size; ++partNumber) {
                partSize = Math.min(partSize, (size - position));

                UploadPartRequest uploadRequest = new UploadPartRequest()
                    .withBucketName(s3BucketName)
                    .withKey(filename)
                    .withUploadId(uploadId)
                    .withInputStream(contents)
                    .withPartNumber(partNumber)
                    .withPartSize(partSize);
                uploadRequest.getRequestClientOptions().setReadLimit((int) uploadRequest.getPartSize() + 1);

                UploadPartResult uploadResult = s3.uploadPart(uploadRequest);
                partETags.add(uploadResult.getPartETag());
                position += partSize;
            }

            CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(s3BucketName, filename,
                initResponse.getUploadId(), partETags);
            s3.completeMultipartUpload(compRequest);
        } catch (Exception e) {
            if (uploadId != null) {
                s3.abortMultipartUpload(new AbortMultipartUploadRequest(s3BucketName, filename, uploadId));
            }
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
                    // Long multiplication, since a chunk size of 2048 MB or more overflows an int
                    return size * 1024L * 1024L;
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
}
