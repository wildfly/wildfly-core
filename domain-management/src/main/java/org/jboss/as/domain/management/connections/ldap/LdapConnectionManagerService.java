/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.domain.management.connections.ldap;

import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;

import java.net.URI;
import java.util.Hashtable;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.net.ssl.SSLContext;

import org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.ReferralHandling;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * The LDAP connection manager to maintain the LDAP connections.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com>Richard Opalka</a>
 */
public class LdapConnectionManagerService implements Service<LdapConnectionManager>, LdapConnectionManager {

    private static final ServiceName BASE_SERVICE_NAME = ServiceName.JBOSS.append("server", "controller", "management", "connection_manager");

    private final LdapConnectionManagerRegistry connectionManagerRegistry;
    private final String name;

    private final Consumer<LdapConnectionManager> ldapConnectionManagerConsumer;
    private final Supplier<SSLContext> fullSSLContextSupplier;
    private final Supplier<SSLContext> trustSSLContextSupplier;
    private final ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier;

    private volatile Config configuration;
    private volatile Hashtable<String, String> properties = new Hashtable<String, String>();

    LdapConnectionManagerService(final Consumer<LdapConnectionManager> ldapConnectionManagerConsumer,
                                 final Supplier<SSLContext> fullSSLContextSupplier,
                                 final Supplier<SSLContext> trustSSLContextSupplier,
                                 final ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier,
                                 final String name, final LdapConnectionManagerRegistry connectionManagerRegistry) {
        this.ldapConnectionManagerConsumer = ldapConnectionManagerConsumer;
        this.fullSSLContextSupplier = fullSSLContextSupplier;
        this.trustSSLContextSupplier = trustSSLContextSupplier;
        this.credentialSourceSupplier = credentialSourceSupplier;
        this.name = name;
        this.connectionManagerRegistry = connectionManagerRegistry;
    }

    Config setConfiguration(final String initialContextFactory, final String url, final String searchDn, final String searchCredential, final ReferralHandling referralHandling, final Set<URI> referralURIs, boolean alwaysSendClientCert) {
        Config configuration = new Config(initialContextFactory, url, searchDn, searchCredential, referralHandling, referralURIs, alwaysSendClientCert);

        try {
            return this.configuration;
        } finally {
            this.configuration = configuration;
        }
    }

    void setConfiguration(final Config configuration) {
        this.configuration = configuration;
    }

    @Override
    public LdapConnectionManager getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        try {
            context.execute(new Runnable() {
                @Override
                public void run() {
                    connectionManagerRegistry.addLdapConnectionManagerService(name, LdapConnectionManagerService.this);
                    ldapConnectionManagerConsumer.accept(LdapConnectionManagerService.this);
                    context.complete();
                }
            });
        } finally {
            context.asynchronous();
        }
    }

    @Override
    public void stop(final StopContext context) {
        try {
            context.execute(new Runnable() {
                @Override
                public void run() {
                    connectionManagerRegistry.removeLdapConnectionManagerService(name);
                    ldapConnectionManagerConsumer.accept(null);
                    context.complete();
                }
            });
        } finally {
            context.asynchronous();
        }
    }

    /*
     * Property Manipulation Methods.
     */

    synchronized void setProperty(final String name, final String value) {
        Hashtable<String, String> properties = new Hashtable<String, String>(this.properties);
        properties.put(name, value);

        this.properties = properties;
    }

    synchronized void removeProperty(final String name) {
        Hashtable<String, String> properties = new Hashtable<String, String>(this.properties);
        properties.remove(name);

        this.properties = properties;
    }

    void setPropertyImmediate(final String name, final String value) {
        properties.put(name, value);
    }


    String getName() {
        return name;
    }

    /**
     * Utility method to identify if this {@code LdapConnectionManagerService} can handle referrals to the {@link URI}
     * specified.
     *
     * @param uri - The referral {@link URI}
     * @return true if this {@code LdapConnectionManagerService} can handle the referral, false otherwise.
     */
    boolean handlesReferralFor(final URI uri) {
        // NOTE - This connection may not actually support referrals but it can support being used for referrals regardless of that.
        return (configuration.getReferralURIs().contains(uri));
    }

    /*
     *  Connection Manager Methods
     */

    @Override
    public DirContext getConnection() throws NamingException {
        return getConnection(configuration);
    }

    private DirContext getConnection(final Config configuration) throws NamingException {
        return getConnection(getFullProperties(configuration), getSSLContext(false));
    }

    @Override
    public void verifyIdentity(String bindDn, String bindCredential) throws NamingException {
        verifyIdentity(configuration, bindDn, bindCredential);
    }

    private void verifyIdentity(final Config configuration, String bindDn, String bindCredential) throws NamingException {
        Hashtable<String, String> connectionProperties = getConnectionOnlyProperties(configuration);
        connectionProperties.put(Context.SECURITY_PRINCIPAL, bindDn);
        connectionProperties.put(Context.SECURITY_CREDENTIALS, bindCredential);

        /* WFCORE-2647: originally, we always used a trust only SSLContext got via getSSLContext(true) here
         * as we did not want to authenticate using a pre-defined key in a KeyStore.
         * However, there are LDAP servers, such as OpenLDAP who expect the client cert on every request
         * and hence we had to make the setting configurable. */
        final boolean trustOnly = !configuration.isAlwaysSendClientCert();
        SECURITY_LOGGER.tracef("Using a %s SSL context to authenticate user %s", trustOnly ? "trustOnly" : "fullSSLContext", bindDn);
        DirContext context = getConnection(connectionProperties, getSSLContext(trustOnly));
        context.close();
    }

    @Override
    public LdapConnectionManager findForReferral(final URI referralUri) {
        Config config = this.configuration;
        switch (config.referralHandling) {
            case FOLLOW:
                /*
                 * For FOLLOW we are using the same existing configuration with the exception of changing the URL.
                 */
                return new LdapConnectionManager() {

                    @Override
                    public void verifyIdentity(String bindDn, String bindCredential) throws NamingException {
                        LdapConnectionManagerService.this.verifyIdentity(new Config(referralUri.toString(), configuration),
                                bindDn, bindCredential);

                    }

                    @Override
                    public DirContext getConnection() throws NamingException {
                        return LdapConnectionManagerService.this
                                .getConnection(new Config(referralUri.toString(), configuration));
                    }

                    @Override
                    public LdapConnectionManager findForReferral(URI referralUri) {
                        return LdapConnectionManagerService.this.findForReferral(referralUri);
                    }
                };
            case THROW:
                if (this.handlesReferralFor(referralUri)) {
                    return this;
                }
                for (LdapConnectionManagerService current : connectionManagerRegistry.availableServices()) {
                    if (current != null && current.handlesReferralFor(referralUri)) {
                        return current;
                    }
                }

                break;
            default:
                return null;
        }

        return null;
    }

    private DirContext getConnection(final Hashtable<String, String> properties, final SSLContext sslContext) throws NamingException {
        ClassLoader old = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            if (sslContext != null) {
                ThreadLocalSSLSocketFactory.setSSLSocketFactory(sslContext.getSocketFactory());
                WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(ThreadLocalSSLSocketFactory.class);
                properties.put("java.naming.ldap.factory.socket", ThreadLocalSSLSocketFactory.class.getName());
            }
            if (SECURITY_LOGGER.isTraceEnabled()) {
                Hashtable<String, String> logProperties;
                if (properties.containsKey(Context.SECURITY_CREDENTIALS)) {
                    logProperties = new Hashtable<String, String>(properties);
                    logProperties.put(Context.SECURITY_CREDENTIALS, "***");
                } else {
                    logProperties = properties;
                }
                SECURITY_LOGGER.tracef("Connecting to LDAP with properties (%s)", logProperties.toString());
            }

            return new InitialDirContext(properties);
        } finally {
            if (sslContext != null) {
                ThreadLocalSSLSocketFactory.removeSSLSocketFactory();
            }
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(old);
        }
    }

    private SSLContext getSSLContext(final boolean trustOnly) {
        if (trustOnly) {
            return trustSSLContextSupplier != null ? trustSSLContextSupplier.get() : null;
        }

        SSLContext sslContext = fullSSLContextSupplier != null ? fullSSLContextSupplier.get() : null;
        if (sslContext == null) {
            sslContext = trustSSLContextSupplier != null ? trustSSLContextSupplier.get() : null;
        }
        return sslContext;
    }

    private Hashtable<String, String> getConnectionOnlyProperties(final Config configuration) {
        final Hashtable<String, String> result = new Hashtable<String, String>(properties);
        result.put(Context.INITIAL_CONTEXT_FACTORY, configuration.initialContextFactory);
        result.put(Context.PROVIDER_URL, configuration.url);
        result.put(Context.REFERRAL, configuration.referralHandling.getValue());
        return result;
    }

    private Hashtable<String, String> getFullProperties(final Config configuration) {
        final Hashtable<String, String> result = getConnectionOnlyProperties(configuration);
        // These are no longer mandatory as the SSL identity of the server
        // could be used instead.
        if (configuration.searchDn != null) {
            result.put(Context.SECURITY_PRINCIPAL, configuration.searchDn);
        }
        String resolvedSearchCredential = resolveSearchCredential();
        if (resolvedSearchCredential != null) {
            result.put(Context.SECURITY_CREDENTIALS, resolvedSearchCredential);
        }

        return result;
    }

    private String resolveSearchCredential() {
        try {
            ExceptionSupplier<CredentialSource, Exception> sourceSupplier = credentialSourceSupplier;
            if (sourceSupplier != null) {
                CredentialSource cs = sourceSupplier.get();
                if (cs != null) {
                    org.wildfly.security.credential.PasswordCredential credential = cs.getCredential(org.wildfly.security.credential.PasswordCredential.class);
                    if (credential != null) {
                        ClearPassword password = credential.getPassword(ClearPassword.class);
                        if (password != null) {
                            return new String(password.getPassword());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            return configuration.searchCredential;
        }
        return configuration.searchCredential;
    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String connectionName) {
            return BASE_SERVICE_NAME.append(connectionName);
        }

        public static Supplier<LdapConnectionManager> requires(final ServiceBuilder<?> sb, final String connectionName) {
            return sb.requires(createServiceName(connectionName));
        }
    }

    static class Config {

        private Config(final String initialContextFactory, final String url, final String searchDn, final String searchCredential, final ReferralHandling referralHandling, final Set<URI> referralURIs, boolean alwaysSendClientCert) {
            this.initialContextFactory = initialContextFactory;
            this.url = url;
            this.searchDn = searchDn;
            this.searchCredential = searchCredential;
            this.referralHandling = referralHandling;
            this.referralURIs = referralURIs;
            this.alwaysSendClientCert = alwaysSendClientCert;
        }

        private Config(final String url, final Config config) {
            this.url = url;
            this.initialContextFactory = config.initialContextFactory;
            this.searchDn = config.searchDn;
            this.searchCredential = config.searchCredential;
            this.referralHandling = config.referralHandling;
            this.referralURIs = config.referralURIs;
            this.alwaysSendClientCert = config.alwaysSendClientCert;
        }

        private final String initialContextFactory;
        private final String url;
        private final String searchDn;
        private final String searchCredential;
        private final ReferralHandling referralHandling;
        private final Set<URI> referralURIs;
        private final boolean alwaysSendClientCert;

        public String getInitialContextFactory() {
            return initialContextFactory;
        }
        public String getUrl() {
            return url;
        }
        public String getSearchDn() {
            return searchDn;
        }
        public String getSearchCredential() {
            return searchCredential;
        }
        public ReferralHandling getReferralHandling() {
            return referralHandling;
        }
        public Set<URI> getReferralURIs() {
            return referralURIs;
        }
        public boolean isAlwaysSendClientCert() {
            return alwaysSendClientCert;
        }
    }

}
