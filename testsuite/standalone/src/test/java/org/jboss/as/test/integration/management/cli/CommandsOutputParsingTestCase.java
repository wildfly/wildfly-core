package org.jboss.as.test.integration.management.cli;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * Created by joe on 9/9/15.
 */
@RunWith(WildflyTestRunner.class)
public class CommandsOutputParsingTestCase {

    @Test
    public void testCommandsOutputParsing() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--output-only")
                .addCliArgument("--commands=connect,/subsystem=logging:read-resource")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()));
        cli.executeNonInteractive();

        ModelNode node = null;

        try {
            node = ModelNode.fromString(cli.getOutput());
        } catch( IllegalArgumentException e ){
            fail("Output: '" + cli.getOutput() + "' Parsing Error: '" + e);
        }

        if(node == null){
            fail("Output: '" + cli.getOutput() + "' Null returned from parser.");
        }

        assertTrue("Output: '" + cli.getOutput() + "' Failure to read outcome->success from node: '" + node.asString() + "'", node.get("outcome").asString().equals("success"));
    }

}
