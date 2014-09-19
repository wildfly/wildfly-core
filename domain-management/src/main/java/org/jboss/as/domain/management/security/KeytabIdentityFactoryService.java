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

    /*
     * Service Methods.
     */

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

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

    SubjectIdentity getSubjectIdentity(final String forHost, final boolean isClient) {
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
