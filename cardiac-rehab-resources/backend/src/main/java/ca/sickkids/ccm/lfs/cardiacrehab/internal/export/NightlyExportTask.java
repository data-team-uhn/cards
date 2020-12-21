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

package ca.sickkids.ccm.lfs.cardiacrehab.internal.export;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import javax.json.JsonObject;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

public class NightlyExportTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(NightlyExportTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    NightlyExportTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;
    }

    @Override
    public void run()
    {
        LOGGER.info("Executing NightlyExport");
        LocalDate today = LocalDate.now();
        String fileDateString = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String requestDateString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        Set<SubjectIdentifier> changedSubjects = this.getChangedSubjects(requestDateString);

        for (SubjectIdentifier identifier : changedSubjects) {
            SubjectContents subjectContents = getSubjectContents(identifier.getPath(), requestDateString);
            if (subjectContents != null) {
                String filename = String.format(
                    "%s_formData_%s.json",
                    cleanString(identifier.getParticipantId()),
                    fileDateString);
                this.output(subjectContents, filename, fileDateString);
            }
        }
    }

    private String cleanString(String input)
    {
        return input.replaceAll("[^A-Za-z0-9]", "");
    }

    private static final class SubjectIdentifier
    {
        private String path;

        private String participantId;

        SubjectIdentifier(String path, String participantId)
        {
            this.path = path;
            this.participantId = participantId;
        }

        public String getPath()
        {
            return this.path;
        }

        public String getParticipantId()
        {
            return this.participantId;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.path.hashCode()) + Objects.hashCode(this.participantId.hashCode());
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || this.getClass() != obj.getClass()) {
                return false;
            }
            SubjectIdentifier other = (SubjectIdentifier) obj;
            return Objects.equals(this.path, other.getPath())
                && Objects.equals(this.participantId, other.getParticipantId());
        }

        @Override
        public String toString()
        {
            return String.format("{path:\"%s\",participantId:\"%s\"}", this.path, this.participantId);
        }
    }

    private static final class SubjectContents
    {
        private String data;

        private String url;

        SubjectContents(String data, String url)
        {
            this.data = data;
            this.url = url;
        }

        public String getData()
        {
            return this.data;
        }

        public String getUrl()
        {
            return this.url;
        }
    }

    private Set<SubjectIdentifier> getChangedSubjects(String requestDateString)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<SubjectIdentifier> subjects = new HashSet<>();
            String query = String.format(
                "SELECT subject.* FROM [lfs:Form] AS form INNER JOIN [lfs:Subject] AS subject"
                    + " ON form.'subject'=subject.[jcr:uuid] WHERE form.[jcr:created] >= '%s'",
                requestDateString
            );

            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource subject = results.next();
                String path = subject.getPath();
                String participantId = subject.getValueMap().get("identifier", String.class);
                subjects.add(new SubjectIdentifier(path, participantId));
            }
            return subjects;
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    private SubjectContents getSubjectContents(String path, String requestDateString)
    {
        String subjectDataUrl = String.format(
            "%s.data.deep.bare.-identify.relativeDates.dataFilter:createdAfter=%s",
            path,
            requestDateString
        );
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Resource subjectData = resolver.resolve(subjectDataUrl);
            return new SubjectContents(subjectData.adaptTo(JsonObject.class).toString(), subjectDataUrl);
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
            return null;
        }
    }

    private void output(SubjectContents input, String filename, String dateString)
    {
        /*
        String path = String.format("%s/cards-exports/%s/", System.getProperty("user.home"), dateString);
        File directory = new File(path);
        directory.mkdirs();
        try (FileWriter file = new FileWriter(path + filename)) {
            file.write(input.getData());
            LOGGER.info("Exported {} to {}", input.getUrl(), path + filename);
        } catch (IOException e) {
            LOGGER.error("Failed to perform the nightly export", e.getMessage(), e);
        }
        */
        LOGGER.warn("Setting up EndpointConfiguration");
        final EndpointConfiguration endpointConfig = new EndpointConfiguration("http://localhost:9000/", "us-west-1");
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(endpointConfig)
            .build();
        try {
            LOGGER.warn("START: s3.putObject()");
            s3.putObject("sample", "test.txt", input.getData());
            LOGGER.warn("END: s3.putObject()");
        } catch (AmazonServiceException e) {
            LOGGER.error("Failed to perform the nightly export", e.getMessage(), e);
        }
    }
}
