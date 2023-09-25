/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import static org.jboss.as.jmx.logging.JmxLogger.ROOT_LOGGER;

import java.io.ObjectInputStream;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.ReflectionException;
import javax.management.loading.ClassLoaderRepository;

import org.jboss.as.server.jmx.MBeanServerPlugin;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class BaseMBeanServerPlugin implements MBeanServerPlugin {
    public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature)
            throws ReflectionException, InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException {
        throw ROOT_LOGGER.cannotCreateMBeansInReservedDomain(name.getDomain());
    }

    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params,
            String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanException,
            NotCompliantMBeanException, InstanceNotFoundException {
        throw ROOT_LOGGER.cannotCreateMBeansInReservedDomain(name.getDomain());
    }

    public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) throws ReflectionException,
            InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException {
        throw ROOT_LOGGER.cannotCreateMBeansInReservedDomain(name.getDomain());
    }

    public ObjectInstance createMBean(String className, ObjectName name) throws ReflectionException,
            InstanceAlreadyExistsException, MBeanException, NotCompliantMBeanException {
        throw ROOT_LOGGER.cannotCreateMBeansInReservedDomain(name.getDomain());
    }

    @Deprecated
    public ObjectInputStream deserialize(ObjectName name, byte[] data) throws OperationsException {
        throw ROOT_LOGGER.dontKnowHowToDeserialize();
    }

    @Deprecated
    public ObjectInputStream deserialize(String className, byte[] data) throws OperationsException, ReflectionException {
        throw ROOT_LOGGER.dontKnowHowToDeserialize();
    }

    @Deprecated
    public ObjectInputStream deserialize(String className, ObjectName loaderName, byte[] data) throws OperationsException,
            ReflectionException {
        throw ROOT_LOGGER.dontKnowHowToDeserialize();
    }

    public ClassLoaderRepository getClassLoaderRepository() {
        throw ROOT_LOGGER.unsupportedMethod("getClassLoaderRepository");
    }

    public String getDefaultDomain() {
        throw ROOT_LOGGER.unsupportedMethod("getDefaultDomain");
    }

    public Object instantiate(String className, Object[] params, String[] signature) throws ReflectionException, MBeanException {
        throw ROOT_LOGGER.unsupportedMethod("instantiate");
    }

    public Object instantiate(String className, ObjectName loaderName, Object[] params, String[] signature)
            throws ReflectionException, MBeanException, InstanceNotFoundException {
        throw ROOT_LOGGER.unsupportedMethod("instantiate");
    }

    public Object instantiate(String className, ObjectName loaderName) throws ReflectionException, MBeanException,
            InstanceNotFoundException {
        throw ROOT_LOGGER.unsupportedMethod("instantiate");
    }

    public Object instantiate(String className) throws ReflectionException, MBeanException {
        throw ROOT_LOGGER.unsupportedMethod("instantiate");
    }

    public ObjectInstance registerMBean(Object object, ObjectName name) throws InstanceAlreadyExistsException,
            MBeanRegistrationException, NotCompliantMBeanException {
        //The constructor for this needs an underlying exception
        throw new MBeanRegistrationException(new RuntimeException(ROOT_LOGGER.cannotRegisterMBeansUnderReservedDomain(name.getDomain())));
    }

    public void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException {
        //The constructor for this needs an underlying exception
        throw new MBeanRegistrationException(new RuntimeException(ROOT_LOGGER.cannotUnregisterMBeansUnderReservedDomain(name.getDomain())));
    }

}
