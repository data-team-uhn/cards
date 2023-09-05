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
package io.uhndata.cards.emailnotifications;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import io.uhndata.cards.forms.api.FormUtils;

/**
 * A read-only email template ready to be customized into an {@link Email}. An email template already defines the
 * sender, subject, HTML body template, text body template, reference properties to interpolate into the body templates,
 * and extra attachments to include. To create a template, use {@link #builder()} to obtain a new {@link Builder
 * template builder}, invoke the builder's method to set the required values, then {@link Builder#build() build} the
 * template. To instantiate a template into an actual email for a specific subject ready to be sent, use
 * {@link #getEmailBuilderForSubject}. This already looks up all the answers for the questions referenced in
 * {@link #getExtraProperties()} and interpolates the body templates.
 * <p>
 * An alternative way to configure an email template is through {@code cards:EmailTemplate} nodes. Each such node
 * defines all the required properties like the Sender and {@link #getSubject() Subject}, the {@link #getHtmlTemplate()
 * HTML} and {@link #getTextTemplate() text templates}, can hold other properties to be used as
 * {@link #getExtraProperties() extra properties}, and any other children will be used as {@link #getInlineAttachments()
 * attachments}. To build a template starting from such a Node, use {@link #builder(Node, ResourceResolver)}.
 * </p>
 *
 * @see Email
 * @version $Id$
 */
public class EmailTemplate
{
    /** JCR nodetype for nodes holding email templates. */
    public static final String NODETYPE = "cards:EmailTemplate";

    /** JCR nodetype for nodes holding extra attachments to include in the email. */
    public static final String OTHER_ATTACHMENTS_NODETYPE = "nt:file";

    /** Property of template nodes holding the sender email address. */
    public static final String SENDER_ADDRESS_PROPERTY = "senderAddress";

    /** Property of template nodes holding the sender display name. */
    public static final String SENDER_NAME_PROPERTY = "senderName";

    /** Property of template nodes holding the (optional) reply-to email address. */
    public static final String REPLY_TO_ADDRESS_PROPERTY = "replyToAddress";

    /** Property of template nodes holding the (optional) reply-to display name. */
    public static final String REPLY_TO_NAME_PROPERTY = "replyToName";

    /** Property of template nodes holding the subject line. */
    public static final String SUBJECT_PROPERTY = "subject";

    /** Name of the child node holding the HTML body template. */
    public static final String HTML_TEMPLATE_NODE = "bodyTemplate.html";

    /** Name of the child node holding the fallback plain text template. */
    public static final String TEXT_TEMPLATE_NODE = "bodyTemplate.txt";

    /** The node where elements common to all email templates can be placed. */
    public static final String COMMON_TEMPLATE_NODE = "/apps/cards/mailTemplates/";

    /** The node where inline attachments common to all email templates can be placed. */
    public static final String COMMON_ATTACHMENTS_NODE = COMMON_TEMPLATE_NODE + "commonAttachments";

    /**
     * An optional child node of the email template, holding a prefix for the HTML body template. If present, this will
     * override the common HTML header and will be added before the HTML body template of each email template.
     */
    public static final String HTML_BODY_HEADER = "bodyTemplate.header.html";

    /**
     * An optional node holding a common prefix for the HTML body template. If present, this will be added before the
     * HTML body template of each email template.
     */
    public static final String COMMON_HTML_BODY_HEADER = COMMON_TEMPLATE_NODE + HTML_BODY_HEADER;

    /**
     * An optional child node of the email template, holding a suffix for the HTML body template. If present, this will
     * override the common HTML footer and will be added after the HTML body template of each email template.
     */
    public static final String HTML_BODY_FOOTER = "bodyTemplate.footer.html";

    /**
     * An optional node holding a common suffix for the HTML body template. If present, this will be added after the
     * HTML body template of each email template.
     */
    public static final String COMMON_HTML_BODY_FOOTER = COMMON_TEMPLATE_NODE + HTML_BODY_FOOTER;

    /**
     * An optional child node of the email template, holding a prefix for the plain text body template. If present, this
     * will override the common plain text header and will be added before the body template of each email template.
     */
    public static final String TEXT_BODY_HEADER = "bodyTemplate.header.txt";

    /**
     * An optional node holding a common prefix for the plain text body template. If present, this will be added before
     * the text body template of each email template.
     */
    public static final String COMMON_TEXT_BODY_HEADER = COMMON_TEMPLATE_NODE + TEXT_BODY_HEADER;

    /**
     * An optional child node of the email template, holding a suffix for the plain text body template. If present, this
     * will override the common plain text footer and will be added after the body template of each email template.
     */
    public static final String TEXT_BODY_FOOTER = "bodyTemplate.footer.txt";

    /**
     * An optional node holding a common suffix for the plain text body template. If present, this will be added after
     * the text body template of each email template.
     */
    public static final String COMMON_TEXT_BODY_FOOTER = COMMON_TEMPLATE_NODE + TEXT_BODY_FOOTER;

    private String senderAddress;

    private String senderName;

    private String replyToAddress;

    private String replyToName;

    private String subject;

    private String htmlTemplate;

    private String textTemplate;

    private final Map<String, String> properties = new HashMap<>();

    private final List<Triple<String, String, byte[]>> inlineAttachments = new LinkedList<>();

    protected EmailTemplate()
    {
        // Nothing to do, this just makes the class uninstantiable
    }

    protected EmailTemplate(final EmailTemplate other)
    {
        this.senderAddress = other.senderAddress;
        this.senderName = other.senderName;
        this.replyToAddress = other.replyToAddress;
        this.replyToName = other.replyToName;
        this.subject = other.subject;
        this.htmlTemplate = other.htmlTemplate;
        this.textTemplate = other.textTemplate;
        this.inlineAttachments.addAll(other.inlineAttachments);
        this.properties.putAll(other.properties);
    }

    /**
     * The email address the email is sent from, part of the {@code From} email header together with
     * {@link #getSenderName()}.
     *
     * @return an email address
     */
    public String getSenderAddress()
    {
        return this.senderAddress;
    }

    /**
     * The name displayed as the sender, part of the {@code From} email header together with
     * {@link #getSenderAddress()}.
     *
     * @return a name
     */
    public String getSenderName()
    {
        return this.senderName;
    }

    /**
     * The email address to send replies to, part of the {@code Reply-To} email header together with
     * {@link #getReplyToName()}.
     *
     * @return an email address
     */
    public String getReplyToAddress()
    {
        return StringUtils.defaultIfBlank(this.replyToAddress, getSenderAddress());
    }

    /**
     * The name displayed as the reply-to destination, part of the {@code Reply-To} email header together with
     * {@link #getReplyToAddress()}.
     *
     * @return a name
     */
    public String getReplyToName()
    {
        return StringUtils.defaultIfBlank(this.replyToName, getSenderName());
    }

    /**
     * The title of the email, the {@code Subject} email header.
     *
     * @return a subject
     */
    public String getSubject()
    {
        // TODO Add support for variable interpolation in the subject
        return this.subject;
    }

    /**
     * A set of extra properties to include as variables available for substitution in the email body templates. The key
     * of the map is the variable name exposed in the template. The value can be either a path to a JCR question node, a
     * path to a JCR property, or a simple value. when instantiating the template into an actual email for a specific
     * subject:
     * <ul>
     * <li>If it's a question reference, an answer to it for the subject will be looked up, and the first such answer,
     * if any, will be used in the template.
     * <li>If it's a JCR property, the value of the property will be used.
     * <li>If it's a simple value, not a JCR reference, the value is used as-is.</li>
     * </ul>
     *
     * @return a map from a variable name to a path to a question node
     */
    public Map<String, String> getExtraProperties()
    {
        return new HashMap<>(this.properties);
    }

    /**
     * A set of extra attachments to include inline in the email. Usually, these are images displayed inline with
     * {@code src="cid:imageName.png"} in the HTML body. Each attachments has three parts: the name of the attachment,
     * which is how the attachment can be referenced with the {@code cid:} syntax; the MIME type of the attachment; and
     * the actual content as a byte array.
     *
     * @return a list of triples [name, MIME type, content]
     */
    public List<Triple<String, String, byte[]>> getInlineAttachments()
    {
        return new LinkedList<>(this.inlineAttachments);
    }

    /**
     * The template for the HTML part of the email body. The template can contain placeholders for variables to be
     * filled in for a specific email instance, using the ${variable} syntax. Such variables can be set in
     * {@link #getExtraProperties()} or passed in {@link #getEmailBuilderForSubject(Node, Map, FormUtils)}.
     *
     * @return a large string, may be {@code null} if no HTML body is to be used
     */
    public String getHtmlTemplate()
    {
        return this.htmlTemplate;
    }

    /**
     * The template for the plain text part of the email body. The template can contain placeholders for variables to be
     * filled in for a specific email instance, using the ${variable} syntax. Such variables can be set in
     * {@link #getExtraProperties()} or passed in {@link #getEmailBuilderForSubject(Node, Map, FormUtils)}.
     *
     * @return a large string, may be {@code null} if no plain text body is to be used
     */
    public String getTextTemplate()
    {
        return this.textTemplate;
    }

    /**
     * Start instantiating the template into an actual email by obtaining an email builder based on this template. No
     * extra processing of the template is done, the body templates must be manually
     * {@link EmailUtils#renderEmailTemplate(String, Map) interpolated} and
     * {@link Email.Builder#withBody(String, String) passed into the email builder}.
     *
     * @return an email builder based on this template
     */
    public Email.Builder getEmailBuilder()
    {
        return new Email.Builder(this);
    }

    /**
     * Start instantiating the template into an actual email for a specific subject by obtaining an email builder based
     * on this template, with the body templates already interpolated with answers to the referenced questions for the
     * targeted subject and the additional properties passed to this method. A specific recipient name and email address
     * {@link Email.Builder#withRecipient(String, String) must be set} before the email can be built.
     *
     * @param subject a subject node whose answers will be used in the body template
     * @param extraProperties additional properties to interpolate in the body template, a map from variable name to
     *            actual value
     * @param formUtils utilities for working with forms
     * @return an email builder based on this template, interpolated for the target subject
     */
    public Email.Builder getEmailBuilderForSubject(Node subject, Map<String, String> extraProperties,
        FormUtils formUtils)
    {
        final Email.Builder builder = getEmailBuilder();
        final Map<String, String> actualProperties = new HashMap<>(extraProperties);
        this.properties.forEach((name, path) -> {
            try {
                final Session session = subject.getSession();
                if (!path.startsWith("/")) {
                    actualProperties.put(name, path);
                } else if (session.nodeExists(path)) {
                    final Node questionNode = session.getNode(path);
                    final Collection<Node> answers =
                        formUtils.findAllSubjectRelatedAnswers(subject, questionNode,
                            EnumSet.allOf(FormUtils.SearchType.class));
                    if (!answers.isEmpty()) {
                        Object answer = formUtils.getValue(answers.iterator().next());
                        if (answer instanceof Calendar) {
                            final DateFormat sdf = DateFormat.getDateInstance();
                            sdf.setTimeZone(((Calendar) answer).getTimeZone());
                            actualProperties.put(name, sdf.format(((Calendar) answer).getTime()));
                        } else {
                            actualProperties.put(name, answer.toString());
                        }
                    }
                } else if (session.propertyExists(path)) {
                    actualProperties.put(name, session.getProperty(path).getString());
                }
            } catch (RepositoryException e) {
                //
            }
        });

        builder.withBody(
            EmailUtils.renderEmailTemplate(this.htmlTemplate, actualProperties),
            EmailUtils.renderEmailTemplate(this.textTemplate, actualProperties));
        return builder;
    }

    /**
     * Start building a template from scratch.
     *
     * @return a new template builder
     */
    public static final Builder builder()
    {
        return new Builder();
    }

    /**
     * Start building a template based on the stored template node. If the template node is fully configured, the
     * template can be directly {@link Builder#build() built} after this, but, if needed, other properties or
     * attachments may be manually added before finalizing the template.
     *
     * @param template a JCR node of type {@code cards:EmailTemplate}
     * @param resolver a resource resolver, needed for determining the MIME type of attachments
     * @return a template builder already set up with the data from the node
     * @throws RepositoryException if accessing the repository fails
     * @throws IOException if reading attachments fails
     */
    public static final Builder builder(final Node template, final ResourceResolver resolver)
        throws RepositoryException, IOException
    {
        return new Builder(template, resolver);
    }

    /**
     * A builder for {@link EmailTemplate}. New instances can be obtained by calling {@link EmailTemplate#builder()} or
     * {@link EmailTemplate#builder(Node, ResourceResolver)}.
     *
     * @version $Id$
     */
    public static final class Builder
    {
        private final EmailTemplate instance;

        private Builder()
        {
            this.instance = new EmailTemplate();
        }

        private Builder(final Node template, final ResourceResolver resolver) throws RepositoryException, IOException
        {
            this();
            readTemplateProperties(template);
            readCommonAttachments(resolver);
            readTemplateAttachments(template, resolver);
        }

        /**
         * Set a HTML body template for the email template. Optional, a template can have only a text body, or an actual
         * email body may be set directly on the email builder.
         *
         * @param body a large string
         * @return same builder instance for method chaining
         */
        public Builder withHtmlTemplate(String body)
        {
            this.instance.htmlTemplate = body;
            return this;
        }

        /**
         * Set a plain text body template for the email template. Optional, a template can have only a HTML body, or an
         * actual email body may be set directly on the email builder.
         *
         * @param body a large string
         * @return same builder instance for method chaining
         */
        public Builder withTextTemplate(final String body)
        {
            this.instance.textTemplate = body;
            return this;
        }

        /**
         * Set the sender email address. Mandatory.
         *
         * @param address a valid email address
         * @return same builder instance for method chaining
         */
        public Builder withSenderAddress(final String address)
        {
            this.instance.senderAddress = address;
            return this;
        }

        /**
         * Set the sender's display name. Optional.
         *
         * @param name a display name
         * @return same builder instance for method chaining
         */
        public Builder withSenderName(final String name)
        {
            this.instance.senderName = name;
            return this;
        }

        /**
         * Set the sender email address and display name.
         *
         * @param address a valid email address
         * @param name a display name
         * @return same builder instance for method chaining
         */
        public Builder withSender(final String address, final String name)
        {
            this.instance.senderAddress = address;
            this.instance.senderName = name;
            return this;
        }

        /**
         * Set the email address to reply to. Optional, defaults to the sender address.
         *
         * @param address a valid email address
         * @return same builder instance for method chaining
         */
        public Builder withReplyToAddress(final String address)
        {
            this.instance.replyToAddress = address;
            return this;
        }

        /**
         * Set the display name for the reply-to email. Optional, defaults to the sender name.
         *
         * @param name a display name
         * @return same builder instance for method chaining
         */
        public Builder withReplyToName(final String name)
        {
            this.instance.replyToName = name;
            return this;
        }

        /**
         * Set the reply-to email address and display name.
         *
         * @param address a valid email address
         * @param name a display name
         * @return same builder instance for method chaining
         */
        public Builder withReplyTo(final String address, final String name)
        {
            this.instance.replyToAddress = address;
            this.instance.replyToName = name;
            return this;
        }

        /**
         * Set the email Subject.
         *
         * @param subject a short string
         * @return same builder instance for method chaining
         */
        public Builder withSubject(final String subject)
        {
            this.instance.subject = subject;
            return this;
        }

        /**
         * Add an additional property to be used in the email body when instantiating the template.
         *
         * @param name the name of the variable to be used in the body template
         * @param value a path to a JCR question node, a path to a JCR property, or just a simple string value
         * @return same builder instance for method chaining
         */
        public Builder withProperty(final String name, final String value)
        {
            this.instance.properties.put(name, value);
            return this;
        }

        /**
         * Add an additional inline attachment to be used in the email.
         *
         * @param name the name of the attachment, and the name to be used as the content ID (cid)
         * @param mimeType the MIME type of the attachment
         * @param value the actual body as a byte array
         * @return same builder instance for method chaining
         */
        public Builder withInlineAttachment(final String name, final String mimeType, final byte[] value)
        {
            this.instance.inlineAttachments.add(new ImmutableTriple<>(name, mimeType, value));
            return this;
        }

        /**
         * Retrieve the built {@link EmailTemplate} instance. The builder should be discarded after this.
         *
         * @return an {@link EmailTemplate} instance
         * @throws IllegalStateException if either the sender address or subject are not set
         */
        public EmailTemplate build() throws IllegalStateException
        {
            if (this.instance.senderAddress == null || this.instance.subject == null) {
                throw new IllegalStateException("Email template isn't ready yet");
            }
            return this.instance;
        }

        private void readTemplateProperties(final Node template)
            throws RepositoryException
        {
            PropertyIterator properties = template.getProperties();
            while (properties.hasNext()) {
                Property property = properties.nextProperty();
                String name = property.getName();
                if (name.startsWith("jcr:") || name.startsWith("sling:") || property.isMultiple()) {
                    continue;
                }
                String value = property.getValue().getString();
                switch (name) {
                    case EmailTemplate.SENDER_ADDRESS_PROPERTY:
                        withSenderAddress(value);
                        break;
                    case EmailTemplate.SENDER_NAME_PROPERTY:
                        withSenderName(value);
                        break;
                    case EmailTemplate.REPLY_TO_ADDRESS_PROPERTY:
                        withReplyToAddress(value);
                        break;
                    case EmailTemplate.REPLY_TO_NAME_PROPERTY:
                        withReplyToName(value);
                        break;
                    case EmailTemplate.SUBJECT_PROPERTY:
                        withSubject(value);
                        break;
                    default:
                        withProperty(name, property.getString());
                }
            }
        }

        private void readCommonAttachments(final ResourceResolver resolver)
            throws RepositoryException, IOException
        {
            final Resource commonAttachmentsResource = resolver.getResource(COMMON_ATTACHMENTS_NODE);
            if (commonAttachmentsResource == null) {
                return;
            }
            final NodeIterator children = commonAttachmentsResource.adaptTo(Node.class).getNodes();
            while (children.hasNext()) {
                Node child = children.nextNode();
                if (child.isNodeType("nt:file")) {
                    withInlineAttachment(child.getName(), getMimeType(child, resolver), readFileAsBytes(child));
                }
            }
        }

        private void readTemplateAttachments(final Node template, final ResourceResolver resolver)
            throws RepositoryException, IOException
        {
            final Session session = template.getSession();
            String htmlBodyHeader = readFileAsString(session.getNode(COMMON_HTML_BODY_HEADER));
            String htmlBody = "";
            String htmlBodyFooter = readFileAsString(session.getNode(COMMON_HTML_BODY_FOOTER));
            String textBodyHeader = readFileAsString(session.getNode(COMMON_TEXT_BODY_HEADER));
            String textBody = "";
            String textBodyFooter = readFileAsString(session.getNode(COMMON_TEXT_BODY_FOOTER));
            NodeIterator children = template.getNodes();
            while (children.hasNext()) {
                Node child = children.nextNode();
                if (EmailTemplate.HTML_BODY_HEADER.equals(child.getName())) {
                    htmlBodyHeader = readFileAsString(child);
                } else if (EmailTemplate.HTML_TEMPLATE_NODE.equals(child.getName())) {
                    htmlBody = readFileAsString(child);
                } else if (EmailTemplate.HTML_BODY_FOOTER.equals(child.getName())) {
                    htmlBodyFooter = readFileAsString(child);
                } else if (EmailTemplate.TEXT_BODY_HEADER.equals(child.getName())) {
                    textBodyHeader = readFileAsString(child);
                } else if (EmailTemplate.TEXT_TEMPLATE_NODE.equals(child.getName())) {
                    textBody = readFileAsString(child);
                } else if (EmailTemplate.TEXT_BODY_FOOTER.equals(child.getName())) {
                    textBodyFooter = readFileAsString(child);
                } else if (child.isNodeType("nt:file")) {
                    withInlineAttachment(child.getName(), getMimeType(child, resolver), readFileAsBytes(child));
                }
            }
            withHtmlTemplate(htmlBodyHeader + htmlBody + htmlBodyFooter);
            withTextTemplate(textBodyHeader + textBody + textBodyFooter);
        }

        private String readFileAsString(final Node node) throws IOException, RepositoryException
        {
            try {
                return IOUtils.toString(getFileStream(node), StandardCharsets.UTF_8);
            } catch (RepositoryException e) {
                return "";
            }
        }

        private byte[] readFileAsBytes(final Node node) throws IOException, RepositoryException
        {
            return getFileStream(node).readAllBytes();
        }

        private InputStream getFileStream(final Node node) throws RepositoryException
        {
            return node.getNode("jcr:content").getProperty("jcr:data").getBinary().getStream();
        }

        private String getMimeType(final Node node, final ResourceResolver resolver)
        {
            try {
                return resolver.getResource(node.getPath()).getResourceMetadata().getContentType();
            } catch (Exception e) {
                return "application/octet-stream";
            }
        }
    }
}
