package org.jboss.as.server.deployment.service;

import org.jboss.as.server.security.ServerPermission;
import org.jboss.msc.service.DelegatingServiceRegistry;
import org.jboss.msc.service.ServiceRegistry;

import java.security.Permission;

/**
 * TODO: these checks should be part of MSC
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class SecuredServiceRegistry extends DelegatingServiceRegistry {

    /**
     * A {@link org.jboss.as.server.security.ServerPermission} needed to use
     * {@link org.jboss.as.server.deployment.service.SecuredServiceRegistry}, i.e. invoke its methods.
     * The name of the permission is "{@code useServiceRegistry}."
     */
    private static final Permission PERMISSION = ServerPermission.USE_SERVICE_REGISTRY;

    public SecuredServiceRegistry(final ServiceRegistry delegate) {
        super(delegate);
    }

    @Override
    protected ServiceRegistry getDelegate() {
        final SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(PERMISSION);
        }
        return super.getDelegate();
    }
}
