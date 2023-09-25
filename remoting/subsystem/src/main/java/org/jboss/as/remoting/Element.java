/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum Element {
    // must be first
    UNKNOWN(null),

    // Remoting 1.0 elements in alpha order
    AUTHENTICATION_PROVIDER("authentication-provider"),
    CONNECTOR("connector"),
    ENDPOINT("endpoint"),
    FORWARD_SECRECY("forward-secrecy"),
    HTTP_CONNECTOR("http-connector"),
    INCLUDE_MECHANISMS("include-mechanisms"),
    LOCAL_OUTBOUND_CONNECTION("local-outbound-connection"),
    NO_ACTIVE("no-active"),
    NO_ANONYMOUS("no-anonymous"),
    NO_DICTIONARY("no-dictionary"),
    NO_PLAIN_TEXT("no-plain-text"),
    OPTION("option"),
    OUTBOUND_CONNECTION("outbound-connection"),
    OUTBOUND_CONNECTIONS("outbound-connections"),
    PASS_CREDENTIALS("pass-credentials"),
    POLICY("policy"),
    PROPERTIES("properties"),
    PROPERTY("property"),
    REMOTE_OUTBOUND_CONNECTION("remote-outbound-connection"),
    QOP("qop"),
    REUSE_SESSION("reuse-session"),
    SASL("sasl"),
    SERVER_AUTH("server-auth"),
    STRENGTH("strength"),
    SUBSYSTEM("subsystem"),
    WORKER_THREAD_POOL("worker-thread-pool")
    ;

    private final String name;

    Element(final String name) {
        this.name = name;
    }

    /**
     * Get the local name of this element.
     *
     * @return the local name
     */
    public String getLocalName() {
        return name;
    }

    private static final Map<String, Element> MAP;

    static {
        final Map<String, Element> map = new HashMap<String, Element>();
        for (Element element : values()) {
            final String name = element.getLocalName();
            if (name != null) map.put(name, element);
        }
        MAP = map;
    }

    public static Element forName(String localName) {
        final Element element = MAP.get(localName);
        return element == null ? UNKNOWN : element;
    }
}
