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

import java.util.Collections;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.apache.sling.commons.messaging.mail.MailService;
import org.apache.sling.commons.messaging.mail.MessageBuilder;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;

public final class EmailUtils
{
    // Hide the utility class constructor
    private EmailUtils()
    {
    }

    /**
     * Generates an email by completing a template with substituted strings.
     *
     * @param emailTemplate the template for the body of the email
     * @param valuesMap the list of strings to be substituted
     * @return the body of the email ready to be sent
     */
    public static String renderEmailTemplate(String emailTemplate, Map<String, String> valuesMap)
    {
        StringSubstitutor sub = new StringSubstitutor(valuesMap);
        return sub.replace(emailTemplate);
    }

    /**
     * Sends a simple plaintext email.
     *
     * @param email the email to send
     * @param mailService the MailService object which sends the email
     * @throws MessagingException if sending the email fails
     */
    public static void sendTextEmail(final Email email, final MailService mailService)
        throws MessagingException
    {
        final MessageBuilder message = mailService.getMessageBuilder()
            .from(email.getSenderAddress(), email.getSenderName())
            .to(email.getRecipientAddress(), email.getRecipientName())
            .replyTo(email.getReplyToAddress(), email.getReplyToName())
            .subject(email.getSubject())
            .text(email.getTextBody());
        for (Map.Entry<String, String> header : email.getExtraHeaders().entrySet()) {
            message.header(header.getKey(), header.getValue());
        }

        mailService.sendMessage(message.build());
    }

    /**
     * Sends a rich-text HTML email with fallback to plaintext for legacy mail clients.
     *
     * @param email the email to send
     * @param mailService the MailService object which sends the email
     * @throws MessagingException if sending the email fails
     */
    public static void sendHtmlEmail(final Email email, final MailService mailService)
        throws MessagingException
    {
        final MessageBuilder message = mailService.getMessageBuilder()
            .from(email.getSenderAddress(), email.getSenderName())
            .to(email.getRecipientAddress(), email.getRecipientName())
            .replyTo(email.getReplyToAddress(), email.getReplyToName())
            .subject(email.getSubject())
            .text(email.getTextBody())
            .html(email.getHtmlBody());
        for (Map.Entry<String, String> header : email.getExtraHeaders().entrySet()) {
            message.header(header.getKey(), header.getValue());
        }
        email.getInlineAttachments()
            .forEach(attachment -> message.inline(attachment.getRight(), attachment.getMiddle(), attachment.getLeft(),
                Collections.singleton(
                    new Header("Content-Disposition", "inline; filename=\"" + attachment.getLeft() + "\""))));

        mailService.sendMessage(message.build());
    }
}
