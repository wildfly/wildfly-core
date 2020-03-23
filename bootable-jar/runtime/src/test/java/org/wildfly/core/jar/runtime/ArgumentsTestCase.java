/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
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
package org.wildfly.core.jar.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class ArgumentsTestCase {

    @Test
    public void test() throws Exception {
        {
            String[] args = {};
            Arguments arguments = Arguments.parseArguments(Arrays.asList(args));
            assertNull(arguments.getDeployment());
            assertTrue(arguments.getServerArguments().isEmpty());
            assertFalse(arguments.isHelp());
            assertFalse(arguments.isVersion());
        }

        {
            Path config = Files.createTempFile(null, null);
            Path deployment = Files.createTempFile(null, ".war");
            try {
                String[] args = {"--version", "--help",
                    "--deployment=" + deployment
                };
                Arguments arguments = Arguments.parseArguments(Arrays.asList(args));
                assertEquals(arguments.getDeployment(), deployment);
                assertEquals(1, arguments.getServerArguments().size());
                assertTrue(arguments.isHelp());
                assertTrue(arguments.isVersion());
            } finally {
                Files.delete(deployment);
                Files.delete(config);
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--foo"};
                Arguments arguments = Arguments.parseArguments(Arrays.asList(args));
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--deployment=foo"};
                Arguments arguments = Arguments.parseArguments(Arrays.asList(args));
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }
    }

}
