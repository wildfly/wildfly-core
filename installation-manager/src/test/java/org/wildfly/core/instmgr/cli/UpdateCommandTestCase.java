package org.wildfly.core.instmgr.cli;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;

import java.util.ArrayList;
import java.util.List;

public class UpdateCommandTestCase {

    private final CLICommandInvocation invocation = Mockito.mock(CLICommandInvocation.class);

    @Test
    public void testPrintManifestUpdateResult() {
        ArrayList<ModelNode> list = new ArrayList<>();

        ModelNode node = new ModelNode();
        node.get(InstMgrConstants.CHANNEL_NAME).set("channel-0");
        node.get(InstMgrConstants.MANIFEST_LOCATION).set("org.wildfly.channels:wildfly-ee");
        node.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).set("1.0.0");
        node.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).set("1.0.1");
        list.add(node);

        node = new ModelNode();
        node.get(InstMgrConstants.CHANNEL_NAME).set("channel-1");
        node.get(InstMgrConstants.MANIFEST_LOCATION).set("org.wildfly.channels:wildfly-ee-something-extra");
        node.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).set("1.0.000");
        node.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).set("1.0.001");
        list.add(node);

        UpdateCommand.printManifestUpdatesResult(invocation, list);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(invocation, Mockito.atLeastOnce()).println(captor.capture());
        captor.getAllValues().forEach(System.out::println); // print to check formatting visually

        List<String> shortenedLines = captor.getAllValues().stream()
                .map(line -> line.replaceAll(" [ ]+", " ").trim()) // replace multiple spaces with single space
                .toList();
        Assert.assertTrue(shortenedLines.contains("channel-0 org.wildfly.channels:wildfly-ee 1.0.0 ==> 1.0.1"));
        Assert.assertTrue(shortenedLines.contains("channel-1 org.wildfly.channels:wildfly-ee-something-extra 1.0.000 ==> 1.0.001"));
    }

    /*@Test
    public void testDomainModeHostUnsupported_manifestVersions() throws Exception {
        UpdateCommand updateCommand = new UpdateCommand();
        updateCommand.host = "primary";
        updateCommand.manifestVersions = List.of("channel::1.2.3");

        assertSupportedHost(updateCommand, 32, false);
    }

    private void assertSupportedHost(UpdateCommand updateCommand, int hostVersion, boolean supported) throws Exception {
        CLICommandInvocation commandInvocation = Mockito.mock(CLICommandInvocation.class);
        CommandContext ctx = Mockito.mock(CommandContext.class);
        ModelControllerClient client = Mockito.mock(ModelControllerClient.class);
        Mockito.when(commandInvocation.getCommandContext()).thenReturn(ctx);
        Mockito.when(ctx.isDomainMode()).thenReturn(true);
        Mockito.when(ctx.getModelControllerClient()).thenReturn(client);

        ModelNode response = new ModelNode();
        response.get("outcome").set("success");
        response.get("result").set(hostVersion);
        Mockito.when(client.execute(Mockito.any(ModelNode.class))).thenReturn(response);

        try {
            updateCommand.execute(commandInvocation);
            if (!supported) {
                Assert.fail("CommandException expected.");
            }
        } catch (CommandException e) {
            if (!supported) {
                Assert.assertTrue(e.getMessage().startsWith("The host primary is of an older version"));
            } else {
                throw e;
            }
        }
    }*/

}
