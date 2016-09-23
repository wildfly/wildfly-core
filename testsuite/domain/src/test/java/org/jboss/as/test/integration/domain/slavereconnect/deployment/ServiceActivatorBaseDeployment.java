/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.domain.slavereconnect.deployment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
        System.setProperty(propertyName, qualifier);
        System.out.println("===> " + this.getClass() + " setting property " + propertyName + "=" + qualifier);
        InputStream in = getClass().getResourceAsStream("overlay");
        if (in != null) {
            try {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))){
                    String s = reader.readLine();
                    System.setProperty(overridePropertyName, s);
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

    @Override
    public void stop(StopContext context) {
        System.clearProperty(propertyName);
        System.clearProperty(overridePropertyName);
        System.out.println("===> " + this.getClass() + " clearing property " + propertyName);
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }
}