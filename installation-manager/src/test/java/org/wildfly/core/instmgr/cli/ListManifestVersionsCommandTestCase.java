package org.wildfly.core.instmgr.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.wildfly.core.instmgr.InstMgrConstants;

import java.util.ArrayList;
import java.util.List;

public class ListManifestVersionsCommandTestCase {

    private final CommandContext ctx = Mockito.mock(CommandContext.class);
    private final List<String> writes = new ArrayList<>();

    @Before
    public void setup() {
        Mockito.doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String line = invocation.getArgument(0);
                writes.add(line + "\n");
                return null;
            }
        }).when(ctx).printLine(Mockito.anyString());

        Mockito.doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String line = invocation.getArgument(0);
                writes.add(line);
                return null;
            }
        }).when(ctx).print(Mockito.anyString());
    }

    @Test
    public void testPrintManifestUpdateResult() {
        List<ModelNode> updates = composeInputData(true);

        ListManifestVersionsCommand.printManifestNode(ctx, updates);

        String output = String.join("", writes);
        // print to check formatting visually
        System.out.println(output);

        Assert.assertTrue(output.contains("Channel name: channel-0"));
        Assert.assertTrue(output.contains("Manifest location: org.wildfly.channels:wildfly-ee"));
        Assert.assertTrue(output.contains("Current manifest version: 1.0.0 (Logical version 1.0.0)"));
        Assert.assertTrue(output.contains("- 1.0.1 (Logical version 1.0.1)"));
    }

    @Test
    public void testPrintManifestUpdateResult_noLogicalVersions() {
        List<ModelNode> updates = composeInputData(false);

        ListManifestVersionsCommand.printManifestNode(ctx, updates);

        String output = String.join("", writes);
        // print to check formatting visually
        System.out.println(output);

        Assert.assertTrue(output.contains("Channel name: channel-0"));
        Assert.assertTrue(output.contains("Manifest location: org.wildfly.channels:wildfly-ee"));
        Assert.assertTrue(output.contains("Current manifest version: 1.0.0"));
        Assert.assertTrue(output.contains("- 1.0.1"));
    }

    private static List<ModelNode> composeInputData(boolean addLogicalVersions) {
        List<ModelNode> list = new ArrayList<>();

        ModelNode manifestNode = new ModelNode();
        manifestNode.get(InstMgrConstants.CHANNEL_NAME).set("channel-0");
        manifestNode.get(InstMgrConstants.MANIFEST_LOCATION).set("org.wildfly.channels:wildfly-ee");
        manifestNode.get(InstMgrConstants.MANIFEST_CURRENT_VERSION).set("1.0.0");
        if (addLogicalVersions) {
            manifestNode.get(InstMgrConstants.MANIFEST_CURRENT_LOGICAL_VERSION).set("Logical version 1.0.0");
        }

        ModelNode versionNode = new ModelNode();
        versionNode.get(InstMgrConstants.MANIFEST_VERSION).set("1.0.1");
        if (addLogicalVersions) {
            versionNode.get(InstMgrConstants.MANIFEST_LOGICAL_VERSION).set("Logical version 1.0.1");
        }
        manifestNode.get(InstMgrConstants.MANIFEST_VERSIONS).add(versionNode);

        list.add(manifestNode);
        return list;
    }

}
