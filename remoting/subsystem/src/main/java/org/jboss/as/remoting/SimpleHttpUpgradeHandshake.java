/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpUpgradeHandshake;
import io.undertow.util.FlexBase64;
import io.undertow.util.HttpString;
import org.jboss.as.remoting.logging.RemotingLogger;

/**
 * Utility class to create a server-side HTTP Upgrade handshake.
 *
 * @author Stuart Douglas
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2014 Red Hat inc.
 */
public class SimpleHttpUpgradeHandshake implements HttpUpgradeHandshake {

    private final String keyHeader;
    private final String magicNumber;
    private final String acceptHeader;

    public SimpleHttpUpgradeHandshake(String magicNumber, String keyHeader, String acceptHeader) {
        this.keyHeader = keyHeader;
        this.magicNumber = magicNumber;
        this.acceptHeader = acceptHeader;
    }

    public boolean handleUpgrade(final HttpServerExchange exchange) throws IOException {
        String secretKey = exchange.getRequestHeaders().getFirst(keyHeader);
        if (secretKey == null) {
            throw RemotingLogger.ROOT_LOGGER.upgradeRequestMissingKey();
        }
        String response = createExpectedResponse(magicNumber, secretKey);
        exchange.getResponseHeaders().put(HttpString.tryFromString(acceptHeader), response);
        return true;
    }

    private String createExpectedResponse(final String magicNumber, final String secretKey) throws IOException {
        try {
            final String concat = secretKey + magicNumber;
            final MessageDigest digest = MessageDigest.getInstance("SHA1");

            digest.update(concat.getBytes(StandardCharsets.UTF_8));
            final byte[] bytes = digest.digest();
            return FlexBase64.encodeString(bytes, false);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
