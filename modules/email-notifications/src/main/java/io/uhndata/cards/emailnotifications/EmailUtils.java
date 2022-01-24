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

import org.apache.sling.commons.messaging.mail.MailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

public final class EmailUtils
{
    private static final String FROM_ADDRESS = System.getenv("PATIENT_NOTIFICATION_FROM_ADDRESS");
    private static final String FROM_NAME = System.getenv("PATIENT_NOTIFICATION_FROM_NAME");
    private static final String SUBJECT = System.getenv("PATIENT_NOTIFICATION_SUBJECT");

    // Hide the utility class constructor
    private EmailUtils()
    {
    }

    public static void sendNotificationEmail(MailService mailService, String toAddress,
        String toName, String emailBody) throws MessagingException
    {
        MimeMessage message = mailService.getMessageBuilder()
            .from(FROM_ADDRESS, FROM_NAME)
            .to(toAddress, toName)
            .replyTo(FROM_ADDRESS)
            .subject(SUBJECT)
            .text(emailBody)
            .build();

        mailService.sendMessage(message);
    }
}
