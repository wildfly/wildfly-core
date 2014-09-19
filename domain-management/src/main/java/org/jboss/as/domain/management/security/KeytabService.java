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

import org.jboss.as.domain.management.SubjectIdentity;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class KeytabService implements Service<KeytabService> {

    private final String principal;
    private final String path;
    private final String[] forHosts;
    private final boolean debug;
    private final InjectedValue<String> relativeTo = new InjectedValue<String>();

    KeytabService(final String principal, final String path, final String[] forHosts, final boolean debug) {
        this.principal = principal;
        this.path = path;
        this.forHosts = forHosts;
        this.debug = debug;
    }

    /*
     * Service Methods
     */

    @Override
    public KeytabService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(StartContext context) throws StartException {
        // Create the JAAS config.

    }

    @Override
    public void stop(StopContext context) {
        // Destroy the JAAS config.

    }

    Injector<String> getRelativeToInjector() {
        return relativeTo;
    }

    /*
     * Exposed Methods
     */

    public String getPrincipal() {
        return principal;
    }

    public String[] getForHosts() {
        return forHosts.clone();
    }

    public SubjectIdentity createSubjectIdentity(final boolean isClient) {
        return null; // TODO - Implement
    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName, final String principal) {
            return KeytabIdentityFactoryService.ServiceUtil.createServiceName(realmName).append(principal);
        }

        public static ServiceBuilder<?> addDependency(ServiceBuilder<?> sb, Injector<KeytabService> injector,
                String realmName, String principal) {
            ServiceBuilder.DependencyType type = ServiceBuilder.DependencyType.REQUIRED;
            sb.addDependency(type, createServiceName(realmName, principal), KeytabService.class, injector);

            return sb;
        }

    }

}
