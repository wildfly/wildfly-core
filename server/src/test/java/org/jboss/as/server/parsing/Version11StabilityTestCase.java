/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.server.parsing;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.Test;

/**
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class Version11StabilityTestCase {

    /*
     * Version 11 of the management schema has been released between WildFly 18 and WildFly 19 so
     * model changes should instead be applied to version 12.
     *
     * Cosmetic changes / bug fixes can result this expected digest being updated but all further
     * changes should be to version 12.  Once version 12 is in place this test can also be removed.
     */
    private static final String EXPECTED_DIGEST = "q6esEQUOg1N+q3gfOXEEWg==";

    @Test
    public void testVersion11Modifications() throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        File schema = new File("src/main/resources/schema/wildfly-config_11_0.xsd");
        int bytesRead = 0;
        try (InputStream is = new FileInputStream(schema)) {
            int read;
            byte[] bytes = new byte[256];
            while ((read = is.read(bytes)) > 0) {
                String modified = new String(bytes, 0, read, StandardCharsets.UTF_8).replace("\r", "");
                md.update(modified.getBytes());
                bytesRead += read;
            }
        }

        byte[] digest = md.digest();
        String encoded = new String(Base64.getEncoder().encode(digest), StandardCharsets.UTF_8);

        System.out.println("Current Digest = " + encoded);
        assertEquals("Version 11 has already been included in a release.", EXPECTED_DIGEST, encoded);
    }

}
