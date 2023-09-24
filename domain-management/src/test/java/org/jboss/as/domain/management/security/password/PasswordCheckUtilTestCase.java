/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.password;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.junit.Test;

/**
 * @author <a href="mailto:g.grossetie@gmail.com">Guillaume Grossetie</a>
 */
public class PasswordCheckUtilTestCase {

    @Test
    public void testInitRestriction() throws URISyntaxException, IOException {
        final URL resource = PasswordCheckUtilTestCase.class.getResource("add-user.properties");

        File addUser = new File(resource.getFile());
        final List<PasswordRestriction> passwordRestrictions = PasswordCheckUtil.create(addUser).getPasswordRestrictions();
        assertPasswordRejected(passwordRestrictions, "ggrossetie", "Password must not match username");
        assertPasswordRejected(passwordRestrictions, "abc12", "Password must have at least 8 characters");
        assertPasswordRejected(passwordRestrictions, "abcdefgh", "Password must have at least 2 digits");
        assertPasswordRejected(passwordRestrictions, "abcdefgh1", "Password must have at least 2 digits");
        assertPasswordRejected(passwordRestrictions, "root", "Password must not be 'root'");
        assertPasswordRejected(passwordRestrictions, "admin", "Password must not be 'admin'");
        assertPasswordRejected(passwordRestrictions, "administrator", "Password must not be 'administrator'");
        assertPasswordAccepted(passwordRestrictions, "abcdefgh12", "Password is valid");
    }

    private void assertPasswordRejected(List<PasswordRestriction> passwordRestrictions, String password, String expectedMessage) {
        boolean accepted = true;
        for (PasswordRestriction passwordRestriction : passwordRestrictions) {
            try {
                passwordRestriction.validate("ggrossetie", password);
            } catch (PasswordValidationException pve) {
                accepted = false;
            }
        }
        assertFalse(expectedMessage, accepted);
    }

    private void assertPasswordAccepted(List<PasswordRestriction> passwordRestrictions, String password, String expectedMessage) {
        boolean accepted = true;
        for (PasswordRestriction passwordRestriction : passwordRestrictions) {
            try {
                passwordRestriction.validate("ggrossetie", password);
            } catch (PasswordValidationException pve) {
                accepted = false;
            }
        }
        assertTrue(expectedMessage, accepted);
    }
}
