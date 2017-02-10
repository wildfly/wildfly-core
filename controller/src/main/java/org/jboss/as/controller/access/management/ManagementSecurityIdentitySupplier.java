/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller.access.management;

import java.util.List;
import java.util.function.Supplier;

import org.jboss.as.controller.AccessAuditContext;
import org.wildfly.security.auth.principal.AnonymousPrincipal;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.ServerAuthenticationContext;

/**
 * A management specific supplier of the {@link SecurityIdentity} for access to the {@code ModelController}.
 *
 * The identity will be selected by using the following checks: -
 *     TODO Elytron - Complete the list !!
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ManagementSecurityIdentitySupplier implements Supplier<SecurityIdentity> {


    private final SecurityDomain anonymousSecurityDomain = SecurityDomain.builder()
            .setDefaultRealmName("Empty")
            .addRealm("Empty", SecurityRealm.EMPTY_REALM).build()
            .build();

    private volatile Supplier<SecurityDomain> configuredSecurityDomainSupplier;
    private volatile List<Supplier<SecurityDomain>> inflowSecurityDomainSuppliers;

    @Override
    public SecurityIdentity get() {
        SecurityIdentity securityIdentity = null;

        AccessAuditContext accessAuditContext = SecurityActions.currentAccessAuditContext();
        if (accessAuditContext != null && accessAuditContext.isInflowed()) {
            return accessAuditContext.getSecurityIdentity();
        }

        Supplier<SecurityDomain> configuredSupplier = configuredSecurityDomainSupplier;
        SecurityDomain configuredSecurityDomain = configuredSupplier != null ? configuredSupplier.get() : null;

        if (configuredSecurityDomain != null) {
            securityIdentity = configuredSecurityDomain.getCurrentSecurityIdentity();
            if (AnonymousPrincipal.getInstance().equals(securityIdentity.getPrincipal()) == false) {
                return securityIdentity;
            }
        }

        if (accessAuditContext != null) {
            securityIdentity = accessAuditContext.getSecurityIdentity();
            if (securityIdentity != null) {
                if (configuredSecurityDomain != null) {
                    ServerAuthenticationContext serverAuthenticationContext = SecurityActions.createServerAuthenticationContext(configuredSecurityDomain);
                    try {
                        if (serverAuthenticationContext.importIdentity(securityIdentity)) {
                            return serverAuthenticationContext.getAuthorizedIdentity();
                        }
                    } catch (RealmUnavailableException | IllegalStateException e) {
                        // TODO Elytron Not much we can do but we need to log this as it means the real identity has been lost
                        // and we have no way to recover it.
                        // Final fallback for when we have no security ready.
                        return anonymousSecurityDomain.getAnonymousSecurityIdentity();
                    }
                } else {
                    // TODO Elytron - For this fall though we want to check that the identity was from a wrapped realm.
                    return securityIdentity;
                }
            }
        }

        List<Supplier<SecurityDomain>> inflowSuppliers = inflowSecurityDomainSuppliers;
        if (inflowSuppliers != null && configuredSecurityDomain != null) {
            for (Supplier<SecurityDomain> inflowSupplier : inflowSuppliers) {
                SecurityDomain current = inflowSupplier.get();
                if (current != null) {
                    securityIdentity = current.getCurrentSecurityIdentity();
                    if (AnonymousPrincipal.getInstance().equals(securityIdentity.getPrincipal()) == false) {
                        ServerAuthenticationContext serverAuthenticationContext = SecurityActions.createServerAuthenticationContext(configuredSecurityDomain);

                        try {
                            if (serverAuthenticationContext.importIdentity(securityIdentity)) {
                                return serverAuthenticationContext.getAuthorizedIdentity();
                            }
                        } catch (RealmUnavailableException | IllegalStateException e) {
                            // Ignored, we may have more domains to attempt to inflow from and a final fall back to anonymous below.
                        }
                    }
                }
            }
        }

        // Final fallback for when we have no security ready.
        return anonymousSecurityDomain.getAnonymousSecurityIdentity();
    }

    public void setConfiguredSecurityDomainSupplier(Supplier<SecurityDomain> configuredSecurityDomainSupplier) {
        this.configuredSecurityDomainSupplier = configuredSecurityDomainSupplier;
    }

    public void setInflowSecurityDomainSuppliers(List<Supplier<SecurityDomain>> inflowSecurityDomainSuppliers) {
        this.inflowSecurityDomainSuppliers = inflowSecurityDomainSuppliers;
    }

}
