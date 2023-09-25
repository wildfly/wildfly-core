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

package org.jboss.as.remoting;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;

/**
 * Enumerates the supported model versions of the remoting subsystem.
 */
public enum RemotingSubsystemModel implements SubsystemModel {
    VERSION_4_0_0(4, 0, 0), // WildFly 11, EAP 7.1
    VERSION_5_0_0(5, 0, 0), // WildFly 12 - 26, EAP 7.2 - 7.4
    VERSION_6_0_0(6, 0, 0), // WildFly 27 - present, EAP 8.0
    VERSION_7_0_0(6, 0, 0), // WildFly 30
    ;
    static final RemotingSubsystemModel CURRENT = VERSION_7_0_0;

    private final ModelVersion version;

    RemotingSubsystemModel(int major, int minor, int micro) {
        this.version = ModelVersion.create(major, minor, micro);
    }

    @Override
    public ModelVersion getVersion() {
        return this.version;
    }
}
