/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron._private;

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.wildfly.common.Assert;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.security.x500.cert.acme.AcmeAccount;
import org.wildfly.security.x500.cert.acme.AcmeChallenge;
import org.wildfly.security.x500.cert.acme.AcmeClientSpi;
import org.wildfly.security.x500.cert.acme.AcmeException;

/**
 * Implementation of the <a href="https://www.ietf.org/id/draft-ietf-acme-acme-12.txt">Automatic Certificate Management
 * Environment (ACME)</a> protocol.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public final class WildFlyAcmeClient extends AcmeClientSpi {
    private static final String ACME_CHALLENGE_PREFIX = "/.well-known/acme-challenge/";
    private static final String TOKEN_REGEX = "[A-Za-z0-9_-]+";

    public AcmeChallenge proveIdentifierControl(AcmeAccount account, List<AcmeChallenge> challenges) throws AcmeException {
        Assert.checkNotNullParam("account", account);
        Assert.checkNotNullParam("challenges", challenges);
        AcmeChallenge selectedChallenge = null;
        for (AcmeChallenge challenge : challenges) {
            if (challenge.getType() == AcmeChallenge.Type.HTTP_01) {
                selectedChallenge = challenge;
                break;
            }
        }
        if (selectedChallenge == null) {
            throw ROOT_LOGGER.missingCertificateAuthorityChallenge();
        }
        // ensure the token is valid before proceeding
        String token = selectedChallenge.getToken();
        if (! token.matches(TOKEN_REGEX)) {
            throw ROOT_LOGGER.invalidCertificateAuthorityChallenge();
        }

        // respond to the http challenge
        String responseFilePath = WildFlySecurityManager.getPropertyPrivileged("jboss.home.dir", ".") + ACME_CHALLENGE_PREFIX + token;
        try (FileOutputStream fos = new FileOutputStream(responseFilePath)) {
            fos.write(selectedChallenge.getKeyAuthorization(account).getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw ROOT_LOGGER.unableToRespondToCertificateAuthorityChallenge(e, e.getLocalizedMessage());
        }
        return selectedChallenge;
    }

    public void cleanupAfterChallenge(AcmeAccount account, AcmeChallenge challenge) throws AcmeException {
        Assert.checkNotNullParam("account", account);
        Assert.checkNotNullParam("challenge", challenge);
        // ensure the token is valid before proceeding
        String token = challenge.getToken();
        if (! token.matches(TOKEN_REGEX)) {
            throw ROOT_LOGGER.invalidCertificateAuthorityChallenge();
        }

        // delete the file that was created to prove identifier control
        String responseFilePath = WildFlySecurityManager.getPropertyPrivileged("jboss.home.dir", ".") + ACME_CHALLENGE_PREFIX + token;
        File responseFile = new File(responseFilePath);
        if (responseFile.exists()) {
            responseFile.delete();
        }
    }

}
