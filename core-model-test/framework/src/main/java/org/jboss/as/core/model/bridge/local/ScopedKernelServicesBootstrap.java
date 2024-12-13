/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.bridge.local;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.core.model.bridge.impl.ChildFirstClassLoaderKernelServicesFactory;
import org.jboss.as.core.model.bridge.impl.ClassLoaderObjectConverterImpl;
import org.jboss.as.core.model.bridge.impl.LegacyControllerKernelServicesProxy;
import org.jboss.as.core.model.test.LegacyModelInitializerEntry;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ScopedKernelServicesBootstrap {
    Stability stability;
    ClassLoader legacyChildFirstClassLoader;
    ClassLoaderObjectConverter objectConverter;

    public ScopedKernelServicesBootstrap(ClassLoader legacyChildFirstClassLoader, Stability stability) {
        this.legacyChildFirstClassLoader = legacyChildFirstClassLoader;
        this.objectConverter = new ClassLoaderObjectConverterImpl(this.getClass().getClassLoader(), legacyChildFirstClassLoader);
        this.stability = stability;
    }

    public LegacyControllerKernelServicesProxy createKernelServices(List<ModelNode> bootOperations, ModelTestOperationValidatorFilter validateOpsFilter, ModelVersion legacyModelVersion, List<LegacyModelInitializerEntry> modelInitializerEntries) throws Exception {

        Object childClassLoaderKernelServices = createChildClassLoaderKernelServices(bootOperations, validateOpsFilter, legacyModelVersion, modelInitializerEntries);
        return new LegacyControllerKernelServicesProxy(legacyChildFirstClassLoader, childClassLoaderKernelServices, objectConverter);
    }

    private Object createChildClassLoaderKernelServices(List<ModelNode> bootOperations, ModelTestOperationValidatorFilter validateOpsFilter, ModelVersion legacyModelVersion, List<LegacyModelInitializerEntry> modelInitializerEntries) {
        try {
            Class<?> clazz = legacyChildFirstClassLoader.loadClass(ChildFirstClassLoaderKernelServicesFactory.class.getName());
            List<Object> convertedBootOps = getConvertedBootOps(bootOperations);
            List<Object> convertedModelInitializerEntries = convertModelInitializer(modelInitializerEntries);

            Object convertedValidationFilter = objectConverter.convertValidateOperationsFilterToChildCl(validateOpsFilter);
            Object convertedLegacyModelVersion = objectConverter.convertModelVersionToChildCl(legacyModelVersion);

            if (!Stability.DEFAULT.equals(stability)) {
                Method m = clazz.getMethod("create",
                        List.class,
                        legacyChildFirstClassLoader.loadClass(ModelTestOperationValidatorFilter.class.getName()),
                        legacyChildFirstClassLoader.loadClass(ModelVersion.class.getName()),
                        List.class,
                        String.class);

                return m.invoke(null,
                        convertedBootOps,
                        convertedValidationFilter,
                        convertedLegacyModelVersion,
                        convertedModelInitializerEntries,
                        stability.toString());
            } else {
                Method m = clazz.getMethod("create",
                        List.class,
                        legacyChildFirstClassLoader.loadClass(ModelTestOperationValidatorFilter.class.getName()),
                        legacyChildFirstClassLoader.loadClass(ModelVersion.class.getName()),
                        List.class);

                return m.invoke(null,
                        convertedBootOps,
                        convertedValidationFilter,
                        convertedLegacyModelVersion,
                        convertedModelInitializerEntries);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Object> convertModelInitializer(List<LegacyModelInitializerEntry> modelInitializerEntries) {
        List<Object> converted = null;
        if (modelInitializerEntries != null) {
            converted = new ArrayList<>();
            for (LegacyModelInitializerEntry entry : modelInitializerEntries) {
                converted.add(objectConverter.convertLegacyModelInitializerEntryToChildCl(entry));
            }
        }
        return converted;
    }

    private List<Object> getConvertedBootOps(List<ModelNode> bootOperations) {
        List<Object> converted = new ArrayList<>();
        for (ModelNode node : bootOperations) {
            if (node != null) {
                converted.add(objectConverter.convertModelNodeToChildCl(node));
            }
        }
        return converted;
    }
}

