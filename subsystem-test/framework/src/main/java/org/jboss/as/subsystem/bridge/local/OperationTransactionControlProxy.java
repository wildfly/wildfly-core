/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.bridge.local;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelController.OperationTransaction;
import org.jboss.as.subsystem.bridge.impl.ClassLoaderObjectConverterImpl;
import org.jboss.as.subsystem.bridge.shared.ObjectSerializer;
import org.jboss.dmr.ModelNode;

/**
 * This will run in the child classloader
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OperationTransactionControlProxy implements ModelController.OperationTransactionControl{

    final ClassLoaderObjectConverter converter;
    final Object mainTransactionControl;


    public OperationTransactionControlProxy(Object mainTransactionControl) {
        this.converter = new ClassLoaderObjectConverterImpl(ObjectSerializer.class.getClassLoader(), this.getClass().getClassLoader());
        this.mainTransactionControl = mainTransactionControl;
    }


    @Override
    public void operationPrepared(OperationTransaction transaction, ModelNode result) {
        Class<?> mainClass = mainTransactionControl.getClass();
        ClassLoader mainCl = mainClass.getClassLoader();

        try {
            Method method = mainClass.getMethod("operationPrepared",
                    mainCl.loadClass(ModelController.OperationTransaction.class.getName()),
                    mainCl.loadClass(ModelNode.class.getName()));

            method.setAccessible(true);

            Class<?> mainOpTxClass = mainCl.loadClass(OperationTransactionProxy.class.getName());
            Constructor<?> ctor = mainOpTxClass.getConstructors()[0];
            Object mainOpTxProxy = ctor.newInstance(transaction);

            method.invoke(mainTransactionControl, mainOpTxProxy, convertModelNodeToMainCl(result));
        } catch (Exception e) {
            unwrapInvocationTargetRuntimeException(e);
            throw new RuntimeException(e);
        }
    }

    private Object convertModelNodeToMainCl(ModelNode modelNode) throws Exception {
        ObjectSerializer localSerializer = ObjectSerializer.FACTORY.createSerializer(this.getClass().getClassLoader());
        ObjectSerializer mainSerializer = ObjectSerializer.FACTORY.createSerializer(ObjectSerializer.class.getClassLoader());
        return mainSerializer.deserializeModelNode(localSerializer.serializeModelNode(modelNode));
    }

    private static void unwrapInvocationTargetRuntimeException(Exception e) {
        e.printStackTrace();
        if (e instanceof InvocationTargetException) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
        }
    }

    public static class OperationTransactionProxy implements ModelController.OperationTransaction {
        private final Object tx;

        public OperationTransactionProxy(Object tx) {
            this.tx = tx;
        }

        @Override
        public void commit() {
            invoke("commit");
        }

        @Override
        public void rollback() {
            invoke("rollback");
        }

        private void invoke(String name) {
            try {
                Method m = tx.getClass().getMethod(name);
                m.setAccessible(true);
                m.invoke(tx);
            } catch (Exception e) {
                unwrapInvocationTargetRuntimeException(e);
                throw new RuntimeException(e);
            }
        }
    }
}
