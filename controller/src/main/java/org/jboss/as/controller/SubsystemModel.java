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

/**
 * Defines a subsystem management model.
 * @author Paul Ferraro
 */
public interface SubsystemModel {
    /**
     * Returns the version of this subsystem model.
     * @return a model version
     */
    ModelVersion getVersion();

    /**
     * Indicates whether the version of this subsystem model is more recent than the specified version and thus requires transformation
     * @param version a model version
     * @return true if the version of this subsystem model is more recent than the specified version, false otherwise
     */
    default boolean requiresTransformation(ModelVersion version) {
        return this.getVersion().compareTo(version) < 0;
    }
}
