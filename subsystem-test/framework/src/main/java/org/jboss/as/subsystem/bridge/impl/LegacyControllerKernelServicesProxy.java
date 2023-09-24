/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.bridge.impl;

import java.lang.reflect.Method;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.model.test.ModelTestLegacyControllerKernelServicesProxy;
import org.jboss.as.subsystem.bridge.local.ClassLoaderObjectConverter;
import org.jboss.as.subsystem.bridge.local.OperationTransactionControlProxy;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesInternal;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class LegacyControllerKernelServicesProxy extends ModelTestLegacyControllerKernelServicesProxy implements KernelServices, KernelServicesInternal {

    private final ClassLoaderObjectConverter converter;
    private Method readFullModelDescription;

    public LegacyControllerKernelServicesProxy(ClassLoader childFirstClassLoader, Object childFirstClassLoaderServices, ClassLoaderObjectConverter converter) {
        super(childFirstClassLoader, childFirstClassLoaderServices);
        this.converter = converter;
    }

    @Override
    public KernelServices getLegacyServices(ModelVersion modelVersion) {
        throw new IllegalStateException("Can only be called for the main controller");
    }

    @Override
    public OperationTransformer.TransformedOperation executeInMainAndGetTheTransformedOperation(ModelNode op, ModelVersion modelVersion) {
        throw new IllegalStateException("Can only be called for the main controller");
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
    public ModelNode readFullModelDescription(ModelNode pathAddress) {
        try {
            if (readFullModelDescription == null) {
                readFullModelDescription = childFirstClassLoaderServices.getClass().getMethod("readFullModelDescription",
                        childFirstClassLoader.loadClass(ModelNode.class.getName()));
                //System.out.println(readFullModelDescription);
            }
            return convertModelNodeFromChildCl(
                    readFullModelDescription.invoke(childFirstClassLoaderServices,
                            convertModelNodeToChildCl(pathAddress)));
        } catch (Exception e) {
            unwrapInvocationTargetRuntimeException(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Class<?> getTestClass() {
        throw new IllegalStateException("Only callable from the main controller");
    }
}
