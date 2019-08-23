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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.security.auth.Subject;

import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.core.security.RealmGroup;
import org.jboss.as.core.security.RealmUser;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.authz.AuthorizationIdentity;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

/**
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class PropertiesSubjectSupplemental extends PropertiesFileLoader implements Service, SubjectSupplementalService,
        SubjectSupplemental {

    private static final String SERVICE_SUFFIX = "properties_authorization";
    private static final String COMMA = ",";

    private final Consumer<SubjectSupplementalService> subjectSupplementalServiceConsumer;
    private final String realmName;

    PropertiesSubjectSupplemental(final Consumer<SubjectSupplementalService> subjectSupplementalServiceConsumer, final Supplier<PathManager> pathManagerSupplier, final String realmName, final String path, final String relativeTo) {
        super(pathManagerSupplier, path, relativeTo);
        this.subjectSupplementalServiceConsumer = subjectSupplementalServiceConsumer;
        this.realmName = realmName;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        super.start(context);
        subjectSupplementalServiceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        subjectSupplementalServiceConsumer.accept(null);
        super.stop(context);
    }

    /*
     * SubjectSupplementalService Method
     */

    public SubjectSupplemental getSubjectSupplemental(Map<String, Object> sharedState) {
        return this;
    }

    /*
     * SubjectSupplementalMethods
     */

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    /**
     * @see org.jboss.as.domain.management.security.SubjectSupplemental#supplementSubject(javax.security.auth.Subject)
     */
    public void supplementSubject(Subject subject) throws IOException {
        Set<RealmUser> users = subject.getPrincipals(RealmUser.class);
        Set<Principal> principals = subject.getPrincipals();
        Properties properties = getProperties();
        // In general we expect exactly one RealmUser, however we could cope with multiple
        // identities so load the groups for them all.
        for (RealmUser current : users) {
            principals.addAll(loadGroups(properties, current));
        }
    }

    private Set<RealmGroup> loadGroups(final Properties properties, final RealmUser user) {
        Set<RealmGroup> response;
        String groupString = properties.getProperty(user.getName(), "").trim();
        if (groupString.length() > 0) {
            String[] groups = groupString.split(COMMA);
            response = new HashSet<RealmGroup>(groups.length);
            for (String current : groups) {
                String cleaned = current.trim();
                if (cleaned.length() > 0) {
                    RealmGroup newGroup = new RealmGroup(realmName, cleaned);
                    SECURITY_LOGGER.tracef("Adding group '%s' for user '%s'.", newGroup, user);
                    response.add(newGroup);
                }
            }
        } else {
            SECURITY_LOGGER.tracef("No groups found for user '%s' in properties file.", user);
            response = Collections.emptySet();
        }

        return response;
    }

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            try {
                Properties groups = getProperties();

                String name = principal.getName();
                return new RealmIdentityImpl(principal, groups.getProperty(name, "").trim());
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
            private final String groups;

            private RealmIdentityImpl(final Principal principal, final String groups) {
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

                if (groups.length() > 0) {
                    String[] temp = groups.split(COMMA);
                    Set<String> groups = new HashSet<>(temp.length);

                    for (String current : temp) {
                        String cleaned = current.trim();
                        if (cleaned.length() > 0) {
                            SECURITY_LOGGER.tracef("Adding group '%s' for identity '%s'.", cleaned, principal.getName());
                            groups.add(cleaned);
                        }
                    }

                    Map<String, Set<String>> groupsAttributeMap = new HashMap<String, Set<String>>();
                    groupsAttributeMap.put("GROUPS",Collections.unmodifiableSet(groups));

                    return AuthorizationIdentity.basicIdentity(new MapAttributes(Collections.unmodifiableMap(groupsAttributeMap)));
                } else {
                    SECURITY_LOGGER.tracef("No groups found for identity '%s' in properties file.", principal.getName());
                    return AuthorizationIdentity.EMPTY;
                }
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
