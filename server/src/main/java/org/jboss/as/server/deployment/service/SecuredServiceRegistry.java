package org.jboss.as.server.deployment.service;

import org.jboss.as.server.security.ServerPermission;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;

import java.security.Permission;
import java.util.List;

/**
 *
 * TODO: these checks should be part of MSC
 * @author Stuart Douglas
 */
public class SecuredServiceRegistry implements ServiceRegistry {

    /**
     * A {@link org.jboss.as.server.security.ServerPermission} needed to use {@link org.jboss.as.server.deployment.service.SecuredServiceRegistry}, i.e. invoke its methods. The name of the permission is "{@code useServiceRegistry}."
     */
    public static final Permission PERMISSION = ServerPermission.USE_SERVICE_REGISTRY;

    private final ServiceRegistry delegate;

    public SecuredServiceRegistry(ServiceRegistry delegate) {
        this.delegate = delegate;
    }

    @Override
    public ServiceController<?> getRequiredService(ServiceName serviceName) throws ServiceNotFoundException {
        checkPermission(PERMISSION);
        return delegate.getRequiredService(serviceName);
    }

    @Override
    public ServiceController<?> getService(ServiceName serviceName) {
        checkPermission(PERMISSION);
        return delegate.getService(serviceName);
    }

    @Override
    public List<ServiceName> getServiceNames() {
        checkPermission(PERMISSION);
        return delegate.getServiceNames();
    }

    private static void checkPermission(final Permission permission) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(permission);
        }
    }
}
