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
package io.uhndata.cards.statusflags.internal;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import io.uhndata.cards.statusflags.StatusFlag;

/**
 * @version $Id$
 */
public class StatusFlagImpl implements StatusFlag
{
    private final String name;

    private final boolean bubbles;

    private final int visibility;

    private final String effect;

    public StatusFlagImpl(final Node definition) throws RepositoryException
    {
        this.name = definition.getName();
        this.bubbles = definition.getProperty("bubbles").getBoolean();
        this.visibility = (int) definition.getProperty("visibility").getLong();
        this.effect = definition.getProperty("effect").getString();
    }

    @Override
    public String getName()
    {
        return this.name;
    }

    @Override
    public boolean bubbles()
    {
        return this.bubbles;
    }

    @Override
    public int getVisibility()
    {
        return this.visibility;
    }

    @Override
    public String getEffect()
    {
        return this.effect;
    }
}
