/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.bridge.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.core.model.bridge.local.ClassLoaderObjectConverter;
import org.jboss.as.core.model.bridge.local.OperationTransactionControlProxy;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.host.controller.ignored.IgnoreDomainResourceTypeResource;
import org.jboss.as.model.test.ModelTestLegacyControllerKernelServicesProxy;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class LegacyControllerKernelServicesProxy extends ModelTestLegacyControllerKernelServicesProxy implements KernelServices {

    private final ClassLoaderObjectConverter converter;

    private Method applyMasterDomainModel;

    public LegacyControllerKernelServicesProxy(ClassLoader childFirstClassLoader, Object childFirstClassLoaderServices, ClassLoaderObjectConverter converter) {
        super(childFirstClassLoader, childFirstClassLoaderServices);
        this.converter = converter;
    }

    public KernelServices getLegacyServices(ModelVersion modelVersion) {
        throw new IllegalStateException("Can only be called for the main controller");
    }


    public void applyMasterDomainModel(ModelNode resources, List<IgnoreDomainResourceTypeResource> ignoredResources) {
        try {
            if (applyMasterDomainModel == null) {
                applyMasterDomainModel = childFirstClassLoaderServices.getClass().getMethod("applyMasterDomainModel",
                        childFirstClassLoader.loadClass(resources.getClass().getName()),
                        List.class);
            }

            List<Object> convertedResources = new ArrayList<Object>();
            if (ignoredResources != null) {
                for (IgnoreDomainResourceTypeResource resource : ignoredResources) {
                    convertedResources.add(converter.convertIgnoreDomainTypeResourceToChildCl(resource));
                }
            }
            applyMasterDomainModel.invoke(childFirstClassLoaderServices, converter.convertModelNodeToChildCl(resources), convertedResources);
        } catch (Exception e) {
            unwrapInvocationTargetRuntimeException(e);
            throw new RuntimeException(e);
        }

    }

    @Override
    protected Object convertModelNodeToChildCl(ModelNode modelNode) {
        return converter.convertModelNodeToChildCl(modelNode);
    }

    @Override
    protected ModelNode convertModelNodeFromChildCl(Object object) {
        return converter.convertModelNodeFromChildCl(object);
    }

    @Override
    protected String getOperationTransactionProxyClassName() {
        return OperationTransactionControlProxy.class.getName();
    }

    @Override
    public void applyMasterDomainModel(ModelVersion modelVersion, List<IgnoreDomainResourceTypeResource> ignoredResources) {
        throw new IllegalStateException("Can only be called for the main controller");
    }
}
