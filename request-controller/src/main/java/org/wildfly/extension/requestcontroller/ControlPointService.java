/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.requestcontroller;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * A service that manages the lifecycle of a control point.
 *
 * WARNING: If the request controller is disabled then the control point will not be created,
 * code that uses this control point must be aware that in some situations it will be null.
 *
 * @author Stuart Douglas
 */
public class ControlPointService implements Service<ControlPoint>{

    private static final ServiceName SERVICE_NAME = RequestController.SERVICE_NAME.append("control-point");
    private final String deployment;
    private final String entryPoint;
    private volatile ControlPoint value;
    private final InjectedValue<RequestController> globalRequestControllerInjectedValue = new InjectedValue<>();

    ControlPointService(String deployment, String entryPoint) {
        this.deployment = deployment;
        this.entryPoint = entryPoint;
    }

    public static ServiceName serviceName(final String deployment, final String entryPoint) {
        return SERVICE_NAME.append(deployment, entryPoint);
    }

    public static void install(final ServiceTarget target, final String deployment, final String entryPoint) {
        ControlPointService service = new ControlPointService(deployment, entryPoint);
        target.addService(serviceName(deployment, entryPoint), service)
                .addDependency(RequestController.SERVICE_NAME, RequestController.class, service.globalRequestControllerInjectedValue)
                .install();
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        value = globalRequestControllerInjectedValue.getValue().getControlPoint(deployment, entryPoint);
    }

    @Override
    public void stop(StopContext stopContext) {
        globalRequestControllerInjectedValue.getValue().removeControlPoint(value);
        value = null;
    }

    @Override
    public ControlPoint getValue() throws IllegalStateException, IllegalArgumentException {
        return value;
    }
}
