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

import java.util.HashMap;
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
     * Generates an email by completing a template with a surveys links.
     *
     * @param emailTemplate the template for the body of the email
     * @param surveysLink the link to the patient's surveys to be completed
     * @return the body of the email ready to be sent
     */
    public static String renderEmailTemplate(String emailTemplate, String surveysLink)
    {
        Map<String, String> valuesMap = new HashMap<String, String>();
        valuesMap.put("surveysLink", surveysLink);
        StringSubstitutor sub = new StringSubstitutor(valuesMap);
        return sub.replace(emailTemplate);
    }

    /**
     * Sends a notification email.
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
}
