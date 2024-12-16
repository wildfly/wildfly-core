/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.bridge.local;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.as.subsystem.bridge.impl.ChildFirstClassLoaderKernelServicesFactory;
import org.jboss.as.subsystem.bridge.impl.ClassLoaderObjectConverterImpl;
import org.jboss.as.subsystem.bridge.impl.LegacyControllerKernelServicesProxy;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ScopedKernelServicesBootstrap {
    ClassLoader legacyChildFirstClassLoader;
    ClassLoaderObjectConverter objectConverter;

    public ScopedKernelServicesBootstrap(ClassLoader legacyChildFirstClassLoader) {
        this.legacyChildFirstClassLoader = legacyChildFirstClassLoader;
        this.objectConverter = new ClassLoaderObjectConverterImpl(this.getClass().getClassLoader(), legacyChildFirstClassLoader);
    }


    public LegacyControllerKernelServicesProxy createKernelServices(String mainSubsystemName, String extensionClassName, AdditionalInitialization additionalInit,
            ModelTestOperationValidatorFilter validateOpsFilter, List<ModelNode> bootOperations, ModelVersion legacyModelVersion, boolean persistXml) throws Exception {

        Object childClassLoaderKernelServices = createChildClassLoaderKernelServices(mainSubsystemName, extensionClassName, additionalInit, validateOpsFilter, bootOperations, legacyModelVersion, persistXml);
        return new LegacyControllerKernelServicesProxy(legacyChildFirstClassLoader, childClassLoaderKernelServices, objectConverter);
    }

    private Object createChildClassLoaderKernelServices(String mainSubsystemName, String extensionClassName, AdditionalInitialization additionalInit, ModelTestOperationValidatorFilter validateOpsFilter,
                                                        List<ModelNode> bootOperations, ModelVersion legacyModelVersion, boolean persistXml) {
        try {
            Stability stability = additionalInit.getStability();
            Class<?> clazz = legacyChildFirstClassLoader.loadClass(ChildFirstClassLoaderKernelServicesFactory.class.getName());

            List<Object> convertedBootOps = getConvertedBootOps(bootOperations);

            Object convertedAdditionalInit = objectConverter.convertAdditionalInitializationToChildCl(additionalInit);
            Object convertedModelVersion = objectConverter.convertModelVersionToChildCl(legacyModelVersion);
            Object convertedValidateOpsFilter = objectConverter.convertValidateOperationsFilterToChildCl(validateOpsFilter);

            if (!Stability.DEFAULT.equals(stability)) {
                Method m = clazz.getMethod("create",
                        String.class,
                        String.class,
                        legacyChildFirstClassLoader.loadClass(AdditionalInitialization.class.getName()),
                        legacyChildFirstClassLoader.loadClass(ModelTestOperationValidatorFilter.class.getName()),
                        List.class,
                        legacyChildFirstClassLoader.loadClass(ModelVersion.class.getName()),
                        Boolean.TYPE,
                        String.class);

                return m.invoke(null,
                        mainSubsystemName,
                        extensionClassName,
                        convertedAdditionalInit,
                        convertedValidateOpsFilter,
                        convertedBootOps,
                        convertedModelVersion,
                        persistXml,
                        stability.toString());
            } else {
                Method m = clazz.getMethod("create",
                        String.class,
                        String.class,
                        legacyChildFirstClassLoader.loadClass(AdditionalInitialization.class.getName()),
                        legacyChildFirstClassLoader.loadClass(ModelTestOperationValidatorFilter.class.getName()),
                        List.class,
                        legacyChildFirstClassLoader.loadClass(ModelVersion.class.getName()),
                        Boolean.TYPE);

                return m.invoke(null,
                        mainSubsystemName,
                        extensionClassName,
                        convertedAdditionalInit,
                        convertedValidateOpsFilter,
                        convertedBootOps,
                        convertedModelVersion,
                        persistXml);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Object> getConvertedBootOps(List<ModelNode> bootOperations) {
        List<Object> convertedBootOps = new ArrayList<>();
        for (ModelNode node : bootOperations) {
            if (node != null) {
                convertedBootOps.add(objectConverter.convertModelNodeToChildCl(node));
            }
        }
        return convertedBootOps;
    }
}
