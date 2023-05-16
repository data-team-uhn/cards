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

/**
 * A read-only email ready to be sent. Emails are built from {@link EmailTemplate templates}, so the first step for
 * creating an email is to build an email template, for example
 * {@link #builder(javax.jcr.Node, org.apache.sling.api.resource.ResourceResolver) by reading a template node from the
 * repository}. Then, the template can be
 * {@link #getEmailBuilderForSubject(javax.jcr.Node, java.util.Map, io.uhndata.cards.forms.api.FormUtils) instantiated
 * for a specific subject}. Finally, it can be sent with the help of
 * {@link EmailUtils#sendHtmlEmail(Email, org.apache.sling.commons.messaging.mail.MailService)}.
 *
 * @see EmailTemplate
 * @version $Id$
 */
public class Email extends EmailTemplate
{
    private String toAddress;

    private String toName;

    private String htmlBody;

    private String textBody;

    protected Email(final EmailTemplate template)
    {
        super(template);
    }

    /**
     * The recipient email address, part of the {@code To} email header together with {@link #getRecipientName()}.
     *
     * @return a valid email address
     */
    public String getRecipientAddress()
    {
        return this.toAddress;
    }

    /**
     * The recipient display name, part of the {@code To} email header together with {@link #getRecipientAddress()}.
     *
     * @return a display name
     */
    public String getRecipientName()
    {
        return this.toName;
    }

    /**
     * The HTML body of the email.
     *
     * @return a large string, may be {@code null} if no HTML body is to be used
     */
    public String getHtmlBody()
    {
        return this.htmlBody;
    }

    /**
     * The plain text body of the email.
     *
     * @return a large string, may be {@code null} if no plain text fallback body is to be used
     */
    public String getTextBody()
    {
        return this.textBody;
    }

    /**
     * A builder for {@link Email}. New instances can be obtained from {@link EmailTemplate#getEmailBuilder()} or
     * {@link EmailTemplate#getEmailBuilderForSubject(javax.jcr.Node, java.util.Map, io.uhndata.cards.forms.api.FormUtils)}.
     *
     * @version $Id$
     */
    public static final class Builder
    {
        private final Email instance;

        protected Builder(final EmailTemplate template)
        {
            this.instance = new Email(template);
        }

        /**
         * Set specific email bodies for this email instance. When using
         * {@link EmailTemplate#getEmailBuilderForSubject(javax.jcr.Node, java.util.Map, io.uhndata.cards.forms.api.FormUtils)},
         * these are already set. Otherwise, {@link EmailUtils#renderEmailTemplate(String, java.util.Map)} can be used
         * to interpolate the template body with a set of variables. It is mandatory to set at least one part.
         *
         * @param htmlBody the HTML part of the body to use, a large string, may be {@code null} if no HTML part is to
         *            be used
         * @param textBody the plain text part of the body to use, a large string, may be {@code null} if no text
         *            fallback part is to be used
         * @return same builder instance for method chaining
         */
        public Builder withBody(String htmlBody, String textBody)
        {
            this.instance.htmlBody = htmlBody;
            this.instance.textBody = textBody;
            return this;
        }

        /**
         * Set a specific recipient for this email instance. It is mandatory to set at least the email address.
         *
         * @param address a valid email address
         * @param name an optional display name for the recipient
         * @return same builder instance for method chaining
         */
        public Builder withRecipient(final String address, final String name)
        {
            this.instance.toAddress = address;
            this.instance.toName = name;
            return this;
        }

        /**
         * Retrieve the built {@link Email} instance. The builder should be discarded after this.
         *
         * @return an {@link Email} instance
         * @throws IllegalStateException if both body parts are not set, or the recipient email address is not set
         */
        public Email build() throws IllegalStateException
        {
            if (this.instance.htmlBody == null && this.instance.textBody == null || this.instance.toAddress == null) {
                // We don't check the name, it may be missing
                throw new IllegalStateException("Email isn't ready yet");
            }
            return this.instance;
        }
    }
}
