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

/**
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
@RunWith(Parameterized.class)
public class StandaloneHelpVersionScriptTestCase extends ScriptTestCase {
    private static final String SCRIPT_NAME = "standalone";

    @Parameter
    public String arg;

    public StandaloneHelpVersionScriptTestCase() {
        super(SCRIPT_NAME);
    }

    @Parameters
    public static Collection<Object> data() {
        return List.of("-v", "-V", "--version", "-h", "--help");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        script.start(null, Map.of(), new String [] {arg});
        script.waitFor(TimeoutUtil.adjust(1), TimeUnit.SECONDS);
        final var stdout = script.getStdoutAsString();
        // Contains -Xmx16m and there is only one -Xmx argument
        boolean ok = stdout.contains("-Xmx16m") && !stdout.replaceFirst("-Xmx", "").contains("-Xmx");
        Assert.assertTrue("Expected to find -Xmx16m in the JVM parameters for a server started with " + script.getLastExecutedCmd() + "\nThe server output was: \n" + stdout, ok);
        Assert.assertFalse("Did not expect to find gc.log in the JVM parameters for a server started with " + script.getLastExecutedCmd() + "\nThe server output was: \n" + stdout, stdout.contains("gc.log"));
    }
}
