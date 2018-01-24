/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.domain.management.security;

import static org.junit.Assert.assertEquals;

import java.util.regex.Matcher;

import org.junit.Test;

/**
 * A test case to verify expected username / password entries can be split using the expression in {@link PropertiesFileLoader}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PropertiesPatternMatchingTestCase {

    private void individualTest(final String entry, final boolean isValid, final String username, final String password) {
        Matcher matcher = PropertiesFileLoader.PROPERTY_PATTERN.matcher(entry);
        assertEquals("Pattern Matched", isValid, matcher.matches());
        if (isValid == false) return;
        String extractedUsername = matcher.group(1);
        String extractedPassword = matcher.group(2);

        assertEquals("Extracted Username", username, extractedUsername);
        assertEquals("Extracted Password", password, extractedPassword);
    }

    private void testEntry(final String entry, final boolean isValid, final String username, final String password) {
        individualTest(entry, isValid, username, password);
        individualTest("#" + entry, isValid, username, password);
    }

    @Test
    public void testAllEntries() {
        String[] usernameParts = new String[] { "abc", "DEF", "\\\\", ",", "\\=", "@", "-", "012", ".", "/" };
        String[] passwordParts = new String[] { "", ",", "ghi", "JKL", "\\\\", "\\=", "345" };

        for (int usernameStart = 0; usernameStart < usernameParts.length; usernameStart++) {
            for (int usernameMid = 0; usernameMid < usernameParts.length; usernameMid++) {
                for (int usernameEnd = 0; usernameEnd < usernameParts.length; usernameEnd++) {
                    for (int passwordStart = 0; passwordStart < passwordParts.length; passwordStart++) {
                        for (int passwordMid = 0; passwordMid < passwordParts.length; passwordMid++) {
                            for (int passwordEnd = 0; passwordEnd < passwordParts.length; passwordEnd++) {
                                String username = usernameParts[usernameStart] + usernameParts[usernameMid] + usernameParts[usernameEnd];
                                String password = passwordParts[passwordStart] + passwordParts[passwordMid] + passwordParts[passwordEnd];

                                testEntry(username + "=" + password, true, username, password);
                                testEntry(username + password, false, username, password);
                            }
                        }
                    }
                }
            }
        }
    }

}
