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


    public LegacyControllerKernelServicesProxy createKernelServices(List<ModelNode> bootOperations, ModelTestOperationValidatorFilter validateOpsFilter, ModelVersion legacyModelVersion, List<LegacyModelInitializerEntry> modelInitializerEntries) throws Exception {

        Object childClassLoaderKernelServices = createChildClassLoaderKernelServices(bootOperations, validateOpsFilter, legacyModelVersion, modelInitializerEntries);
        return new LegacyControllerKernelServicesProxy(legacyChildFirstClassLoader, childClassLoaderKernelServices, objectConverter);
    }

    private Object createChildClassLoaderKernelServices(List<ModelNode> bootOperations, ModelTestOperationValidatorFilter validateOpsFilter, ModelVersion legacyModelVersion, List<LegacyModelInitializerEntry> modelInitializerEntries){
        try {
            Class<?> clazz = legacyChildFirstClassLoader.loadClass(ChildFirstClassLoaderKernelServicesFactory.class.getName());

            Method m = clazz.getMethod("create",
                    List.class,
                    legacyChildFirstClassLoader.loadClass(ModelTestOperationValidatorFilter.class.getName()),
                    legacyChildFirstClassLoader.loadClass(ModelVersion.class.getName()),
                    List.class);

            List<Object> convertedBootOps = new ArrayList<Object>();
            for (int i = 0 ; i < bootOperations.size() ; i++) {
                ModelNode node = bootOperations.get(i);
                if (node != null) {
                    convertedBootOps.add(objectConverter.convertModelNodeToChildCl(node));
                }
            }

            List<Object> convertedModelInitializerEntries = null;
            if (modelInitializerEntries != null) {
                convertedModelInitializerEntries = new ArrayList<Object>();
                for (LegacyModelInitializerEntry entry : modelInitializerEntries) {
                    convertedModelInitializerEntries.add(objectConverter.convertLegacyModelInitializerEntryToChildCl(entry));
                }
            }

            return m.invoke(null, convertedBootOps, objectConverter.convertValidateOperationsFilterToChildCl(validateOpsFilter), objectConverter.convertModelVersionToChildCl(legacyModelVersion), convertedModelInitializerEntries);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
