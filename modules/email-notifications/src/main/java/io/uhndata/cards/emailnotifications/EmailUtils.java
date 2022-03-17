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

import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.apache.sling.commons.messaging.mail.MailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

public final class EmailUtils
{
    private static final String FROM_ADDRESS = System.getenv("PATIENT_NOTIFICATION_FROM_ADDRESS");
    private static final String FROM_NAME = System.getenv("PATIENT_NOTIFICATION_FROM_NAME");

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
     * @param mailService the MailService object which sends the email
     * @param toAddress the destination email address
     * @param toName the name of the email recipient
     * @param emailSubject the subject line of the email
     * @param emailTextBody the plaintext body of the email
     */
    public static void sendNotificationEmail(MailService mailService, String toAddress,
        String toName, String emailSubject, String emailTextBody) throws MessagingException
    {
        MimeMessage message = mailService.getMessageBuilder()
            .from(FROM_ADDRESS, FROM_NAME)
            .to(toAddress, toName)
            .replyTo(FROM_ADDRESS)
            .subject(emailSubject)
            .text(emailTextBody)
            .build();

        mailService.sendMessage(message);
    }

    /**
     * Sends a rich-text HTML email with fallback to plaintext for legacy mail clients.
     *
     * @param mailService the MailService object which sends the email
     * @param toAddress the destination email address
     * @param toName the name of the email recipient
     * @param emailSubject the subject line of the email
     * @param emailTextBody the plaintext body of the email
     * @param emailHtmlBody the rich-text HTML body of the email
     */
    public static void sendNotificationHtmlEmail(MailService mailService, String toAddress,
        String toName, String emailSubject, String emailTextBody, String emailHtmlBody) throws MessagingException
    {
        MimeMessage message = mailService.getMessageBuilder()
            .from(FROM_ADDRESS, FROM_NAME)
            .to(toAddress, toName)
            .replyTo(FROM_ADDRESS)
            .subject(emailSubject)
            .text(emailTextBody)
            .html(emailHtmlBody)
            .build();

        mailService.sendMessage(message);
    }
}
