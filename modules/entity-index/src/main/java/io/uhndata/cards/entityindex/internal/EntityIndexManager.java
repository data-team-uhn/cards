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
package io.uhndata.cards.entityindex.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.SortedSetSortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ExternalResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.entityindex.EntityIndexer;
import io.uhndata.cards.entityindex.IndexFields;
import io.uhndata.cards.entityindex.SearchQuery;
import io.uhndata.cards.entityindex.SearchResults;

/**
 * Maintains and searches the {@link EntityIndexer entity index}. The index lives in a Lucene directory on the local
 * filesystem, is bootstrapped by walking all the existing entities when empty, and is then kept up to date by
 * observing changes under the entity root. Changes usually become searchable within
 * {@link EntityIndexConfig#refresh_seconds()} of being saved.
 *
 * @version $Id$
 * @since 0.9.41
 */
@Component(service = EntityIndexer.class, immediate = true)
@Designate(ocd = EntityIndexConfig.class)
@SuppressWarnings({ "checkstyle:ClassDataAbstractionCoupling", "checkstyle:ClassFanOutComplexity" })
public class EntityIndexManager implements EntityIndexer, ResourceChangeListener, ExternalResourceChangeListener
{
    /** The subservice name used to obtain the read-only service session. */
    public static final String SUBSERVICE = "indexer";

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityIndexManager.class);

    /** How many times to retry the initial index bootstrap while the repository is starting up. */
    private static final int BOOTSTRAP_RETRIES = 30;

    private static final long BOOTSTRAP_RETRY_SECONDS = 10;

    /** The commit metadata key holding the schema version the index was built with. */
    private static final String SCHEMA_VERSION_KEY = "entityIndexSchema";

    /** Bumped when the document format changes in a way that requires rebuilding existing indexes. */
    private static final String DOCUMENT_FORMAT_VERSION = "2";

    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    private EntityIndexConfig config;

    private Directory directory;

    private Analyzer analyzer;

    private IndexWriter writer;

    private SearcherManager searcherManager;

    private EntityDocumentBuilder documentBuilder;

    private QueryTranslator translator;

    private ScheduledExecutorService scheduler;

    private ServiceRegistration<?> listenerRegistration;

    private final AtomicBoolean dirty = new AtomicBoolean();

    private int bootstrapAttempts;

    private String storedSchemaVersion;

    /**
     * Open the index and start the maintenance tasks.
     *
     * @param configuration the indexing schema
     * @param context the bundle context, used to register the observation listener
     * @throws IOException if the index cannot be opened
     */
    @Activate
    public void activate(final EntityIndexConfig configuration, final BundleContext context) throws IOException
    {
        this.config = configuration;
        if (!configuration.enabled()) {
            LOGGER.info("Entity index is disabled");
            return;
        }
        this.analyzer = new FieldAwareAnalyzer();
        this.translator = new QueryTranslator(this.analyzer);
        this.documentBuilder = new EntityDocumentBuilder(ItemRule.parseAll(configuration.item_rules()),
            configuration.container_types(), configuration.key_alias_prefix());
        final Path indexLocation = resolveIndexLocation(configuration, context);
        LOGGER.info("Opening entity index for {} at {}", configuration.entity_root(), indexLocation);
        this.directory = FSDirectory.open(indexLocation);
        this.writer = new IndexWriter(this.directory, new IndexWriterConfig(this.analyzer));
        this.storedSchemaVersion = readStoredSchemaVersion();
        this.writer.setLiveCommitData(
            Map.of(SCHEMA_VERSION_KEY, currentSchemaVersion(configuration)).entrySet());
        this.searcherManager = new SearcherManager(this.writer, null);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleWithFixedDelay(this::refresh,
            configuration.refresh_seconds(), configuration.refresh_seconds(), TimeUnit.SECONDS);
        this.scheduler.scheduleWithFixedDelay(this::commit,
            configuration.commit_seconds(), configuration.commit_seconds(), TimeUnit.SECONDS);
        this.scheduler.schedule(this::bootstrap, 1, TimeUnit.SECONDS);
        registerListener(context, configuration.entity_root());
    }

    /**
     * Stop the maintenance tasks and close the index.
     */
    @Deactivate
    public void deactivate()
    {
        if (this.listenerRegistration != null) {
            this.listenerRegistration.unregister();
            this.listenerRegistration = null;
        }
        if (this.scheduler != null) {
            this.scheduler.shutdown();
            try {
                this.scheduler.awaitTermination(30, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeQuietly(this.searcherManager);
        closeQuietly(this.writer);
        closeQuietly(this.directory);
        closeQuietly(this.analyzer);
    }

    @Override
    public void index(final String entityPath)
    {
        if (this.writer == null) {
            return;
        }
        try (ResourceResolver resolver = getServiceResolver()) {
            indexWithSession(entityPath, resolver.adaptTo(Session.class));
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get an indexing session: {}", e.getMessage(), e);
        }
    }

    @Override
    public void delete(final String entityPath)
    {
        if (this.writer == null) {
            return;
        }
        try {
            this.writer.deleteDocuments(new Term(IndexFields.PATH, entityPath));
            this.dirty.set(true);
        } catch (final IOException e) {
            LOGGER.warn("Failed to unindex {}: {}", entityPath, e.getMessage(), e);
        }
    }

    @Override
    public synchronized void reindexAll()
    {
        if (this.writer == null) {
            return;
        }
        LOGGER.info("Rebuilding the entity index for {}", this.config.entity_root());
        final long start = System.currentTimeMillis();
        long count = 0;
        try (ResourceResolver resolver = getServiceResolver()) {
            final Session session = resolver.adaptTo(Session.class);
            this.writer.deleteAll();
            if (session.nodeExists(this.config.entity_root())) {
                final NodeIterator entities = session.getNode(this.config.entity_root()).getNodes();
                while (entities.hasNext()) {
                    if (indexEntity(entities.nextNode())) {
                        ++count;
                    }
                }
            }
            this.writer.commit();
            this.searcherManager.maybeRefreshBlocking();
            LOGGER.info("Indexed {} entities in {}ms", count, System.currentTimeMillis() - start);
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get a reindexing session: {}", e.getMessage(), e);
        } catch (final IOException | RepositoryException e) {
            LOGGER.error("Failed to rebuild the entity index: {}", e.getMessage(), e);
        }
    }

    @Override
    public long getIndexedEntityCount()
    {
        if (this.searcherManager == null) {
            return -1;
        }
        try {
            final IndexSearcher searcher = this.searcherManager.acquire();
            try {
                return searcher.getIndexReader().numDocs();
            } finally {
                this.searcherManager.release(searcher);
            }
        } catch (final IOException e) {
            LOGGER.warn("Failed to read the entity index size: {}", e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public SearchResults search(final SearchQuery query) throws IOException
    {
        if (this.searcherManager == null) {
            return new SearchResults(Collections.emptyList(), 0, 0);
        }
        final long start = System.currentTimeMillis();
        final IndexSearcher searcher = this.searcherManager.acquire();
        try {
            final Query luceneQuery = this.translator.translate(query, searcher);
            final TopDocs hits = searcher.search(luceneQuery, Math.max(1, query.getMaxHits()), getSort(query));
            final long total = searcher.count(luceneQuery);
            final List<String> paths = new ArrayList<>(hits.scoreDocs.length);
            final Set<String> pathField = Collections.singleton(IndexFields.PATH);
            for (final org.apache.lucene.search.ScoreDoc hit : hits.scoreDocs) {
                paths.add(searcher.getIndexReader().storedFields()
                    .document(hit.doc, pathField).get(IndexFields.PATH));
            }
            return new SearchResults(paths, total, System.currentTimeMillis() - start);
        } finally {
            this.searcherManager.release(searcher);
        }
    }

    @Override
    public void onChange(final List<ResourceChange> changes)
    {
        // Multiple changes on the descendants of an entity all map to one reindex of the whole entity, and a removal
        // of the entity itself overrides reindexing it
        final Map<String, Boolean> entityToRemoval = new LinkedHashMap<>();
        for (final ResourceChange change : changes) {
            final String entityPath = entityRoot(change.getPath());
            if (entityPath == null) {
                continue;
            }
            final boolean removed = change.getType() == ResourceChange.ChangeType.REMOVED
                && entityPath.equals(change.getPath());
            entityToRemoval.merge(entityPath, removed, Boolean::logicalOr);
        }
        if (entityToRemoval.isEmpty()) {
            return;
        }
        try (ResourceResolver resolver = getServiceResolver()) {
            final Session session = resolver.adaptTo(Session.class);
            for (final Map.Entry<String, Boolean> entry : entityToRemoval.entrySet()) {
                if (entry.getValue()) {
                    delete(entry.getKey());
                } else {
                    indexWithSession(entry.getKey(), session);
                }
            }
        } catch (final LoginException e) {
            LOGGER.warn("Failed to get an indexing session: {}", e.getMessage(), e);
        }
    }

    /**
     * Map the path of a changed resource to the entity it belongs to.
     *
     * @param changedPath the path of a changed resource
     * @return the path of the entity containing the change, or {@code null} if the change is outside any entity
     */
    private String entityRoot(final String changedPath)
    {
        final String rootPrefix = this.config.entity_root() + "/";
        if (changedPath == null || !changedPath.startsWith(rootPrefix)) {
            return null;
        }
        final int nameEnd = changedPath.indexOf('/', rootPrefix.length());
        return nameEnd == -1 ? changedPath : changedPath.substring(0, nameEnd);
    }

    private void indexWithSession(final String entityPath, final Session session)
    {
        try {
            if (!session.nodeExists(entityPath)) {
                delete(entityPath);
                return;
            }
            indexEntity(session.getNode(entityPath));
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed to index {}: {}", entityPath, e.getMessage(), e);
        }
    }

    private boolean indexEntity(final Node entity)
    {
        try {
            if (!entity.isNodeType(this.config.entity_type())) {
                return false;
            }
            final Document document = this.documentBuilder.build(entity);
            this.writer.updateDocument(new Term(IndexFields.PATH, entity.getPath()), document);
            this.dirty.set(true);
            return true;
        } catch (final RepositoryException | IOException e) {
            LOGGER.warn("Failed to index entity: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * The schema version the index must be built with: the document format version plus a fingerprint of the
     * configured indexing rules. When it differs from the version stored in the index, a rebuild is needed.
     *
     * @param configuration the indexing schema
     * @return a version string
     */
    private String currentSchemaVersion(final EntityIndexConfig configuration)
    {
        final StringBuilder result = new StringBuilder(DOCUMENT_FORMAT_VERSION);
        result.append('/').append(configuration.entity_type());
        for (final ItemRule rule : ItemRule.parseAll(configuration.item_rules())) {
            result.append('/').append(rule.canonical());
        }
        for (final String container : configuration.container_types()) {
            result.append('/').append(container);
        }
        return result.toString();
    }

    private String readStoredSchemaVersion()
    {
        final Iterable<Map.Entry<String, String>> data = this.writer.getLiveCommitData();
        if (data != null) {
            for (final Map.Entry<String, String> entry : data) {
                if (SCHEMA_VERSION_KEY.equals(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * If the index is empty but the repository has entities, e.g. on the very first startup with this module
     * enabled, or if the index was built with a different schema, rebuild the index. Retried a few times to wait out
     * the repository initialization.
     */
    private void bootstrap()
    {
        try {
            final String targetVersion = currentSchemaVersion(this.config);
            if (getIndexedEntityCount() != 0 && !targetVersion.equals(this.storedSchemaVersion)) {
                LOGGER.info("Entity index schema changed from [{}] to [{}], rebuilding",
                    this.storedSchemaVersion, targetVersion);
                this.storedSchemaVersion = targetVersion;
                reindexAll();
                return;
            }
            if (getIndexedEntityCount() != 0) {
                return;
            }
            try (ResourceResolver resolver = getServiceResolver()) {
                final Session session = resolver.adaptTo(Session.class);
                if (!session.nodeExists(this.config.entity_root())
                    || !session.getNode(this.config.entity_root()).hasNodes()) {
                    return;
                }
            }
            reindexAll();
        } catch (final LoginException e) {
            // The service user or its mapping may not be installed yet, try again later
            if (++this.bootstrapAttempts < BOOTSTRAP_RETRIES && !this.scheduler.isShutdown()) {
                this.scheduler.schedule(this::bootstrap, BOOTSTRAP_RETRY_SECONDS, TimeUnit.SECONDS);
            } else {
                LOGGER.error("Giving up on bootstrapping the entity index: {}", e.getMessage(), e);
            }
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed to bootstrap the entity index: {}", e.getMessage(), e);
        }
    }

    private Sort getSort(final SearchQuery query)
    {
        if (StringUtils.isBlank(query.getSortField())) {
            return new Sort(new SortedNumericSortField(IndexFields.CREATED + IndexFields.NSORT_SUFFIX,
                SortField.Type.LONG, query.isSortDescending()));
        }
        if (query.isSortNumeric()) {
            return new Sort(new SortedNumericSortField(query.getSortField() + IndexFields.NSORT_SUFFIX,
                SortField.Type.LONG, query.isSortDescending()));
        }
        return new Sort(new SortedSetSortField(query.getSortField() + IndexFields.SORT_SUFFIX,
            query.isSortDescending()));
    }

    private void refresh()
    {
        if (this.dirty.compareAndSet(true, false)) {
            try {
                this.searcherManager.maybeRefresh();
            } catch (final IOException e) {
                LOGGER.warn("Failed to refresh the entity index searcher: {}", e.getMessage(), e);
            }
        }
    }

    private void commit()
    {
        try {
            if (this.writer.hasUncommittedChanges()) {
                this.writer.commit();
            }
        } catch (final IOException e) {
            LOGGER.warn("Failed to commit the entity index: {}", e.getMessage(), e);
        }
    }

    private ResourceResolver getServiceResolver() throws LoginException
    {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(ResourceResolverFactory.SUBSERVICE, SUBSERVICE);
        return this.resolverFactory.getServiceResourceResolver(parameters);
    }

    private Path resolveIndexLocation(final EntityIndexConfig configuration, final BundleContext context)
    {
        if (StringUtils.isNotBlank(configuration.index_path())) {
            return Paths.get(configuration.index_path());
        }
        final String slingHome = context.getProperty("sling.home");
        final String base = StringUtils.isNotBlank(slingHome) ? slingHome : System.getProperty("java.io.tmpdir");
        // Separate subdirectories per entity root, in case multiple index instances are configured
        return Paths.get(base, "entity-index" + this.config.entity_root().replace('/', '_'));
    }

    private void registerListener(final BundleContext context, final String entityRoot)
    {
        final Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(ResourceChangeListener.PATHS, new String[] { entityRoot });
        properties.put(ResourceChangeListener.CHANGES, new String[] { "ADDED", "CHANGED", "REMOVED" });
        this.listenerRegistration = context.registerService(
            new String[] { ResourceChangeListener.class.getName(), ExternalResourceChangeListener.class.getName() },
            this, properties);
    }

    private void closeQuietly(final AutoCloseable closeable)
    {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final Exception e) {
                LOGGER.debug("Failed to close index resource: {}", e.getMessage(), e);
            }
        }
    }
}
