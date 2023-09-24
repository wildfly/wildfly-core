/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.secondaryreconnect.deployment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServiceActivatorBaseDeployment implements ServiceActivator, Service<Void> {

    private final ServiceName serviceName;
    private final String propertyName;
    private final String overridePropertyName;
    private final String qualifier;

    protected ServiceActivatorBaseDeployment(String qualifier) {
        serviceName = ServiceName.of("test", "deployment", qualifier);
        propertyName = "test.deployment.prop." + qualifier;
        overridePropertyName = "test.overlay.prop." + qualifier;
        this.qualifier = qualifier;
    }

    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        serviceActivatorContext.getServiceTarget().addService(serviceName, this).install();
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        setProperty(propertyName, qualifier);
        System.out.println("===> " + this.getClass() + " setting property " + propertyName + "=" + qualifier);
        InputStream in = getClass().getResourceAsStream("overlay");
        if (in != null) {
            try {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))){
                    String s = reader.readLine();
                    setProperty(overridePropertyName, s);
                    System.out.println("===> " + this.getClass() + " setting property " + overridePropertyName + "=" + s);
                } catch (IOException e) {
                    throw new StartException(e);
                }
            } finally {
                try {
                    in.close();
                } catch (IOException ignore){
                }
            }
        }
    }

    private static void setProperty(final String key, final String value) {
        if (System.getSecurityManager() == null) {
            System.setProperty(key, value);
        } else {
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                System.setProperty(key, value);
                return null;
            });
        }
    }

    private static void clearProperty(final String key) {
        if (System.getSecurityManager() == null) {
            System.clearProperty(key);
        } else {
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                System.clearProperty(key);
                return null;
            });
        }
    }

    @Override
    public void stop(StopContext context) {
        clearProperty(propertyName);
        clearProperty(overridePropertyName);
        System.out.println("===> " + this.getClass() + " clearing property " + propertyName);
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }
}