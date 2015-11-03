/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */

package org.jboss.as.domain.management.security;


import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.jboss.as.domain.management.SubjectIdentity;
import org.jboss.msc.service.StartException;
import org.junit.Assert;
import org.junit.Test;

/**
 * WFCORE-820
 *
 * Tests order in which keytabs are selected.
 *
 * Excerpt from XSD documentation (wildfly-config_4_0.xsd):
 *
 * <quote>
 * At the time authentication is going to be handled the keytab will be selected as follows: -
 *  1 - Iterate the list of keytabs and identity one where the for-hosts attribute contains an entry matching protocol/hostname.
 *  2 - Iterate the list of keytabs and identify one where the name of the principal matches matches protocol/hostname.
 *  3 - Iterate the list of keytabs and identity one where the for-hosts attribute contains an entry matching hostname.
 *  4 - Iterate the list of keytabs and identify one where the hostname portion of the principal matches the hostname of the request.
 *  5 - Use the keytab where for-hosts is set to '*'.
 * </quote>
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class KeytabIdentityFactoryServiceTestCase {

    private static final SubjectIdentity RIGHT_SUBJECT_IDENTITY = new MockSubjectIdentity();
    private static final SubjectIdentity WRONG_SUBJECT_IDENTITY = new MockSubjectIdentity();

    /**
     * Case 1: for-hosts contains protocol/hostname
     */
    @Test
    public void testForHostWithProto() throws StartException {
        KeytabIdentityFactoryService service = new KeytabIdentityFactoryService();
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "HTTP/EXAMPLE", RIGHT_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "EXAMPLE", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/EXAMPLE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("PROTO/EXAMPLE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "*", WRONG_SUBJECT_IDENTITY));
        service.start(null);

        SubjectIdentity subjectIdentity = service.getSubjectIdentity("HTTP", "EXAMPLE");

        Assert.assertNotNull(subjectIdentity);
        Assert.assertTrue("Different keytab used then expected.", subjectIdentity == RIGHT_SUBJECT_IDENTITY);
    }

    /**
     * Case 3: for-hosts contains hostname
     */
    @Test
    public void testForHostWithoutProto() throws StartException {
        KeytabIdentityFactoryService service = new KeytabIdentityFactoryService();
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "HTTP/SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "EXAMPLE", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/EXAMPLE@SOMETHING.COM", "SOMEHOST", RIGHT_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("PROTO/EXAMPLE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "*", WRONG_SUBJECT_IDENTITY));
        service.start(null);

        SubjectIdentity subjectIdentity = service.getSubjectIdentity("HTTP", "EXAMPLE");

        Assert.assertNotNull(subjectIdentity);
        Assert.assertEquals("Different keytab used then expected.", subjectIdentity, RIGHT_SUBJECT_IDENTITY);
    }

    /**
     * Case 2: principal name matches protocol/hostname
     */
    @Test
    public void testPrincipalWithProto() throws StartException {
        KeytabIdentityFactoryService service = new KeytabIdentityFactoryService();
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "HTTP/SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/EXAMPLE@SOMETHING.COM", "SOMEHOST", RIGHT_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("PROTO/EXAMPLE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "*", WRONG_SUBJECT_IDENTITY));
        service.start(null);

        SubjectIdentity subjectIdentity = service.getSubjectIdentity("HTTP", "EXAMPLE");

        Assert.assertNotNull(subjectIdentity);
        Assert.assertEquals("Different keytab used then expected.", subjectIdentity, RIGHT_SUBJECT_IDENTITY);
    }

    /**
     * Case 4: principal hostname matches hostname
     */
    @Test
    public void testPrincipalWithoutProto() throws StartException {
        KeytabIdentityFactoryService service = new KeytabIdentityFactoryService();
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "HTTP/SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("PROTO/EXAMPLE@SOMETHING.COM", "SOMEHOST", RIGHT_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "*", WRONG_SUBJECT_IDENTITY));
        service.start(null);

        SubjectIdentity subjectIdentity = service.getSubjectIdentity("HTTP", "EXAMPLE");

        Assert.assertNotNull(subjectIdentity);
        Assert.assertEquals("Different keytab used then expected.", subjectIdentity, RIGHT_SUBJECT_IDENTITY);
    }

    /**
     * Case 5: default
     */
    @Test
    public void testDefault() throws StartException {
        KeytabIdentityFactoryService service = new KeytabIdentityFactoryService();
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "HTTP/SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("PROTO/ANYVALUE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "*", RIGHT_SUBJECT_IDENTITY));
        service.start(null);

        SubjectIdentity subjectIdentity = service.getSubjectIdentity("HTTP", "EXAMPLE");

        Assert.assertNotNull(subjectIdentity);
        Assert.assertEquals("Different keytab used then expected.", subjectIdentity, RIGHT_SUBJECT_IDENTITY);
    }

    /**
     * Host name should be case insensitive according to The Kerberos Network Authentication Service (V5)
     */
    @Test
    public void testHostNameCaseInSensitive() throws StartException {
        KeytabIdentityFactoryService service = new KeytabIdentityFactoryService();
        service.getKeytabInjector().inject(createKeytabService("HTTP/localhost@SOMETHING.COM", "SOMEHOST", RIGHT_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("HTTP/ANYVALUE@SOMETHING.COM", "SOMEHOST", WRONG_SUBJECT_IDENTITY));
        service.getKeytabInjector().inject(createKeytabService("PROTO/ANYVALUE@SOMETHING.COM", "localhost", WRONG_SUBJECT_IDENTITY));
        service.start(null);

        SubjectIdentity subjectIdentity = service.getSubjectIdentity("HTTP", "LocalHost");
        service.stop(null);

        Assert.assertNotNull(subjectIdentity);
        Assert.assertTrue("Different keytab used then expected.", subjectIdentity == RIGHT_SUBJECT_IDENTITY);
    }

    /**
     * Creates mocked KeytabService
     */
    private KeytabService createKeytabService(String principal, String forHost, final SubjectIdentity subjectIdentity) {
        return new KeytabService(principal, null, null, new String[]{forHost}, true) {
            @Override
            public SubjectIdentity createSubjectIdentity(boolean isClient) throws LoginException {
                return subjectIdentity;
            }
        };
    }

    private static class MockSubjectIdentity implements SubjectIdentity {
        @Override
        public Subject getSubject() {
            return null;
        }

        @Override
        public void logout() {
        }
    }
}
