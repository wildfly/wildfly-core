/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.logging;

import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Tests if the server starts when the process UUID is invalid or removed.
 * Tests if the correct warning messages are logged.
 *
 * @author <a href="mailto:dcihak@redhat.com">Daniel Cihak</a>
 * @see <a href="https://issues.redhat.com/browse/WFCORE-6290">WFCORE-6290</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ProcessUuidTestCase extends AbstractLoggingTestCase {

    @Inject
    protected static ServerController container;

    @After
    public void stopContainer() {
        container.stop();
    }

    /**
     * Server should start with the invalid process UUID.
     *
     * @throws Exception
     */
    @Test
    public void testInvalidProcessUuid() throws Exception {
        FileUtils.write(this.getProcessUuidFile(), "INVALID_UUID", StandardCharsets.UTF_8);

        container.start();
        Path serverLog = AbstractLoggingTestCase.getAbsoluteLogFilePath("server.log");
        AbstractLoggingTestCase.checkLogs("WFLYCTL0501", serverLog, true);
    }

    /**
     * When the process UUID is removed new one should be generated before the server starts.
     *
     * @throws Exception
     */
    @Test
    public void testEmptyProcessUuid() throws Exception {
        FileUtils.write(this.getProcessUuidFile(), "", StandardCharsets.UTF_8);

        container.start();
        Path serverLog = AbstractLoggingTestCase.getAbsoluteLogFilePath("server.log");
        AbstractLoggingTestCase.checkLogs("WFLYCTL0500", serverLog, true);
        container.stop();
    }

    private File getProcessUuidFile() {
        String home = System.getProperty("jboss.home");
        File kernelDirectory = new File(home, "standalone/data/kernel");
        return new File(kernelDirectory, File.separator + "process-uuid");
    }
}