/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

/**
 * @author Emanuel Muckenhuber
 */
interface CommonAttributes {

    String AUTHENTICATION_CONTEXT = "authentication-context";
    String AUTHENTICATION_PROVIDER = "authentication-provider";
    String CONNECTOR = "connector";
    String CONNECTOR_REF = "connector-ref";
    String FORWARD_SECRECY = "forward-secrecy";
    String HTTP_AUTHENTICATION_FACTORY = "http-authentication-factory";
    String HTTP_CONNECTOR = "http-connector";
    String INCLUDE_MECHANISMS = "include-mechanisms";
    String LOCAL_OUTBOUND_CONNECTION = "local-outbound-connection";
    String NAME = "name";
    String NO_ACTIVE = "no-active";
    String NO_ANONYMOUS = "no-anonymous";
    String NO_DICTIONARY = "no-dictionary";
    String NO_PLAIN_TEXT = "no-plain-text";
    String OUTBOUND_CONNECTION = "outbound-connection";
    String OUTBOUND_SOCKET_BINDING_REF = "outbound-socket-binding-ref";
    String PASS_CREDENTIALS = "pass-credentials";
    String POLICY = "policy";
    String PROPERTIES = "properties";
    String PROPERTY = "property";
    String PROTOCOL = "protocol";
    String QOP = "qop";
    String REMOTE_OUTBOUND_CONNECTION = "remote-outbound-connection";
    String REUSE_SESSION= "reuse-session";
    String SASL = "sasl";
    String SASL_AUTHENTICATION_FACTORY = "sasl-authentication-factory";
    String SASL_POLICY = "sasl-policy";
    String SASL_PROTOCOL = "sasl-protocol";
    String SECURITY = "security";
    String SECURITY_REALM = "security-realm";
    String SERVER_AUTH = "server-auth";
    String SERVER_NAME = "server-name";
    String SOCKET_BINDING = "socket-binding";
    String SSL_CONTEXT = "ssl-context";
    String STRENGTH = "strength";
    String SUBSYSTEM = "subsystem";
    String THREAD_POOL = "thread-pool";
    String URI = "uri";
    String USERNAME = "username";
    String VALUE = "value";
    String WORKER = "worker";
}
