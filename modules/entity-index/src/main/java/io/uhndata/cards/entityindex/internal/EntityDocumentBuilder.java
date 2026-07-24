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

import java.util.ArrayList;
import java.util.List;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.entityindex.IndexFields;

/**
 * Flattens a whole entity — the root node together with all its descendant items — into a single Lucene document,
 * following the field naming convention described in {@link IndexFields}.
 *
 * @version $Id$
 * @since 0.9.41
 */
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
class EntityDocumentBuilder
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityDocumentBuilder.class);

    /** Values longer than this are not indexed as exact keywords or sort keys, only as full text. */
    private static final int MAX_KEYWORD_LENGTH = 4096;

    /** Values are truncated to this length when used as sort keys. */
    private static final int MAX_SORT_KEY_LENGTH = 256;

    private final String[] itemTypes;

    private final String[] containerTypes;

    private final String keyProperty;

    private final String valueProperty;

    private final String noteProperty;

    private final String keyAliasPrefix;

    EntityDocumentBuilder(final String[] itemTypes, final String[] containerTypes, final String keyProperty,
        final String valueProperty, final String noteProperty, final String keyAliasPrefix)
    {
        this.itemTypes = itemTypes;
        this.containerTypes = containerTypes;
        this.keyProperty = keyProperty;
        this.valueProperty = valueProperty;
        this.noteProperty = noteProperty;
        this.keyAliasPrefix = keyAliasPrefix;
    }

    /**
     * Flatten an entity into an index document.
     *
     * @param entity the entity root node
     * @return a Lucene document holding all the indexable content of the entity
     * @throws RepositoryException if reading the entity fails
     */
    Document build(final Node entity) throws RepositoryException
    {
        final Document doc = new Document();
        final List<String> fulltext = new ArrayList<>();
        doc.add(new StringField(IndexFields.PATH, entity.getPath(), Store.YES));
        doc.add(new StringField(IndexFields.UUID, entity.getIdentifier(), Store.YES));
        doc.add(new StringField(IndexFields.TYPE, entity.getPrimaryNodeType().getName(), Store.NO));
        addQuestionnaire(entity, doc);
        addSubject(entity, doc, fulltext);
        addMultiString(entity, "relatedSubjects", IndexFields.RELATED_SUBJECTS, doc);
        addMultiString(entity, "statusFlags", IndexFields.STATUS_FLAGS, doc);
        addDate(entity, "jcr:created", IndexFields.CREATED, doc);
        addDate(entity, "jcr:lastModified", IndexFields.LAST_MODIFIED, doc);
        addString(entity, "jcr:createdBy", IndexFields.CREATED_BY, doc);
        addString(entity, "jcr:lastModifiedBy", IndexFields.LAST_MODIFIED_BY, doc);
        processChildren(entity, doc, fulltext);
        fulltext.forEach(text -> doc.add(new TextField(IndexFields.FULLTEXT, text, Store.NO)));
        return doc;
    }

    private void addQuestionnaire(final Node entity, final Document doc) throws RepositoryException
    {
        if (entity.hasProperty("questionnaire")) {
            try {
                final Node questionnaire = entity.getProperty("questionnaire").getNode();
                doc.add(new StringField(IndexFields.QUESTIONNAIRE, questionnaire.getIdentifier(), Store.YES));
                doc.add(new StringField(IndexFields.QUESTIONNAIRE_PATH, questionnaire.getPath(), Store.NO));
            } catch (final ItemNotFoundException e) {
                LOGGER.debug("Dangling questionnaire reference in {}", entity.getPath());
            }
        }
    }

    private void addSubject(final Node entity, final Document doc, final List<String> fulltext)
        throws RepositoryException
    {
        if (!entity.hasProperty("subject")) {
            return;
        }
        try {
            final Node subject = entity.getProperty("subject").getNode();
            doc.add(new StringField(IndexFields.SUBJECT, subject.getIdentifier(), Store.NO));
            if (subject.hasProperty("identifier")) {
                final String identifier = subject.getProperty("identifier").getString();
                doc.add(new StringField(IndexFields.SUBJECT_IDENTIFIER, identifier, Store.YES));
                fulltext.add(identifier);
            }
            if (subject.hasProperty("fullIdentifier")) {
                final String fullIdentifier = subject.getProperty("fullIdentifier").getString();
                doc.add(new StringField(IndexFields.SUBJECT_FULL_IDENTIFIER, fullIdentifier, Store.NO));
                fulltext.add(fullIdentifier);
            }
        } catch (final ItemNotFoundException e) {
            LOGGER.debug("Dangling subject reference in {}", entity.getPath());
        }
    }

    private void addMultiString(final Node entity, final String property, final String field, final Document doc)
        throws RepositoryException
    {
        if (entity.hasProperty(property)) {
            final Property p = entity.getProperty(property);
            final Value[] values = p.isMultiple() ? p.getValues() : new Value[] { p.getValue() };
            for (final Value value : values) {
                doc.add(new StringField(field, value.getString(), Store.NO));
            }
        }
    }

    private void addString(final Node entity, final String property, final String field, final Document doc)
        throws RepositoryException
    {
        if (entity.hasProperty(property)) {
            doc.add(new StringField(field, entity.getProperty(property).getString(), Store.NO));
        }
    }

    private void addDate(final Node entity, final String property, final String field, final Document doc)
        throws RepositoryException
    {
        if (entity.hasProperty(property)) {
            final Property p = entity.getProperty(property);
            final long epoch = p.getDate().getTimeInMillis();
            doc.add(new StringField(field, p.getString(), Store.NO));
            doc.add(new LongPoint(field + IndexFields.LONG_SUFFIX, epoch));
            doc.add(new SortedNumericDocValuesField(field + IndexFields.NSORT_SUFFIX, epoch));
        }
    }

    private void processChildren(final Node parent, final Document doc, final List<String> fulltext)
        throws RepositoryException
    {
        final NodeIterator children = parent.getNodes();
        while (children.hasNext()) {
            final Node child = children.nextNode();
            if (isAnyType(child, this.itemTypes)) {
                processItem(child, doc, fulltext);
            } else if (isAnyType(child, this.containerTypes)) {
                processChildren(child, doc, fulltext);
            }
        }
    }

    private boolean isAnyType(final Node node, final String[] types) throws RepositoryException
    {
        for (final String type : types) {
            if (node.isNodeType(type)) {
                return true;
            }
        }
        return false;
    }

    private void processItem(final Node item, final Document doc, final List<String> fulltext)
        throws RepositoryException
    {
        if (!item.hasProperty(this.keyProperty)) {
            return;
        }
        final Node key;
        try {
            key = item.getProperty(this.keyProperty).getNode();
        } catch (final ItemNotFoundException e) {
            LOGGER.debug("Dangling {} reference in {}", this.keyProperty, item.getPath());
            return;
        }
        final String uuid = key.getIdentifier();
        final String keyPath = key.getPath();
        final String alias = keyPath.startsWith(this.keyAliasPrefix)
            ? keyPath.substring(this.keyAliasPrefix.length()) : keyPath;
        doc.add(new StringField(IndexFields.QUESTIONS, uuid, Store.NO));
        final Value[] values = getValues(item);
        if (values.length > 0) {
            doc.add(new StringField(IndexFields.ANSWERED_QUESTIONS, uuid, Store.NO));
            for (final Value value : values) {
                addValue(doc, uuid, value, fulltext);
                addValue(doc, alias, value, null);
            }
        }
        if (item.hasProperty(this.noteProperty)) {
            final String note = item.getProperty(this.noteProperty).getString();
            if (StringUtils.isNotBlank(note)) {
                doc.add(new TextField(uuid + IndexFields.NOTE_SUFFIX, note, Store.NO));
                doc.add(new TextField(alias + IndexFields.NOTE_SUFFIX, note, Store.NO));
                fulltext.add(note);
            }
        }
    }

    private Value[] getValues(final Node item) throws RepositoryException
    {
        if (!item.hasProperty(this.valueProperty)) {
            return new Value[0];
        }
        final Property value = item.getProperty(this.valueProperty);
        return value.isMultiple() ? value.getValues() : new Value[] { value.getValue() };
    }

    private void addValue(final Document doc, final String key, final Value value, final List<String> fulltext)
        throws RepositoryException
    {
        switch (value.getType()) {
            case PropertyType.LONG:
                addNumber(doc, key, String.valueOf(value.getLong()), value.getLong(), value.getLong());
                break;
            case PropertyType.DOUBLE:
                addDecimal(doc, key, String.valueOf(value.getDouble()), value.getDouble());
                break;
            case PropertyType.DECIMAL:
                addDecimal(doc, key, value.getDecimal().toPlainString(), value.getDecimal().doubleValue());
                break;
            case PropertyType.BOOLEAN:
                addNumber(doc, key, value.getBoolean() ? "1" : "0", value.getBoolean() ? 1 : 0,
                    value.getBoolean() ? 1 : 0);
                break;
            case PropertyType.DATE:
                final long epoch = value.getDate().getTimeInMillis();
                doc.add(new StringField(key, value.getString(), Store.NO));
                doc.add(new LongPoint(key + IndexFields.LONG_SUFFIX, epoch));
                doc.add(new SortedNumericDocValuesField(key + IndexFields.NSORT_SUFFIX, epoch));
                break;
            default:
                addText(doc, key, value.getString(), fulltext);
                break;
        }
    }

    private void addNumber(final Document doc, final String key, final String stringForm, final long exact,
        final long sortKey)
    {
        doc.add(new StringField(key, stringForm, Store.NO));
        doc.add(new LongPoint(key + IndexFields.LONG_SUFFIX, exact));
        doc.add(new DoublePoint(key + IndexFields.DOUBLE_SUFFIX, exact));
        doc.add(new SortedNumericDocValuesField(key + IndexFields.NSORT_SUFFIX, sortKey));
    }

    private void addDecimal(final Document doc, final String key, final String stringForm, final double exact)
    {
        doc.add(new StringField(key, stringForm, Store.NO));
        doc.add(new DoublePoint(key + IndexFields.DOUBLE_SUFFIX, exact));
        doc.add(new SortedNumericDocValuesField(key + IndexFields.NSORT_SUFFIX,
            NumericUtils.doubleToSortableLong(exact)));
    }

    private void addText(final Document doc, final String key, final String text, final List<String> fulltext)
    {
        if (text.length() <= MAX_KEYWORD_LENGTH) {
            doc.add(new StringField(key, text, Store.NO));
            doc.add(new SortedSetDocValuesField(key + IndexFields.SORT_SUFFIX,
                new BytesRef(StringUtils.truncate(text, MAX_SORT_KEY_LENGTH))));
        }
        doc.add(new TextField(key + IndexFields.TEXT_SUFFIX, text, Store.NO));
        if (fulltext != null) {
            fulltext.add(text);
        }
    }
}
