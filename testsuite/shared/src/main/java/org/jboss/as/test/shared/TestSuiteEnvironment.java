/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.shared;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.wildfly.test.api.Authentication;

/**
 * Class that allows for non arquillian tests to access the current server address and port, and other testsuite environment
 * properties.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class TestSuiteEnvironment {
    private static final boolean IS_WINDOWS;
    private static final boolean IS_IBM_JVM;
    private static final boolean IS_J9_JVM;

    static {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        IS_WINDOWS = os.contains("win");
        IS_IBM_JVM = System.getProperty("java.vendor").startsWith("IBM");
        IS_J9_JVM = System.getProperty("java.vendor").contains("OpenJ9")
                    || System.getProperty("java.vm.vendor").contains("OpenJ9") || IS_IBM_JVM;
    }

    public static ModelControllerClient getModelControllerClient() {
        return getModelControllerClient(null);
    }

    /**
     * Creates a client based on the {@linkplain #getServerAddress() server address} and
     * {@linkplain #getServerPort() port}. If the {@code authConfigUri} is not {@code null} the it will be used to
     * authenticate the client.
     *
     * @param authConfigUri the path too the authentication configuration or {@code null}
     *
     * @return the client
     */
    public static ModelControllerClient getModelControllerClient(final URI authConfigUri) {
        return getModelControllerClient(authConfigUri, getServerAddress(), getServerPort());
    }

    /**
     * Creates a client based on the {@linkplain #getServerAddress() server address} and
     * {@linkplain #getServerPort() port}. If the {@code authConfigUri} is not {@code null} the it will be used to
     * authenticate the client.
     *
     * @param authConfigUri the path too the authentication configuration or {@code null}
     * @param address of the server
     * @param port the management port of the server
     *
     * @return the client
     */
    public static ModelControllerClient getModelControllerClient(final URI authConfigUri, String address, int port) {
        final ModelControllerClientConfiguration.Builder builder = new ModelControllerClientConfiguration.Builder()
                .setHostName(address)
                .setPort(port);
        if (authConfigUri == null) {
            builder.setHandler(Authentication.getCallbackHandler());
        } else {
            builder.setAuthenticationConfigUri(authConfigUri);
        }
        return ModelControllerClient.Factory.create(builder.build());
    }

    public static String getJavaPath() {
        String home = System.getenv("JAVA_HOME");
        if(home == null) {
            home = getSystemProperty("java.home");
        }
        if(home != null) {
            return home + java.io.File.separator + "bin" + java.io.File.separator + "java";
        }
        return "java";
    }

    /**
     * Returns the JBoss home directory or {@code null} if it could not be found.
     * <p>
     * The following is the order the directory is resolved:
     * <ol>
     *     <li>The {@code jboss.dist} system property is checked</li>
     *     <li>The {@code jboss.home} system property is checked</li>
     *     <li>The {@code JBOSS_HOME} environment variable is checked</li>
     * </ol>
     * </p>
     * @return the JBoss home directory or {@code null} if it could not be resolved
     */
    public static String getJBossHome() {
        String jbossHome = getSystemProperty("jboss.dist");
        if (jbossHome == null) {
            jbossHome = getSystemProperty("jboss.home");
        }
        return jbossHome == null ? System.getenv("JBOSS_HOME") : jbossHome;
    }

    public static String getSystemProperty(String name, String def) {
        return System.getProperty(name, def);
    }

    public static String getSystemProperty(String name) {
        return System.getProperty(name);
    }

    public static void setSystemProperty(String name, String value) {
        System.setProperty(name, value);
    }

    public static void clearSystemProperty(String name) {
        System.clearProperty(name);
    }

    public static String getTmpDir() {
        return getSystemProperty("java.io.tmpdir");
    }

    /**
     * @return The server port for node0
     */
    public static int getServerPort() {
        //this here is just fallback logic for older testsuite code that wasn't updated to newer property names
        return Integer.getInteger("management.port", Integer.getInteger("as.managementPort", 9990));
    }

    /**
     * @return The server address of node0
     */
    public static String getServerAddress() {
        String address = System.getProperty("management.address");
        if (address==null){
            address = System.getProperty("node0");
        }
        if (address!=null){
            return formatPossibleIpv6Address(address);
        }
        return "localhost";
    }

    /**
     * @param nodeName of the server
     *
     * @return The server address of nodeName
     */
    public static String getServerAddress(String nodeName) {
        String address = System.getProperty(nodeName);

        if (address!=null){
            return formatPossibleIpv6Address(address);
        }
        return "localhost";
    }

    /**
     * @return The server address of node1
     */
    public static String getServerAddressNode1() {
        String address = System.getProperty("node1");
        if (address != null){
            return formatPossibleIpv6Address(address);
        }
        return "localhost";
    }

    /**
     * @return The ipv6 arguments that should be used when launching external java processes, such as the application client
     */
    public static String getIpv6Args() {
        if (System.getProperty("ipv6") == null) {
            return " -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false ";
        }
        return " -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true ";
    }

    /**
     *
     */
    public static void getIpv6Args(List<String> command) {
        if (System.getProperty("ipv6") == null) {
            command.add("-Djava.net.preferIPv4Stack=true");
            command.add("-Djava.net.preferIPv6Addresses=false");
        } else {
            command.add("-Djava.net.preferIPv4Stack=false");
            command.add("-Djava.net.preferIPv6Addresses=true");
        }
    }

    public static String formatPossibleIpv6Address(String address) {
        if (address == null) {
            return address;
        }
        if (!address.contains(":")) {
            return address;
        }
        if (address.startsWith("[") && address.endsWith("]")) {
            return address;
        }
        return "[" + address + "]";
    }

    public static String getSecondaryTestAddress(final boolean useCanonicalHost) {
        String address = System.getProperty("secondary.test.address");
        if (StringUtils.isBlank(address)) {
            address = getServerAddress();
        }
        if (useCanonicalHost) {
            address = StringUtils.strip(address, "[]");
        }
        return address;
    }

    /**
     * Creates an HTTP url with the {@link TestSuiteEnvironment#getHttpAddress() address} and {@link
     * TestSuiteEnvironment#getHttpPort() port}.
     *
     * @return the URL
     *
     * @throws java.net.MalformedURLException if an unknown protocol is specified
     * @see java.net.URL#URL(String, String, int, String)
     */
    public static URL getHttpUrl() throws MalformedURLException {
        return new URL("http", getHttpAddress(), getHttpPort(), "");
    }

    /**
     * Gets the address used as the binding address for HTTP.
     * <p/>
     * The system properties are checked in the following order:
     * <ul>
     * <li>{@code jboss.bind.address}</li>
     * <li>{@code management.address}</li>
     * <li>{@code node0</li>
     * </ul>
     * <p/>
     * If neither system property is set {@code 0.0.0.0} is returned.
     *
     * @return the address for HTTP to bind to
     */
    public static String getHttpAddress() {
        String address = getSystemProperty("jboss.bind.address");
        if (address == null) {
            address = getSystemProperty("management.address");
            if (address == null) {
                address = getSystemProperty("node0");
            }
        }
        return address == null ? "0.0.0.0" : formatPossibleIpv6Address(address);
    }

    /**
     * Gets the port used to bind to.
     * <p/>
     * Checks the system property {@code jboss.http.port} returning {@code 8080} by default.
     *
     * @return the binding port
     */
    public static int getHttpPort() {
        return Integer.parseInt(getSystemProperty("jboss.http.port", "8080"));
    }

    /**
     * Indicates whether or not the OS is Windows.
     *
     * @return {@code true} if the OS is Windows, otherwise {@code false}
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Indicates whether or not this is a IBM JVM.
     *
     * @return {@code true} if this is a IBM JVM, otherwise {@code false}
     *
     * @see #isJ9Jvm()
     */
    public static boolean isIbmJvm() {
        return IS_IBM_JVM;
    }

    /**
     * Indicates whether or not this is an Eclipse OpenJ9 or IBM J9 JVM.
     *
     * @return {@code true} if this is an Eclipse OpenJ9 or IBM J9 JVM, otherwise {@code false}
     */
    public static boolean isJ9Jvm() {
        return IS_J9_JVM;
    }
}
