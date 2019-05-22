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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.security.auth.login.LoginException;

import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.SubjectIdentity;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * {@link Service} responsible for {@link SubjectIdentity} creation.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class KeytabIdentityFactoryService implements Service {

    private static final String SERVICE_SUFFIX = "keytab_factory";

    private final Consumer<KeytabIdentityFactoryService> serviceConsumer;
    private final Set<Supplier<KeytabService>> keytabServices = Collections.synchronizedSet(new HashSet<>());

    private volatile KeytabService defaultService = null;
    private volatile Map<String, KeytabService> hostServiceMap = null;

    KeytabIdentityFactoryService(final Consumer<KeytabIdentityFactoryService> serviceConsumer) {
        this.serviceConsumer = serviceConsumer;
    }

    /*
     * Service Methods.
     */

    @Override
    public void start(final StartContext context) throws StartException {
        Set<Supplier<KeytabService>> services = keytabServices;

        hostServiceMap = new HashMap<String, KeytabService>(services.size()); // Assume at least one per service.
        /*
         * Iterate the services and find the first one to offer default resolution, also create a hostname to KeytabService map
         * for the first one that claims each host.
         */
        for (Supplier<KeytabService> current : services) {
            for (String currentHost : current.get().getForHosts()) {
                if ("*".equals(currentHost)) {
                    if (defaultService == null) {
                        defaultService = current.get();
                    }
                } else if (currentHost != null) {
                    int idx = currentHost.indexOf("/");
                    String hostKey = idx > -1 ? currentHost.substring(0, idx) + "/" + currentHost.substring(idx + 1).toLowerCase(Locale.ENGLISH) :
                        currentHost.toLowerCase(Locale.ENGLISH);
                    if (hostServiceMap.containsKey(hostKey) == false) {
                        hostServiceMap.put(hostKey, current.get());
                    }
                }
            }
        }

        /*
         * Iterate the services again and attempt to identify host names from the principal name and add to the map if there is
         * not already a mapping for that host name.
         */
        for (Supplier<KeytabService> current : services) {
            String principal = current.get().getPrincipal();
            int start = principal.indexOf('/');
            int end = principal.indexOf('@');

            String currentHost = principal.substring(start > -1 ? start + 1 : 0, end > -1 ? end : principal.length() - 1);
            if (hostServiceMap.containsKey(currentHost.toLowerCase(Locale.ENGLISH)) == false) {
                hostServiceMap.put(currentHost.toLowerCase(Locale.ENGLISH), current.get());
            }
            principal = principal.substring(0, end > -1 ? end : principal.length() - 1);
            if (principal.equals(currentHost) == false) {
                String principalKey = principal.substring(0, start) + "/" + currentHost.toLowerCase(Locale.ENGLISH);
                if (hostServiceMap.containsKey(principalKey) == false) {
                    hostServiceMap.put(principalKey, current.get());
                }
            }
        }
        if (serviceConsumer != null) {
            serviceConsumer.accept(this);
        }
    }

    @Override
    public void stop(final StopContext context) {
        if (serviceConsumer != null) {
            serviceConsumer.accept(null);
        }
        defaultService = null;
        hostServiceMap = null;
    }

    void addKeytabSupplier(final Supplier<KeytabService> supplier) {
        keytabServices.add(supplier);
    }

    /*
     * SubjectIdentity factory method.
     */

    SubjectIdentity getSubjectIdentity(final String protocol, final String forHost) {
        KeytabService selectedService = null;

        final String hostName = forHost == null ? null : forHost.toLowerCase(Locale.ENGLISH);
        String name = protocol + "/" + hostName;
        selectedService = hostServiceMap.get(name);
        if (selectedService == null) {
            SECURITY_LOGGER.tracef("No mapping for name '%s' to KeytabService, attempting to use host only match.", name);
            selectedService = hostServiceMap.get(hostName);
            if (selectedService == null) {
                SECURITY_LOGGER.tracef("No mapping for host '%s' to KeytabService, attempting to use default.", forHost);
                selectedService = defaultService;
            }
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

        public static Supplier<KeytabIdentityFactoryService> requires(final ServiceBuilder<?> sb, final String realmName) {
            return sb.requires(createServiceName(realmName));
        }

    }

}
