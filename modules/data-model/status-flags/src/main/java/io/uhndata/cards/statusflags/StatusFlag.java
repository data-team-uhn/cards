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
package io.uhndata.cards.statusflags;

/**
 * Describes a status flag that may be set on a data node.
 *
 * @version $Id$
 */
public interface StatusFlag
{
    /**
     * The name of the flag itself, the actual value stored in the node.
     *
     * @return a short string
     */
    String getName();

    /**
     * Whether or not the flag bubbles up from the node where it is set to the root data node, e.g. an INCOMPLETE flag
     * on an answer will also propagate up and mark the whole form as INCOMPLETE.
     *
     * @return {@code true} if the flag should be automatically copied upwards to the root data node, {@code false} if
     *         it is only manually set on specific nodes
     */
    boolean bubbles();

    /**
     * The desired visibility of the flag. The value is a bitmap. {@code 0} means that the flag is hidden in the UI,
     * {@code 1} means that it is displayed on the node's own page, {@code 2} means that it is displayed in aggregated
     * charts, {@code 4} means that it is displayed in tables, {@code 8} means that it is displayed to the patients as
     * well.
     *
     * @return a number
     */
    int getVisibility();

    /**
     * The intended effect of the flag, mainly affecting the color and boldness used to display it in the UI. For
     * example: {@code success}, {@code warning}, {@code error}, {@code info}, {@code neutral}.
     *
     * @return a short string
     */
    String getEffect();
}
