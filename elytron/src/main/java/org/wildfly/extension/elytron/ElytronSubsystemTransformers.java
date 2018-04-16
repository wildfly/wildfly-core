/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;

/**
 * Registers transformers for the elytron subsystem.
 *
 * @author Brian Stansberry
 */
public final class ElytronSubsystemTransformers implements ExtensionTransformerRegistration {
    private static final ModelVersion ELYTRON_1_2_0 = ModelVersion.create(1, 2);
    private static final ModelVersion ELYTRON_2_0_0 = ModelVersion.create(2, 0);
    private static final ModelVersion ELYTRON_3_0_0 = ModelVersion.create(3, 0);

    @Override
    public String getSubsystemName() {
        return ElytronExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());

        ResourceTransformationDescriptionBuilder builderCurrentTo2_0_0 = chainedBuilder.createBuilder(ELYTRON_3_0_0, ELYTRON_2_0_0);
        builderCurrentTo2_0_0.discardChildResource(PathElement.pathElement(ElytronDescriptionConstants.PERMISSION_SET));
        builderCurrentTo2_0_0
                .addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER))
                .getAttributeBuilder()
                .setValueConverter(MAPPING_PERMISSION_SET_CONVERTER, ElytronDescriptionConstants.PERMISSION_MAPPINGS)
                .end();
        builderCurrentTo2_0_0
                .addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CONSTANT_PERMISSION_MAPPER))
                .getAttributeBuilder()
                .addRename(ElytronDescriptionConstants.PERMISSION_SETS, ElytronDescriptionConstants.PERMISSIONS)
                .setValueConverter(CONSTANT_PERMISSION_SET_CONVERTER, ElytronDescriptionConstants.PERMISSION_SETS)
                .end();

        // 2.0.0 to 1.2.0, aka EAP 7.1.0
        chainedBuilder.createBuilder(ELYTRON_2_0_0, ELYTRON_1_2_0);
        chainedBuilder.buildAndRegister(registration, new ModelVersion[] { ELYTRON_2_0_0, ELYTRON_1_2_0 });

    }

    private static final AttributeConverter MAPPING_PERMISSION_SET_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                for (ModelNode permissionMapping : attributeValue.asList()) {
                    if (permissionMapping.hasDefined(ElytronDescriptionConstants.PERMISSION_SETS)) {
                        ModelNode permissionSets = permissionMapping.get(ElytronDescriptionConstants.PERMISSION_SETS);
                        for (ModelNode permissionSet : permissionSets.asList()) {
                            ModelNode permissionSetName = permissionSet.get(ElytronDescriptionConstants.PERMISSION_SET);
                            PathAddress permissionSetAddress = address.getParent().append(ElytronDescriptionConstants.PERMISSION_SET, permissionSetName.asString());
                            ModelNode permissions = context.readResourceFromRoot(permissionSetAddress).getModel().get(ElytronDescriptionConstants.PERMISSIONS);
                            for (ModelNode permission: permissions.asList()) {
                                permissionMapping.get(ElytronDescriptionConstants.PERMISSIONS).add(permission);
                            }
                        }
                        permissionMapping.remove(ElytronDescriptionConstants.PERMISSION_SETS);
                    }
                }
            }
        }
    };

    private static final AttributeConverter CONSTANT_PERMISSION_SET_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                ModelNode allPermissions = new ModelNode();
                for (ModelNode permissionSet : attributeValue.asList()) {
                    ModelNode permissionSetName = permissionSet.get(ElytronDescriptionConstants.PERMISSION_SET);
                    PathAddress permissionSetAddress = address.getParent().append(ElytronDescriptionConstants.PERMISSION_SET, permissionSetName.asString());
                    ModelNode permissions = context.readResourceFromRoot(permissionSetAddress).getModel().get(ElytronDescriptionConstants.PERMISSIONS);
                    for (ModelNode permission: permissions.asList()) {
                        allPermissions.add(permission);
                    }
                }
                attributeValue.set(allPermissions);
            }
        }
    };

}
