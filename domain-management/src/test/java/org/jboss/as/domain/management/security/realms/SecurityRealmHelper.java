/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.security.realms;

import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

import java.util.function.Supplier;

final class SecurityRealmHelper {

    private static final ServiceName SECURITY_REALM_GETTER = ServiceName.of("sec.realm.getter.for.test");

    private SecurityRealmHelper() {
        // forbidden instantiation
    }

    static SecurityRealm getSecurityRealm(final ServiceTarget target, final ServiceName securityRealmSN) {
        SecurityRealm retVal = null;
        ServiceBuilder<?> builder = target.addService(SECURITY_REALM_GETTER.append(securityRealmSN));
        Supplier<SecurityRealm> securityRealmSupplier = builder.requires(securityRealmSN);
        SecurityRealmGetterService service = new SecurityRealmGetterService(securityRealmSupplier);
        builder.setInstance(service);
        StabilityMonitor sm = new StabilityMonitor();
        builder.addMonitor(sm);
        ServiceController<?> ctrl = builder.install();
        try {
            sm.awaitStability();
            retVal = service.securityRealmSupplier.get();
        } catch (Throwable t) {
            // ignored
        } finally {
            sm.clear();
            ctrl.setMode(ServiceController.Mode.REMOVE);
            try {
                sm.awaitStability();
            } catch (Throwable t) {
                // ignored
            } finally {
                sm.removeController(ctrl);
            }
        }
        return retVal;
    }

    private static final class SecurityRealmGetterService implements Service {

        private final Supplier<SecurityRealm> securityRealmSupplier;

        private SecurityRealmGetterService(final Supplier<SecurityRealm> securityRealmSupplier) {
            this.securityRealmSupplier = securityRealmSupplier;
        }

        @Override
        public void start(final StartContext startContext) {
            // does nothing
        }

        @Override
        public void stop(final StopContext stopContext) {
            // does nothing
        }
    }

}
