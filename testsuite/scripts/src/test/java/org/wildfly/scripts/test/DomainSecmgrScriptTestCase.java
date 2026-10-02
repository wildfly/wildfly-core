/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.Assert;
import org.wildfly.common.test.ServerHelper;

public class DomainSecmgrScriptTestCase extends ScriptTestCase {

    public DomainSecmgrScriptTestCase() {
        super("domain");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        script.start(Map.of("SECMGR", SECMGR_VALUE), ServerHelper.DEFAULT_SERVER_JAVA_OPTS);
        if (!script.waitFor(TimeoutUtil.adjust(10), TimeUnit.SECONDS)) {
            throw new TimeoutException("Script did not exit after SECMGR error. Last executed command: " + script.getLastExecutedCmd() + "\nThe server output was: \n" + script.getStdoutAsString());
        }
        Assert.assertEquals("Expected exit code 1", 1, script.exitValue());
        final var stdout = script.getStdoutAsString();
        Assert.assertTrue("Expected SECMGR removal error message for a server started with " + script.getLastExecutedCmd() + "\nThe server output was: \n" + stdout,
                stdout.contains("ERROR: The SECMGR option has been removed"));
    }
}
