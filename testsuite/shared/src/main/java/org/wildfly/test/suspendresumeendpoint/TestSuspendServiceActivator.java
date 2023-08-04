package org.wildfly.test.suspendresumeendpoint;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.network.SocketBindingManager;
import org.wildfly.extension.requestcontroller.RequestController;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceRegistryException;

/**
 * @author Stuart Douglas
 */
public class TestSuspendServiceActivator implements ServiceActivator {

    @SuppressWarnings("deprecation")
    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {

        TestUndertowService testUndertowService = new TestUndertowService();
        serviceActivatorContext.getServiceTarget().addService(TestUndertowService.SERVICE_NAME, testUndertowService)
                .addDependency(RuntimeCapability.Builder.of("org.wildfly.request-controller", RequestController.class).build().getCapabilityServiceName(),
                        RequestController.class, testUndertowService.getRequestControllerInjectedValue())
                .addDependency(RuntimeCapability.Builder.of("org.wildfly.management.socket-binding-manager", SocketBindingManager.class).build().getCapabilityServiceName(),
                        SocketBindingManager.class, testUndertowService.getSocketBindingManagerInjectedValue())
                .install();
    }
}
