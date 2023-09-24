/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.cli.jpms;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.EnumMap;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

public class JPMSTestActivator implements ServiceActivator, Service {
    public static final ServiceName SERVICE_NAME = ServiceName.of("test", "deployment", "trivial");
    public static final String DEFAULT_SYS_PROP_NAME = "test.deployment.trivial.prop";
    public static final String DEFAULT_SYS_PROP_VALUE = "default-value";

    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        // requires opens java.base/java.util
        // Done by org.wildfly.clustering.marshalling.spi.util.EnumMapExternalizer
        for (Field field : EnumMap.class.getDeclaredFields()) {
            if (field.getType() == Class.class) {
                field.setAccessible(true);
            }
        }

        // requires opens java.base/java.lang.invoke
        // Done by RestEasy
        Constructor<MethodHandles.Lookup> constructor;
        try {
            constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        serviceActivatorContext.getServiceTarget()
               .addService(SERVICE_NAME)
               .setInstance(this)
               .setInitialMode(ServiceController.Mode.ACTIVE)
               .install();
    }

    @Override
    public void start(StartContext context) throws StartException {
        System.setProperty(DEFAULT_SYS_PROP_NAME, DEFAULT_SYS_PROP_VALUE);
    }

    @Override
    public void stop(StopContext context) {
        System.clearProperty(DEFAULT_SYS_PROP_NAME);
    }
}
