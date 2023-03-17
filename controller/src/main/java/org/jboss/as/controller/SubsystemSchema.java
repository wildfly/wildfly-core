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
import org.jboss.as.controller.xml.XMLElementSchema;
import org.jboss.dmr.ModelNode;

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
}
