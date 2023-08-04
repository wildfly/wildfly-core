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

import java.util.List;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.xml.IntVersionSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLElementSchema;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;

/**
 * Defines a versioned schema for a subsystem.
 * @author Paul Ferraro
 * @param <S> the schema type
 */
public interface SubsystemSchema<S extends SubsystemSchema<S>> extends XMLElementSchema<S, List<ModelNode>> {

    @Override
    default String getLocalName() {
        return ModelDescriptionConstants.SUBSYSTEM;
    }

    /**
     * Creates a subsystem URN of the form
     * {@value XMLElementSchema.WILDFLY_IDENTIFIER}:{@code subsystemName}:{@link IntVersion#major()}.{@link IntVersion#minor()}
     * for the specified subsystem name and version.
     * @param <S> the schema type
     * @param subsystemName the subsystem name
     * @param version the schema version
     * @return a versioned namespace
     */
    static <S extends SubsystemSchema<S>> VersionedNamespace<IntVersion, S> createSubsystemURN(String subsystemName, IntVersion version) {
        return IntVersionSchema.createURN(List.of(IntVersionSchema.WILDFLY_IDENTIFIER, subsystemName), version);
    }

    /**
     * Creates a subsystem URN of the form
     * {@value XMLElementSchema.JBOSS_IDENTIFIER}:{@value ModelDescriptionConstants.DOMAIN}:{@code subsystemName}:{@link IntVersion#major()}.{@link IntVersion#minor()}
     * for the specified subsystem name and version.
     * @param <S> the schema type
     * @param subsystemName the subsystem name
     * @param version the schema version
     * @return a versioned namespace
     */
    static <S extends SubsystemSchema<S>> VersionedNamespace<IntVersion, S> createLegacySubsystemURN(String subsystemName, IntVersion version) {
        return IntVersionSchema.createURN(List.of(IntVersionSchema.JBOSS_IDENTIFIER, ModelDescriptionConstants.DOMAIN, subsystemName), version);
    }
}
