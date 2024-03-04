/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wildfly.extension.core.management;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;

import static org.wildfly.extension.core.management.CoreManagementExtension.CURRENT_VERSION;
import static org.wildfly.extension.core.management.CoreManagementExtension.VERSION_1_0_0;

public class CoreManagementTransformerRegistration implements ExtensionTransformerRegistration {
    @Override
    public String getSubsystemName() {
        return CoreManagementExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration subsystemRegistration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(CURRENT_VERSION);

        // 2.0.0 to 1.0.0
        chainedBuilder.createBuilder(CURRENT_VERSION, VERSION_1_0_0)
                .rejectChildResource(UnstableApiAnnotationResourceDefinition.PATH);

        chainedBuilder.buildAndRegister(subsystemRegistration, new ModelVersion[] {VERSION_1_0_0});
    }
}
