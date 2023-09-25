/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common;

/**
 * Common constants for AS security tests.
 *
 * @author Josef Cacek
 */
public class SecurityTestConstants {

    public static final String SERVER_KEYSTORE = "server.keystore";
    public static final String SERVER_TRUSTSTORE = "server.truststore";
    public static final String SERVER_CRT = "server.crt";
    public static final String CLIENT_KEYSTORE = "client.keystore";
    public static final String CLIENT_TRUSTSTORE = "client.truststore";
    public static final String CLIENT_CRT = "client.crt";
    public static final String UNTRUSTED_KEYSTORE = "untrusted.keystore";
    public static final String UNTRUSTED_CRT = "untrusted.crt";

    public static final String KEYSTORE_PASSWORD = "123456";

    /**
     * A web.xml content (web-app version=3.0), which sets authentication method to BASIC.
     */
    public static final String WEB_XML_BASIC_AUTHN = "<?xml version='1.0'?>\n"
            + "<web-app xmlns='http://java.sun.com/xml/ns/javaee' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'\n"
            + "    xsi:schemaLocation='http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd'\n"
            + "    version='3.0'>\n" //
            + "  <login-config>\n" //
            + "    <auth-method>BASIC</auth-method>\n" //
            + "    <realm-name>Test realm</realm-name>\n" //
            + "  </login-config>\n" //
            + "</web-app>\n";

}
