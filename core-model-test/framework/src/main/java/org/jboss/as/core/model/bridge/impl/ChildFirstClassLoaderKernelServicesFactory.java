/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.bridge.impl;

import java.util.List;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.model.test.AbstractKernelServicesImpl;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.LegacyModelInitializerEntry;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.TestParser;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.host.controller.RestartMode;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ChildFirstClassLoaderKernelServicesFactory {

    public static KernelServices create(List<ModelNode> bootOperations, ModelTestOperationValidatorFilter validateOpsFilter, ModelVersion legacyModelVersion,
            List<LegacyModelInitializerEntry> modelInitializerEntries) throws Exception {

        TestModelType type = TestModelType.DOMAIN;
        XMLMapper xmlMapper = XMLMapper.Factory.create();
        TestParser testParser = TestParser.create(null, xmlMapper, type);
        ModelInitializer modelInitializer = null;
        if (modelInitializerEntries != null && !modelInitializerEntries.isEmpty()) {
            modelInitializer = new LegacyModelInitializer(modelInitializerEntries);
        }

        RunningModeControl runningModeControl = new HostRunningModeControl(RunningMode.ADMIN_ONLY, RestartMode.HC_ONLY);
        ExtensionRegistry extensionRegistry = new ExtensionRegistry(ProcessType.HOST_CONTROLLER, runningModeControl);
        return AbstractKernelServicesImpl.create(ProcessType.HOST_CONTROLLER, runningModeControl, validateOpsFilter, bootOperations, testParser, legacyModelVersion, type, modelInitializer, extensionRegistry, null);
    }

    public static KernelServices create(List<ModelNode> bootOperations, ModelTestOperationValidatorFilter validateOpsFilter, ModelVersion legacyModelVersion,
                                        List<LegacyModelInitializerEntry> modelInitializerEntries, String stabilityStr) throws Exception {

        Stability stability = Stability.fromString(stabilityStr);
        TestModelType type = TestModelType.DOMAIN;
        XMLMapper xmlMapper = XMLMapper.Factory.create();
        TestParser testParser = TestParser.create(stability, null, xmlMapper, type);
        ModelInitializer modelInitializer = null;
        if (modelInitializerEntries != null && !modelInitializerEntries.isEmpty()) {
            modelInitializer = new LegacyModelInitializer(modelInitializerEntries);
        }

        RunningModeControl runningModeControl = new HostRunningModeControl(RunningMode.ADMIN_ONLY, RestartMode.HC_ONLY);

        ExtensionRegistry extensionRegistry = ExtensionRegistry.builder(ProcessType.HOST_CONTROLLER)
                .withRunningMode(runningModeControl.getRunningMode())
                .withStability(stability)
                .build();

        return AbstractKernelServicesImpl.create(ProcessType.HOST_CONTROLLER, runningModeControl, validateOpsFilter, bootOperations, testParser, legacyModelVersion, type, modelInitializer, extensionRegistry, null);
    }

    private static class LegacyModelInitializer implements ModelInitializer {

        private final List<LegacyModelInitializerEntry> entries;

        LegacyModelInitializer(List<LegacyModelInitializerEntry> entries) {
            this.entries = entries;
        }

        @Override
        public void populateModel(ManagementModel managementModel) {
            populateModel(managementModel.getRootResource());
            for (LegacyModelInitializerEntry entry : entries) {
                if (entry.getCapabilities() != null) {
                    PathAddress parent = entry.getParentAddress();
                    if (parent == null) {
                        parent = PathAddress.EMPTY_ADDRESS;
                    }
                    PathAddress pa = parent.append(entry.getRelativeResourceAddress());
                    CapabilityScope scope = CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER, pa);
                    RuntimeCapabilityRegistry cr = managementModel.getCapabilityRegistry();

                    for (String capabilityName : entry.getCapabilities()) {
                        RuntimeCapability<Void> capability =
                                RuntimeCapability.Builder.of(capabilityName).build();
                        RuntimeCapabilityRegistration reg = new RuntimeCapabilityRegistration(capability, scope,
                                new RegistrationPoint(pa, null));
                        cr.registerCapability(reg);
                    }
                }
            }
        }

        @Override
        public void populateModel(Resource rootResource) {
            for (LegacyModelInitializerEntry entry : entries) {
                Resource parent = rootResource;
                if (entry.getParentAddress() != null && entry.getParentAddress().size() > 0) {
                    for (PathElement element : entry.getParentAddress()) {
                        parent = rootResource.getChild(element);
                        if (parent == null) {
                            throw new IllegalStateException("No parent at " + element);
                        }
                    }
                }
                Resource resource = Resource.Factory.create();
                if (entry.getModel() != null) {
                    resource.getModel().set(entry.getModel());
                }
                parent.registerChild(entry.getRelativeResourceAddress(), resource);
            }
        }

    }
}
