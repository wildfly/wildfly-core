/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
 */

package org.jboss.as.test.patching;

import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.test.patching.PatchingTestUtil.createPatchXMLFile;
import static org.jboss.as.test.patching.PatchingTestUtil.createZippedPatchFile;
import static org.jboss.as.test.patching.PatchingTestUtil.randomString;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.version.ProductConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class BootLoggingPatchingStateTestCase extends AbstractPatchingTestCase {

    protected ProductConfig productConfig;
    protected CommandContext ctx;

    protected ByteArrayOutputStream baos = new ByteArrayOutputStream();
    protected PrintStream out;

    @Before
    public void setup() throws Exception {
        productConfig = new ProductConfig(PatchingTestUtil.PRODUCT, PatchingTestUtil.AS_VERSION, "main");
        ctx = CLITestUtil.getCommandContext();
    }

    @After
    public void cleanup() throws Exception {
        ctx.terminateSession();
        out.close();
    }

    @Test
    public void testMain() throws Exception {

        assertLoggedState(PatchingTestUtil.PRODUCT, null);

        // prepare the patch
        final String oneOff1Id = randomString();
        applyPatch(oneOff1Id, true);

        assertLoggedState(PatchingTestUtil.PRODUCT, null, oneOff1Id);

        final String cpId = randomString();
        applyPatch(cpId, false);
        assertLoggedState(PatchingTestUtil.PRODUCT, cpId);

        final String oneOff2Id = randomString();
        applyPatch(oneOff2Id, true);
        assertLoggedState(PatchingTestUtil.PRODUCT, cpId, oneOff2Id);

        final String oneOff3Id = randomString();
        applyPatch(oneOff3Id, true);
        assertLoggedState(PatchingTestUtil.PRODUCT, cpId, oneOff3Id, oneOff2Id);

        final String lp1OneOffId = randomString();
        applyPatch("lp1", "1.1", lp1OneOffId, true);
        assertLoggedState(new PatchingState(PatchingTestUtil.PRODUCT, cpId, oneOff3Id, oneOff2Id),
                new PatchingState("lp1", null, lp1OneOffId));

        final String lp2CpId = randomString();
        applyPatch("lp2", "2.2", lp2CpId, false);
        assertLoggedState(new PatchingState(PatchingTestUtil.PRODUCT, cpId, oneOff3Id, oneOff2Id),
                new PatchingState("lp1", null, lp1OneOffId),
                new PatchingState("lp2", lp2CpId));
    }

    protected void applyPatch(String patchId, boolean oneOff) throws Exception {
        applyPatch(productConfig.getProductName(), productConfig.getProductVersion(), patchId, oneOff);
    }

    protected void applyPatch(String product, String version, String patchId, boolean oneOff) throws Exception {
        final File patchDir = mkdir(tempDir, patchId);
        final ContentModification miscFileAdded = ContentModificationUtils.addMisc(patchDir, patchId, patchId + " content", product + patchId);
        final PatchBuilder builder = PatchBuilder.create().setPatchId(patchId);
        if(oneOff) {
            builder.oneOffPatchIdentity(product, version);
        } else {
            builder.upgradeIdentity(product, version, version);
        }
        final Patch patch = builder.addContentModification(miscFileAdded).build();
        createPatchXMLFile(patchDir, patch);
        applyPatch(createZippedPatchFile(patchDir, patchId));
    }

    private void assertLoggedState(String stream, String cpId, String... oneOff) throws Exception {
        assertLoggedState(new PatchingState(stream, cpId, oneOff));
    }

    private void assertLoggedState(PatchingState... states) throws Exception {
        startController();
        try {
            final String patchingCode = "WFLYPAT0050";
            final Set<String> messages = new HashSet<>();
            try (BufferedReader reader = getOutputReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(patchingCode)) {
                        messages.add(line);
                    }
                }
            }
            Assert.assertEquals(states.length, messages.size());

            // Check each state to ensure it was logged
            for (PatchingState state : states) {
                boolean matched = false;
                for (String message : messages) {
                    if (message.contains(state.getCp()) && message.contains(state.getStream()) && message.contains(state.getOneOffs())) {
                        matched = true;
                        break;
                    }
                }
                Assert.assertTrue("Could not match state " + state + " to messages: " + messages, matched);
            }
        } finally {
            stopController();
        }
    }

    private void applyPatch(File zippedPatch) throws Exception {
        startController();
        try {
            ctx.connectController();
            ctx.handle("patch apply " + zippedPatch.getAbsolutePath());
            if(ctx.getExitCode() != 0) {
                Assert.fail("failed to apply patch");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            stopController();
        }
    }


    private void stopController() {
        controller.stop();
    }

    private void startController() {
        baos.reset();
        out = new PrintStream(baos);
        controller.start(out);
    }

    private BufferedReader getOutputReader() {
        out.flush();
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(baos.toByteArray())));
    }

    private static class PatchingState {
        final String stream;
        final String cp;
        final String[] oneOffs;

        PatchingState(String stream, String cp, String... oneOffs) {
            this.stream = stream;
            this.cp = cp;
            this.oneOffs = oneOffs;
        }

        String getCp() {
            return cp == null ? "base" : cp;
        }

        String getOneOffs() {
            final StringBuilder buf = new StringBuilder();
            if (oneOffs.length == 0) {
                buf.append("none");
            } else {
                buf.append(oneOffs[0]);
                if (oneOffs.length > 1) {
                    for (int i = 1; i < oneOffs.length; ++i) {
                        buf.append(", ").append(oneOffs[i]);
                    }
                }
            }
            return buf.toString();
        }

        String getStream() {
            return stream;
        }

        @Override
        public String toString() {
            return "PatchingState(stream=" + stream
                    + ", cp=" + cp
                    + ", oneOffs=" + (oneOffs == null ? "null" : Arrays.toString(oneOffs))
                    + ")";
        }
    }
}
