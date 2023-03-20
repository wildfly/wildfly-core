/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.bridge.impl;

import java.util.List;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.as.model.test.ModelTestParser;
import org.jboss.as.subsystem.test.AbstractKernelServicesImpl;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.TestParser;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ChildFirstClassLoaderKernelServicesFactory {

    public static KernelServices create(String mainSubsystemName, String extensionClassName, AdditionalInitialization additionalInit, ModelTestOperationValidatorFilter validateOpsFilter,
            List<ModelNode> bootOperations, ModelVersion legacyModelVersion, boolean persistXml) throws Exception {
        Extension extension = (Extension) Class.forName(extensionClassName).newInstance();

        //TODO this should get serialized properly
        if (additionalInit == null) {
            additionalInit = AdditionalInitialization.MANAGEMENT;
        }

        ExtensionRegistry extensionRegistry = new ExtensionRegistry(ProcessType.DOMAIN_SERVER, new RunningModeControl(RunningMode.ADMIN_ONLY));
        ModelTestParser testParser = new TestParser(mainSubsystemName, extensionRegistry);
        return AbstractKernelServicesImpl.create(null, mainSubsystemName, additionalInit, validateOpsFilter,
                extensionRegistry, bootOperations, testParser, extension, legacyModelVersion, false, persistXml);
    }
}
