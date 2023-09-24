/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.kerberos;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.directory.server.annotations.CreateTransport;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.junit.AssumptionViolatedException;

/**
 * Helper methods for JGSSAPI &amp; SPNEGO &amp; Kerberos testcases. It mainly helps to skip tests on configurations which
 * contains issues.
 *
 * @author Josef Cacek
 */
public final class KerberosTestUtils {

    public static final String OID_KERBEROS_V5 = "1.2.840.113554.1.2.2";
    public static final String OID_KERBEROS_V5_LEGACY = "1.2.840.48018.1.2.2";
    public static final String OID_NTLM = "1.3.6.1.4.1.311.2.2.10";
    public static final String OID_SPNEGO = "1.3.6.1.5.5.2";
    public static final String OID_DUMMY = "1.1.2.5.6.7";

    /**
     * Just a private constructor.
     */
    private KerberosTestUtils() {
        // It's OK to be empty - we don't instantiate this class.
    }

    /**
     * This method throws an {@link AssumptionViolatedException} (i.e. it skips the test-case) if the configuration is
     * unsupported for HTTP authentication with Kerberos. Configuration in this case means combination of [ hostname used | JDK
     * vendor | Java version ].
     *
     * @throws AssumptionViolatedException
     */
    public static void assumeKerberosAuthenticationSupported() throws AssumptionViolatedException {
        if (isIPV6()) {
            throw new AssumptionViolatedException(
                    "Kerberos tests are not supported on IBM Java with IPv6. Find more info in https://bugzilla.redhat.com/show_bug.cgi?id=1188632");
        }
        if (isIPV6()) {
            throw new AssumptionViolatedException(
                    "Kerberos tests are not supported when hostname is not available for tested IPv6 address. Find more info in https://issues.jboss.org/browse/WFLY-5409");
        }
    }

    /**
     * Returns true if the server bind address is an IPv6 address without canonical hostname.
     *
     * @return
     */
    private static boolean isIPV6() {
        return CoreUtils.getDefaultHost(true).indexOf(':')>=0;
    }

    /**
     * Creates login context for given {@link Krb5LoginConfiguration} and credentials and calls the {@link LoginContext#login()}
     * method on it. This method contains workaround for IBM JDK issue described in bugzilla <a
     * href="https://bugzilla.redhat.com/show_bug.cgi?id=1206177">https://bugzilla.redhat.com/show_bug.cgi?id=1206177</a>.
     *
     * @param krb5Configuration
     * @param user
     * @param pass
     * @return
     * @throws LoginException
     */
    public static LoginContext loginWithKerberos(final Krb5LoginConfiguration krb5Configuration, final String user,
            final String pass) throws LoginException {
        LoginContext lc = new LoginContext(krb5Configuration.getName(), new UsernamePasswordHandler(user, pass));
        lc.login();
        return lc;
    }

    /**
     * Fixes/replaces LDAP bind address in the CreateTransport annotation of ApacheDS.
     *
     * @param createLdapServer
     * @param address
     */
    public static void fixApacheDSTransportAddress(ManagedCreateLdapServer createLdapServer, String address) {
        final CreateTransport[] createTransports = createLdapServer.transports();
        for (int i = 0; i < createTransports.length; i++) {
            final ManagedCreateTransport mgCreateTransport = new ManagedCreateTransport(createTransports[i]);
            // localhost is a default used in original CreateTransport annotation. We use it as a fallback.
            mgCreateTransport.setAddress(address != null ? address : "localhost");
            createTransports[i] = mgCreateTransport;
        }
    }

    static class UsernamePasswordHandler implements CallbackHandler {

        private final String user;
        private final String password;

        UsernamePasswordHandler(final String user, final String password) {
            this.user = user;
            this.password = password;
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    ((NameCallback) current).setName(user);
                } else if (current instanceof PasswordCallback) {
                    ((PasswordCallback) current).setPassword(password.toCharArray());
                }
            }
        }
    }
}
