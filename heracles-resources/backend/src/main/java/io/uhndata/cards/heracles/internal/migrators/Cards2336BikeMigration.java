/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.heracles.internal.migrators;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.version.VersionManager;

import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.migrators.spi.DataMigrator;

@Component(immediate = true)
public class Cards2336BikeMigration implements DataMigrator
{
    private static final String QUESTION_PATH = "/Questionnaires/CPET Interpretation/CardiacStressTest/cpet_prot";

    private static final Logger LOGGER = LoggerFactory.getLogger(Cards2336BikeMigration.class);

    @Reference
    private FormUtils formUtils;

    @Override
    public String getName()
    {
        return "CARDS-2336: Migrate CPET Interpretation Protocol's  \"Bike\" answer to \"Bike 50 rpm\"";
    }

    @Override
    public int getPriority()
    {
        return 0;
    }

    @Override
    public boolean shouldRun(Version previousVersion, Version currentVersion, Session session)
    {
        return previousVersion.compareTo(Version.valueOf("0.9.18")) < 0;
    }

    @Override
    public void run(Version previousVersion, Version currentVersion, Session session)
    {
        try {
            if (!session.nodeExists(QUESTION_PATH)) {
                return;
            }

            VersionManager versionManager = session.getWorkspace().getVersionManager();
            final List<String> formsToCheckin = new ArrayList<>();

            final String id = session.getNode(QUESTION_PATH).getIdentifier();
            final NodeIterator answers = session.getWorkspace().getQueryManager().createQuery(
                "select answer.* from [cards:TextAnswer] as answer"
                    + " where answer.question = '" + id + "' and answer.value = 'Bike'",
                Query.JCR_SQL2).execute().getNodes();

            while (answers.hasNext()) {
                Node answer = answers.nextNode();
                Node form = this.formUtils.getForm(answer);
                final boolean wasCheckedOut = versionManager.isCheckedOut(form.getPath());
                if (!wasCheckedOut) {
                    versionManager.checkout(form.getPath());
                    formsToCheckin.add(form.getPath());
                }
                answer.setProperty("value", "Bike 50 rpm");
            }
            session.save();
            formsToCheckin.forEach(f -> {
                try {
                    versionManager.checkin(f);
                } catch (RepositoryException e) {
                    LOGGER.warn("Failed to checkin {}: {}", f, e);
                }
            });
        } catch (RepositoryException e) {
            LOGGER.error("Failed to run migrator {}: {}", getName(), e.getMessage(), e);
        }
    }
}
