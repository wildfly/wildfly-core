/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller;

import org.jboss.as.controller.xml.VersionedURN;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.Versioned;

/**
 * A versioned subsystem namespace identified by a URN constructed using a version.
 * @author Paul Ferraro
 */
public class SubsystemURN<N extends Versioned<IntVersion, N>> extends VersionedURN<N> {

    /**
     * Constructs a URN-based versioned namespace using the default subsystem namespace identifier and the specified subsystem name and version.
     * @param subsystemName the subsystem name
     * @param version the schema version
     */
    public SubsystemURN(String subsystemName, IntVersion version) {
        super(WILDFLY_IDENTIFIER, subsystemName, version);
    }
}
