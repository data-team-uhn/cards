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
package io.uhndata.cards.locking;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionPattern;

/**
 * A restriction that makes locked nodes and forms not editable.
 *
 * @version $Id$
 */
public class LockRestrictionPattern implements RestrictionPattern
{
    // private final ResourceResolverFactory rrf;

    /**
     *
     */
    public LockRestrictionPattern()
    {

    }

    @Override
    public boolean matches()
    {
        // Not a repository wide restriction
        return false;
    }

    @Override
    public boolean matches(final String path)
    {
        return path.endsWith("/lock") && (path.startsWith("/Subjects") || path.startsWith("/Forms"));
    }
    @Override
    public boolean matches(final String path, final boolean isProperty)
    {
        return matches(path);
    }

    @Override
    public boolean matches(final Tree tree, final PropertyState property)
    {
        return matches(tree.getPath());
    }
}
