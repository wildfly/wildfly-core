/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.kerberos;

import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.annotations.SaslMechanism;

/**
 * A helper implementation of {@link CreateLdapServer} annotation which allows to configure values.
 *
 * @author Josef Cacek
 */
public class ManagedCreateLdapServer extends AnnotationLiteral<CreateLdapServer> implements CreateLdapServer {

    private static final long serialVersionUID = 1L;

    /** The instance name */
    private String name;
    /** The transports to use, default to LDAP */
    private CreateTransport[] transports;
    /** The LdapServer factory */
    private Class<?> factory;
    /** The maximum size limit. */
    private long maxSizeLimit;
    /** The maximum time limit. */
    private int maxTimeLimit;
    /** Tells if anonymous access are allowed or not. */
    private boolean allowAnonymousAccess;
    /** The external keyStore file to use, default to the empty string */
    private String keyStore;
    /** The certificate password in base64, default to the empty string */
    private String certificatePassword;
    /** name of the classes implementing extended operations */
    private Class<?>[] extendedOpHandlers;
    /** supported set of SASL mechanisms */
    private SaslMechanism[] saslMechanisms;
    /** NTLM provider class, default value is an invalid class */
    private Class<?> ntlmProvider;
    /** The name of this host, validated during SASL negotiation. */
    private String saslHost;
    /** The service principal, used by GSSAPI. */
    private String saslPrincipal;

    /** The service principal, used by GSSAPI. */
    private String[] saslRealms;

    private Class<?>[] trustManagers;

    // Constructors ----------------------------------------------------------

    /**
     * Create a new ManagedCreateLdapServer.
     *
     * @param createLdapServer
     */
    public ManagedCreateLdapServer(CreateLdapServer createLdapServer) {
        name = createLdapServer.name();
        transports = createLdapServer.transports();
        factory = createLdapServer.factory();
        maxSizeLimit = createLdapServer.maxSizeLimit();
        maxTimeLimit = createLdapServer.maxTimeLimit();
        allowAnonymousAccess = createLdapServer.allowAnonymousAccess();
        keyStore = createLdapServer.keyStore();
        certificatePassword = createLdapServer.certificatePassword();
        extendedOpHandlers = createLdapServer.extendedOpHandlers();
        saslMechanisms = createLdapServer.saslMechanisms();
        ntlmProvider = createLdapServer.ntlmProvider();
        saslHost = createLdapServer.saslHost();
        saslPrincipal = createLdapServer.saslPrincipal();
        saslRealms = createLdapServer.saslRealms();
        trustManagers = createLdapServer.trustManagers();
    }

    // Public methods --------------------------------------------------------

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#name()
     */
    public String name() {
        return name;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#transports()
     */
    public CreateTransport[] transports() {
        return transports;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#factory()
     */
    public Class<?> factory() {
        return factory;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#maxSizeLimit()
     */
    public long maxSizeLimit() {
        return maxSizeLimit;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#maxTimeLimit()
     */
    public int maxTimeLimit() {
        return maxTimeLimit;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#allowAnonymousAccess()
     */
    public boolean allowAnonymousAccess() {
        return allowAnonymousAccess;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#keyStore()
     */
    public String keyStore() {
        return keyStore;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#certificatePassword()
     */
    public String certificatePassword() {
        return certificatePassword;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#extendedOpHandlers()
     */
    public Class<?>[] extendedOpHandlers() {
        return extendedOpHandlers;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#saslMechanisms()
     */
    public SaslMechanism[] saslMechanisms() {
        return saslMechanisms;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#ntlmProvider()
     */
    public Class<?> ntlmProvider() {
        return ntlmProvider;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#saslHost()
     */
    public String saslHost() {
        return saslHost;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateLdapServer#saslPrincipal()
     */
    public String saslPrincipal() {
        return saslPrincipal;
    }

    @Override
    public Class<?>[] trustManagers() {
        return trustManagers;
    }

    @Override
    public String[] saslRealms() {
        return saslRealms;
    }

    /**
     * Set the name.
     *
     * @param name The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set the transports.
     *
     * @param transports The transports to set.
     */
    public void setTransports(CreateTransport[] transports) {
        this.transports = transports;
    }

    /**
     * Set the factory.
     *
     * @param factory The factory to set.
     */
    public void setFactory(Class<?> factory) {
        this.factory = factory;
    }

    /**
     * Set the maxSizeLimit.
     *
     * @param maxSizeLimit The maxSizeLimit to set.
     */
    public void setMaxSizeLimit(long maxSizeLimit) {
        this.maxSizeLimit = maxSizeLimit;
    }

    /**
     * Set the maxTimeLimit.
     *
     * @param maxTimeLimit The maxTimeLimit to set.
     */
    public void setMaxTimeLimit(int maxTimeLimit) {
        this.maxTimeLimit = maxTimeLimit;
    }

    /**
     * Set the allowAnonymousAccess.
     *
     * @param allowAnonymousAccess The allowAnonymousAccess to set.
     */
    public void setAllowAnonymousAccess(boolean allowAnonymousAccess) {
        this.allowAnonymousAccess = allowAnonymousAccess;
    }

    /**
     * Set the keyStore.
     *
     * @param keyStore The keyStore to set.
     */
    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    /**
     * Set the certificatePassword.
     *
     * @param certificatePassword The certificatePassword to set.
     */
    public void setCertificatePassword(String certificatePassword) {
        this.certificatePassword = certificatePassword;
    }

    /**
     * Set the extendedOpHandlers.
     *
     * @param extendedOpHandlers The extendedOpHandlers to set.
     */
    public void setExtendedOpHandlers(Class<?>[] extendedOpHandlers) {
        this.extendedOpHandlers = extendedOpHandlers;
    }

    /**
     * Set the saslMechanisms.
     *
     * @param saslMechanisms The saslMechanisms to set.
     */
    public void setSaslMechanisms(SaslMechanism[] saslMechanisms) {
        this.saslMechanisms = saslMechanisms;
    }

    /**
     * Set the ntlmProvider.
     *
     * @param ntlmProvider The ntlmProvider to set.
     */
    public void setNtlmProvider(Class<?> ntlmProvider) {
        this.ntlmProvider = ntlmProvider;
    }

    /**
     * Set the saslHost.
     *
     * @param saslHost The saslHost to set.
     */
    public void setSaslHost(String saslHost) {
        this.saslHost = saslHost;
    }

    /**
     * Set the saslPrincipal.
     *
     * @param saslPrincipal The saslPrincipal to set.
     */
    public void setSaslPrincipal(String saslPrincipal) {
        this.saslPrincipal = saslPrincipal;
    }



}
