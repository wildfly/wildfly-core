package org.jboss.as.test.shared;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.test.api.Authentication;

/**
 * Class that allows for non arquillian tests to access the current server address and port, and other testsuite environment
 * properties.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class TestSuiteEnvironment {

    public static ModelControllerClient getModelControllerClient() {
        try {
            return ModelControllerClient.Factory.create(
                    InetAddress.getByName(getServerAddress()),
                    TestSuiteEnvironment.getServerPort(),
                    Authentication.getCallbackHandler()
                    );
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
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
}
