package org.wildfly.test.suspendresumeendpoint;

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
                .addDependency(RequestController.SERVICE_NAME, RequestController.class, testUndertowService.getRequestControllerInjectedValue())
                .addDependency(SocketBindingManager.SOCKET_BINDING_MANAGER, SocketBindingManager.class, testUndertowService.getSocketBindingManagerInjectedValue())
                .install();
    }
}
