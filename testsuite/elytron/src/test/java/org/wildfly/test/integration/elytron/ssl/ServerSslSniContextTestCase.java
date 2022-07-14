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

package org.wildfly.test.integration.elytron.ssl;

import org.hamcrest.MatcherAssert;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.hamcrest.CoreMatchers.containsString;

@ServerSetup(ServerReload.SetupTask.class)
@RunWith(WildFlyRunner.class)
public class ServerSslSniContextTestCase {
    CLIWrapper cli;

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);
        // add server-ssl-sni-context
        cli.sendLine("/subsystem=elytron/key-store=exampleKeyStore:add(path=server.keystore,relative-to=jboss.server.config.dir,credential-reference={clear-text=\"keystore_password\"},type=JKS)");
        cli.sendLine("/subsystem=elytron/key-manager=exampleKeyManager:add(key-store=exampleKeyStore,alias-filter=server,credential-reference={clear-text=\"key_password\"})");
        cli.sendLine("/subsystem=elytron/server-ssl-context=exampleSslContext:add(key-manager=exampleKeyManager)");
        cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:add(default-ssl-context=exampleSslContext");
    }

    @After
    public void cleanup() throws Exception {
        removeTestResources();
        cli.close();
    }

    @Test
    public void testInvalidHostContextMapValue() {
        boolean success = cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:write-attribute(name=host-context-map,value={\"\\\\?.invalid.com\"=exampleSslContext})", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat("Wrong error message", cli.readOutput(), containsString("Invalid value of host context map"));
        success = cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:write-attribute(name=host-context-map,value={\"invalid\\\\.\\\\.example.com\"=exampleSslContext})", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat("Wrong error message", cli.readOutput(), containsString("Invalid value of host context map"));
        success = cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:write-attribute(name=host-context-map,value={\"*\\.invalid.com\"=exampleSslContext})", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat("Wrong error message", cli.readOutput(), containsString("Invalid value of host context map"));
        success = cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:write-attribute(name=host-context-map,value={\"invalid.com-\"=exampleSslContext})", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat("Wrong error message", cli.readOutput(), containsString("Invalid value of host context map"));
        success = cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:write-attribute(name=host-context-map,value={\"invalid.com\\\\.\"=exampleSslContext})", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat("Wrong error message", cli.readOutput(), containsString("Invalid value of host context map"));
    }

    @Test
    public void testValidHostContextMapValue() {
        boolean success = cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:write-attribute(name=host-context-map,value={\"..valid\\\\.example\\\\.com\"=exampleSslContext})", true);
        Assert.assertTrue(success);
        success = cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:write-attribute(name=host-context-map,value={\"valid\\\\.example\\\\.com\"=exampleSslContext})", true);
        Assert.assertTrue(success);
        success = cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:write-attribute(name=host-context-map,value={\"[^.]*\\\\.example\\\\.com\"=exampleSslContext})", true);
        Assert.assertTrue(success);
    }

    private void removeTestResources() {
        cli.sendLine("/subsystem=elytron/server-ssl-sni-context=exampleSslSniContext:remove");
        cli.sendLine("/subsystem=elytron/server-ssl-context=exampleSslContext:remove");
        cli.sendLine("/subsystem=elytron/key-manager=exampleKeyManager:remove");
        cli.sendLine("/subsystem=elytron/key-store=exampleKeyStore:remove");
    }
}
