package org.wildfly.test.suspendresumeendpoint;

import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.server.requestcontroller.GlobalRequestController;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceRegistryException;

/**
 * @author Stuart Douglas
 */
public class TestSuspendServiceActivator implements ServiceActivator {

    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {

        TestUndertowService testUndertowService = new TestUndertowService();
        serviceActivatorContext.getServiceTarget().addService(TestUndertowService.SERVICE_NAME, testUndertowService)
                .addDependency(GlobalRequestController.SERVICE_NAME, GlobalRequestController.class, testUndertowService.getRequestControllerInjectedValue())
                .addDependency(SocketBindingManager.SOCKET_BINDING_MANAGER, SocketBindingManager.class, testUndertowService.getSocketBindingManagerInjectedValue())
                .install();
    }
}
