/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
