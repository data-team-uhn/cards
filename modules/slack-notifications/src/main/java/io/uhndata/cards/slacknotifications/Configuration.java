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

package io.uhndata.cards.slacknotifications;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Scheduled Slack Notifications",
    description = "Configuration for sending regular slack notifications.")
public @interface Configuration
{
    @AttributeDefinition(name = "Name")
    String name();

    @AttributeDefinition(name = "Schedule",
        description = "A Quartz-readable schedule expression determining when the notification job runs, for example "
            + "'0 0 0 * * ? *' for a nightly notification, or '0 0 9 ? * MON *' for a weekly Monday morning message.")
    String schedule() default "%ENV%SLACK_NOTIFICATIONS_SCHEDULE";

    @AttributeDefinition(name = "Endpoint",
        description = "A webhook endpoint that will receive the message.")
    String endpoint() default "%ENV%SLACK_NOTIFICATIONS_ENDPOINT";

    @AttributeDefinition(name = "Message title",
        description = "An optional title to include in the message.")
    String title() default "";

    @AttributeDefinition(name = "Include notifications",
        description = "Customize which notifications to include in the message. Leave empty to include all.")
    String[] include();

    @AttributeDefinition(name = "Extra parameters",
        description = "Optional extra parameters to pass to the notification producers."
            + " The expected values depend on each notification producer, but they must be in the key=value format.")
    String[] notificationParameters();
}
