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
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.naming.NamingException;
import javax.security.auth.Subject;

import org.jboss.as.core.security.RealmGroup;
import org.jboss.as.core.security.RealmUser;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionManager;
import org.jboss.as.domain.management.security.BaseLdapGroupSearchResource.GroupName;
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
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * A {@link SubjectSupplemental} for loading a users groups from LDAP.
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class LdapSubjectSupplementalService implements Service, SubjectSupplementalService {

    private final Consumer<SubjectSupplementalService> subjectSupplementalServiceConsumer;
    private final Supplier<LdapConnectionManager> connectionManagerSupplier;
    private final Supplier<LdapSearcherCache<LdapEntry, String>> userSearcherSupplier;
    private final Supplier<LdapSearcherCache<LdapEntry[], LdapEntry>> groupSearcherSupplier;

    private LdapSearcherCache<LdapEntry, String> userSearcher;
    private LdapSearcherCache<LdapEntry[], LdapEntry> groupSearcher;

    protected final int searchTimeLimit = 10000; // TODO - Maybe make configurable.

    private final String realmName;
    private final boolean shareConnection;
    private final boolean forceUserDnSearch;
    private final boolean iterative;
    private final GroupName groupName;

    LdapSubjectSupplementalService(final Consumer<SubjectSupplementalService> subjectSupplementalServiceConsumer,
                                   final Supplier<LdapConnectionManager> connectionManagerSupplier,
                                   final Supplier<LdapSearcherCache<LdapEntry, String>> userSearcherSupplier,
                                   final Supplier<LdapSearcherCache<LdapEntry[], LdapEntry>> groupSearcherSupplier,
                                   final String realmName, final boolean shareConnection, final boolean forceUserDnSearch,
                                   final boolean iterative, final GroupName groupName) {
        this.subjectSupplementalServiceConsumer = subjectSupplementalServiceConsumer;
        this.connectionManagerSupplier = connectionManagerSupplier;
        this.userSearcherSupplier = userSearcherSupplier;
        this.groupSearcherSupplier = groupSearcherSupplier;
        this.realmName = realmName;
        this.shareConnection = shareConnection;
        this.forceUserDnSearch = forceUserDnSearch;
        this.iterative = iterative;
        this.groupName = groupName;
    }

    public void start(final StartContext context) {
        userSearcher = userSearcherSupplier != null ? userSearcherSupplier.get() : null;
        groupSearcher = groupSearcherSupplier.get();

        if (SECURITY_LOGGER.isTraceEnabled()) {
            SECURITY_LOGGER.tracef("LdapSubjectSupplementalService realmName=%s", realmName);
            SECURITY_LOGGER.tracef("LdapSubjectSupplementalService shareConnection=%b", shareConnection);
            SECURITY_LOGGER.tracef("LdapSubjectSupplementalService forceUserDnSearch=%b", forceUserDnSearch);
            SECURITY_LOGGER.tracef("LdapSubjectSupplementalService iterative=%b", iterative);
            SECURITY_LOGGER.tracef("LdapSubjectSupplementalService groupName=%s", groupName);
        }
        subjectSupplementalServiceConsumer.accept(this);
    }

    public void stop(final StopContext context) {
        subjectSupplementalServiceConsumer.accept(null);
        groupSearcher = null;
        userSearcher = null;
    }

    /*
     * SubjectSupplementalService Method
     */

    public SubjectSupplemental getSubjectSupplemental(Map<String, Object> sharedState) {
        return new LdapSubjectSupplemental(sharedState);
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    /*
     * SubjectSupplementalMethods
     */

    class LdapSubjectSupplemental implements SubjectSupplemental {

        private final LdapGroupSearcher ldapGroupSearcher;

        protected LdapSubjectSupplemental(final Map<String, Object> sharedState) {
            this.ldapGroupSearcher = new LdapGroupSearcher(sharedState);
        }

        @Override
        public void supplementSubject(Subject subject) throws IOException {
            final ClassLoader old = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(LdapSubjectSupplemental.class);
            try {
                Set<RealmUser> users = subject.getPrincipals(RealmUser.class);
                Set<Principal> principals = subject.getPrincipals();

                Set<RealmGroup> set = new HashSet<>();
                Set<String> result = new HashSet<>();
                for (RealmUser user : users) {
                    String name = user.getName();
                    result.add(name);
                }
                for (String s : ldapGroupSearcher.loadGroups(result)) {
                    RealmGroup realmGroup = new RealmGroup(realmName, s);
                    set.add(realmGroup);
                }
                principals.addAll(set);
            } finally {
                WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(old);
            }

        }

    }

    private class LdapGroupSearcher {

        private final Set<LdapEntry> searchedPerformed = new HashSet<LdapEntry>();
        private final Map<String, Object> sharedState;

        protected LdapGroupSearcher(final Map<String, Object> sharedState) {
            this.sharedState = sharedState;
        }

        Set<String> loadGroups(Set<String> users) throws IOException {
            Set<String> groups = new HashSet<>();

            final LdapConnectionHandler connectionHandler;
            if (sharedState.containsKey(LdapConnectionHandler.class.getName())) {
                SECURITY_LOGGER.trace("Using existing LdapConnectionHandler from shared state.");
                connectionHandler = (LdapConnectionHandler) sharedState.remove(LdapConnectionHandler.class.getName());
            } else {
                SECURITY_LOGGER.trace("Creating new LdapConnectionHandler.");
                connectionHandler = LdapConnectionHandler.newInstance(connectionManagerSupplier.get());
            }
            try {
                // In general we expect exactly one RealmUser, however we could cope with multiple
                // identities so load the groups for them all.
                for (String current : users) {
                    SECURITY_LOGGER.tracef("Loading groups for '%s'", current);
                    groups.addAll(loadGroups(current, connectionHandler));
                }

                return groups;
            } catch (Exception e) {
                SECURITY_LOGGER.trace("Failure supplementing Subject", e);
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new IOException(e);
            } finally {
                connectionHandler.close();
            }
        }

        private Set<String> loadGroups(String user, LdapConnectionHandler connectionHandler) throws IOException, NamingException {
            LdapEntry entry = null;
            if (forceUserDnSearch == false && sharedState.containsKey(LdapEntry.class.getName())) {
                entry = (LdapEntry) sharedState.get(LdapEntry.class.getName());
                SECURITY_LOGGER.tracef("Loaded from sharedState '%s'", entry);
            }
            if (entry == null || user.equals(entry.getSimpleName())==false) {
                entry = userSearcher.search(connectionHandler, user).getResult();
                SECURITY_LOGGER.tracef("Performed userSearch '%s'", entry);
            }

            return loadGroups(entry, connectionHandler);
        }

        private Set<String> loadGroups(LdapEntry entry, LdapConnectionHandler connectionHandler) throws IOException, NamingException {
            Set<String> realmGroups = new HashSet<>();

            Stack<LdapEntry[]> entries = new Stack<LdapEntry[]>();
            entries.push(loadGroupEntries(entry, connectionHandler));
            while (entries.isEmpty() == false) {
                LdapEntry[] found = entries.pop();
                for (LdapEntry current : found) {
                    String group = groupName == GroupName.SIMPLE ? current.getSimpleName() : current.getDistinguishedName();
                    SECURITY_LOGGER.tracef("Adding RealmGroup '%s'", group);
                    realmGroups.add(group);
                    if (iterative) {
                        SECURITY_LOGGER.tracef("Performing iterative load for %s", current);
                        entries.push(loadGroupEntries(current, connectionHandler));
                    }
                }
            }

            return realmGroups;
        }

        private LdapEntry[] loadGroupEntries(LdapEntry entry, LdapConnectionHandler connectionHandler) throws IOException, NamingException {
            if (searchedPerformed.add(entry) == false) {
                SECURITY_LOGGER.tracef("A search has already been performed for %s", entry);
                return new LdapEntry[0];
            }

            return groupSearcher.search(connectionHandler, entry).getResult();
        }

    }

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {

            return new RealmIdentityImpl(principal, SecurityRealmService.SharedStateSecurityRealm.getSharedState());
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

            private final LdapGroupSearcher ldapGroupSearcher;
            private final Principal principal;
            private Set<String> groups;

            public RealmIdentityImpl(final Principal principal, final Map<String, Object> sharedState) {
                ldapGroupSearcher = new LdapGroupSearcher(sharedState != null ? sharedState : new HashMap<>());
                this.principal = principal;
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName);
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName, parameterSpec);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return null;
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getEvidenceVerifySupport(evidenceType, algorithmName);
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {
                return false;
            }

            @Override
            public boolean exists() throws RealmUnavailableException {
                Set<String> groups = getGroups();
                return groups != null && groups.size() > 0;
            }

            @Override
            public AuthorizationIdentity getAuthorizationIdentity() throws RealmUnavailableException {
                Set<String> groups = getGroups();
                if (groups != null && groups.size() > 0) {
                    Map<String, Set<String>> groupsAttributeMap = new HashMap<String, Set<String>>();
                    groupsAttributeMap.put("GROUPS",Collections.unmodifiableSet(groups));

                    return AuthorizationIdentity.basicIdentity(new MapAttributes(Collections.unmodifiableMap(groupsAttributeMap)));
                } else {
                    SECURITY_LOGGER.tracef("No groups found for identity '%s' in LDAP file.", principal.getName());
                    return AuthorizationIdentity.EMPTY;
                }
            }

            private synchronized Set<String> getGroups() throws RealmUnavailableException {
                if (groups == null) {
                    final ClassLoader old = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(LdapSubjectSupplemental.class);
                    try {
                        groups = ldapGroupSearcher.loadGroups(Collections.singleton(principal.getName()));
                    } catch (IOException e) {
                        throw new RealmUnavailableException(e);
                    } finally {
                        WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(old);
                    }
                }

                return groups;
            }

        }
    }

    public static final class ServiceUtil {

        private static final String SERVICE_SUFFIX = "ldap-authorization";

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealm.ServiceUtil.createServiceName(realmName).append(SERVICE_SUFFIX);
        }

    }

}
