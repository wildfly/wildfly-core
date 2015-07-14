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

package org.jboss.as.domain.management.security;

import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.security.auth.login.LoginException;

import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.SubjectIdentity;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedSetValue;

/**
 * {@link Service} responsible for {@link SubjectIdentity} creation.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class KeytabIdentityFactoryService implements Service<KeytabIdentityFactoryService> {

    private static final String SERVICE_SUFFIX = "keytab_factory";

    private final InjectedSetValue<KeytabService> keytabServices = new InjectedSetValue<KeytabService>();

    private volatile KeytabService defaultService = null;
    private volatile Map<String, KeytabService> hostServiceMapByForHosts = null;
    private volatile Map<String, KeytabService> hostServiceMapByPrincipal = null;

    /*
     * Service Methods.
     */

    @Override
    public void start(StartContext context) throws StartException {
        Set<KeytabService> services = keytabServices.getValue();

        hostServiceMapByForHosts = new HashMap<String, KeytabService>(services.size()); // Assume at least one per service.
        hostServiceMapByPrincipal = new HashMap<String, KeytabService>(services.size());
        /*
         * Iterate the services and find the first one to offer default resolution, also create a hostname to KeytabService map
         * for the first one that claims each host.
         */
        for (KeytabService current : services) {
            for (String currentHost : current.getForHosts()) {
                if ("*".equals(currentHost)) {
                    if (defaultService == null) {
                        defaultService = current;
                    }
                } else if (!hostServiceMapByForHosts.containsKey(currentHost)) {
                    hostServiceMapByForHosts.put(currentHost, current);
                }
            }
        }

        /*
         * Iterate the services again and attempt to identify host names from the principal name and add to the map if there is
         * not already a mapping for that host name.
         */
        for (KeytabService current : services) {
            String principal = current.getPrincipal();
            int start = principal.indexOf('/');
            int end = principal.indexOf('@');

            String currentHost = principal.substring(start > -1 ? start + 1 : 0, end > -1 ? end : principal.length() - 1);
            if (!hostServiceMapByPrincipal.containsKey(currentHost)) {
                hostServiceMapByPrincipal.put(currentHost, current);
            }
            principal = principal.substring(0, end > -1 ? end : principal.length() - 1);
            if (!principal.equals(currentHost) && !hostServiceMapByPrincipal.containsKey(principal)) {
                hostServiceMapByPrincipal.put(principal, current);
            }
        }
    }

    @Override
    public void stop(StopContext context) {
        defaultService = null;
        hostServiceMapByForHosts = null;
        hostServiceMapByPrincipal = null;    }

    @Override
    public KeytabIdentityFactoryService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    Injector<KeytabService> getKeytabInjector() {
        return keytabServices.injector();
    }

    /*
     * SubjectIdentity factory method.
     */

    SubjectIdentity getSubjectIdentity(final String protocol, final String forHost) {
        KeytabService selectedService;

        String name = protocol + "/" + forHost;
        selectedService = hostServiceMapByForHosts.get(name);
        if (selectedService == null) {
            SECURITY_LOGGER.tracef("No for-hosts mapping for name '%s' to KeytabService, attempting to use host only match.", name);
            selectedService = hostServiceMapByForHosts.get(forHost);
        }
        if (selectedService == null) {
            SECURITY_LOGGER.tracef("No for-hosts mapping for name '%s' to KeytabService, attempting to use mapping by principal.", name);
            selectedService = hostServiceMapByPrincipal.get(name);
        }
        if (selectedService == null) {
            SECURITY_LOGGER.tracef("No principal mapping for name '%s' to KeytabService, attempting to use host only match.", name);
            selectedService = hostServiceMapByPrincipal.get(forHost);
        }
        if (selectedService == null) {
            SECURITY_LOGGER.tracef("No principal mapping for host '%s' to KeytabService, attempting to use default.", forHost);
            selectedService = defaultService;
        }

        if (selectedService != null) {
            if (SECURITY_LOGGER.isTraceEnabled()) {
                SECURITY_LOGGER.tracef("Selected KeytabService with principal '%s' for host '%s'",
                        selectedService.getPrincipal(), forHost);
            }
            try {
                return selectedService.createSubjectIdentity(false);
            } catch (LoginException e) {
                SECURITY_LOGGER.keytabLoginFailed(selectedService.getPrincipal(), forHost, e);
                /*
                 * Allow to continue and return null, i.e. we have an error preventing Kerberos authentication so log that but
                 * other mechanisms may be available leaving the server still accessible.
                 */
            }
        } else {
            SECURITY_LOGGER.tracef("No KeytabService available for host '%s' unable to return SubjectIdentity.", forHost);
        }

        return null;
    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealm.ServiceUtil.createServiceName(realmName).append(SERVICE_SUFFIX);
        }

        public static ServiceBuilder<?> addDependency(ServiceBuilder<?> sb, Injector<KeytabIdentityFactoryService> injector,
                String realmName) {
            ServiceBuilder.DependencyType type = ServiceBuilder.DependencyType.REQUIRED;
            sb.addDependency(type, createServiceName(realmName), KeytabIdentityFactoryService.class, injector);

            return sb;
        }

    }

}
