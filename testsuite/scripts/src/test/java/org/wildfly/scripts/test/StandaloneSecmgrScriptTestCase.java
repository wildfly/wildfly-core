/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wildfly.common.test.ServerHelper;

@RunWith(Parameterized.class)
public class StandaloneSecmgrScriptTestCase extends ScriptTestCase {

    @Parameter(0)
    public Map<String, String> env;

    @Parameter(1)
    public String[] args;

    @Parameter(2)
    public String expectedError;

    public StandaloneSecmgrScriptTestCase() {
        super("standalone");
    }

    @Parameters
    public static Collection<Object[]> data() {
        return List.of(
                new Object[] { Map.of("SECMGR", SECMGR_VALUE), ServerHelper.DEFAULT_SERVER_JAVA_OPTS, "ERROR: The SECMGR option has been removed" },
                new Object[] { Map.of(), new String[] { "-secmgr" }, "ERROR: The -secmgr option has been removed" },
                new Object[] { Map.of(), new String[] { "-Djava.security.manager" }, "ERROR: The use of -Djava.security.manager is not supported" },
                new Object[] { Map.of(), new String[] { "-Djava.security.manager=allow" }, "ERROR: The use of -Djava.security.manager is not supported" },
                new Object[] { Map.of("JAVA_OPTS", "-Djava.security.manager"), ServerHelper.DEFAULT_SERVER_JAVA_OPTS, "ERROR: The use of -Djava.security.manager is not supported" }
        );
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        script.start(env, args);
        if (!script.waitFor(TimeoutUtil.adjust(10), TimeUnit.SECONDS)) {
            throw new TimeoutException("Script did not exit after security manager error. Last executed command: "
                    + script.getLastExecutedCmd() + "\nThe server output was: \n" + script.getStdoutAsString());
        }
        Assert.assertEquals("Expected exit code 1", 1, script.exitValue());
        final var stdout = script.getStdoutAsString();
        Assert.assertTrue("Expected error message containing '" + expectedError + "' for a server started with "
                        + script.getLastExecutedCmd() + "\nThe server output was: \n" + stdout,
                stdout.contains(expectedError));
    }
}
