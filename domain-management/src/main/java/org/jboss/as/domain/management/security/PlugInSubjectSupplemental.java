/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.security.auth.Subject;

import org.jboss.as.core.security.RealmGroup;
import org.jboss.as.core.security.RealmUser;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.plugin.AuthorizationPlugIn;
import org.jboss.as.domain.management.plugin.PlugInConfigurationSupport;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.authz.AuthorizationIdentity;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

/**
 * The {@link SubjectSupplementalService} for Plug-Ins
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class PlugInSubjectSupplemental extends AbstractPlugInService implements Service,
        SubjectSupplementalService {

    private static final String SERVICE_SUFFIX = "plug-in-authorization";

    private final Consumer<SubjectSupplementalService> subjectSupplementalServiceConsumer;

    PlugInSubjectSupplemental(final Consumer<SubjectSupplementalService> subjectSupplementalServiceConsumer,
                              final Supplier<PlugInLoaderService> plugInLoaderSupplier,
                              final String realmName, final String name, final Map<String, String> properties) {
        super(plugInLoaderSupplier, realmName, name, properties);
        this.subjectSupplementalServiceConsumer = subjectSupplementalServiceConsumer;
    }

    @Override
    public void start(final StartContext context) {
        subjectSupplementalServiceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        subjectSupplementalServiceConsumer.accept(null);
    }


    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    public SubjectSupplemental getSubjectSupplemental(Map<String, Object> sharedState) {
        final String name = getPlugInName();
        final AuthorizationPlugIn ap = getPlugInLoader().loadAuthorizationPlugIn(name);
        if (ap instanceof PlugInConfigurationSupport) {
            PlugInConfigurationSupport pcf = (PlugInConfigurationSupport) ap;
            try {
                pcf.init(getConfiguration(), sharedState);
            } catch (IOException e) {
                throw DomainManagementLogger.ROOT_LOGGER.unableToInitialisePlugIn(name, e.getMessage());
            }
        }

        return new SubjectSupplemental() {

            public void supplementSubject(Subject subject) throws IOException {
                Set<RealmUser> users = subject.getPrincipals(RealmUser.class);
                Set<Principal> principals = subject.getPrincipals();
                // In general we expect exactly one RealmUser, however we could cope with multiple
                // identities so load the roles for them all.
                for (RealmUser current : users) {
                    principals.addAll(loadGroups(current));
                }
            }

            private Set<RealmGroup> loadGroups(final RealmUser user) throws IOException {
                Set<RealmGroup> response;
                String[] groups = ap.loadRoles(user.getName(), getRealmName());
                response = new HashSet<RealmGroup>(groups.length);
                for (String current : groups) {
                    RealmGroup newGroup = new RealmGroup(getRealmName(), current);
                    SECURITY_LOGGER.tracef("Adding group '%s' for user '%s'.", newGroup, user);
                    response.add(newGroup);
                }
                return response;
            }

        };

    }

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            try {
                final String name = getPlugInName();
                final AuthorizationPlugIn ap = getPlugInLoader().loadAuthorizationPlugIn(name);
                if (ap instanceof PlugInConfigurationSupport) {
                    PlugInConfigurationSupport pcf = (PlugInConfigurationSupport) ap;
                    try {
                        pcf.init(getConfiguration(), SecurityRealmService.SharedStateSecurityRealm.getSharedState());
                    } catch (IOException e) {
                        throw DomainManagementLogger.ROOT_LOGGER.unableToInitialisePlugIn(name, e.getMessage());
                    }
                }

                String[] groups = ap.loadRoles(principal.getName(), getRealmName());
                if (SECURITY_LOGGER.isTraceEnabled()) {
                    for (String group : groups) {
                        SECURITY_LOGGER.tracef("Adding group '%s' for identity '%s'.", group, principal.getName());
                    }
                }
                return new RealmIdentityImpl(principal, groups);
            } catch (IOException e) {
                throw new RealmUnavailableException(e);
            }
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                throws RealmUnavailableException {
            return SupportLevel.UNSUPPORTED;
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec)
                throws RealmUnavailableException {
            return SupportLevel.UNSUPPORTED;
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return SupportLevel.UNSUPPORTED;
        }

        private class RealmIdentityImpl implements RealmIdentity {

            private final Principal principal;
            private final String[] groups;

            private RealmIdentityImpl(final Principal principal, final String[] groups) {
                this.principal = principal;
                this.groups = groups;
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                    throws RealmUnavailableException {
                return SupportLevel.UNSUPPORTED;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec)
                    throws RealmUnavailableException {
                return SupportLevel.UNSUPPORTED;
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return null;
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                    throws RealmUnavailableException {
                return SupportLevel.UNSUPPORTED;
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {
                return false;
            }

            @Override
            public boolean exists() throws RealmUnavailableException {
                return true;
            }

            @Override
            public AuthorizationIdentity getAuthorizationIdentity() throws RealmUnavailableException {
                Map<String, List<String>> groupsAttributeMap = new HashMap<String, List<String>>();
                groupsAttributeMap.put("GROUPS", Arrays.asList(groups));

                return AuthorizationIdentity.basicIdentity(new MapAttributes(Collections.unmodifiableMap(groupsAttributeMap)));
            }

        }

    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealm.ServiceUtil.createServiceName(realmName).append(SERVICE_SUFFIX);
        }
    }

}
