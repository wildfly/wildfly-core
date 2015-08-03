/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.as.cli.impl;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.scriptsupport.CLI;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Demonstrates WFCORE-774 memory leak in CLI
 *
 * Instructions:
 *
 * 1) run standalone EAP on default ports
 * 2) un-ignore this test
 * 3) go to $EAP_HOME/cli/
 * 4) run the test with `MAVEN_OPTS="-Xmx64m -XX:+HeapDumpOnOutOfMemoryError" mvn test -Dtest=CommandContextImplTestCase -DforkMode=never`
 *
 * With broken version, test should fail with 'Exception in provider: Java heap space' message before reaching
 * 10000 iterations. Fixed version should complete successfully.
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
@Ignore // demonstration only, not to be run during build process
public class CommandContextImplTestCase {

    @Test
    public void testCommandContext() throws CliInitializationException {
        CLI cli = CLI.newInstance();
        for (int i = 0; i < 10000; i++) {
            cli.connect();
            cli.disconnect();
            System.out.println(i);
        }
    }

}
