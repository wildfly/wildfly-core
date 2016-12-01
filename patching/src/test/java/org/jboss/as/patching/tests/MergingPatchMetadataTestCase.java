/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.tests;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.Identity;
import org.jboss.as.patching.metadata.Identity.IdentityUpgrade;
import org.jboss.as.patching.metadata.LayerType;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.metadata.ModuleItem;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.Patch.PatchType;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.metadata.PatchElementBuilder;
import org.jboss.as.patching.metadata.PatchElementProvider;
import org.jboss.as.patching.metadata.PatchMerger;
import org.jboss.as.patching.runner.PatchUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class MergingPatchMetadataTestCase {

    protected static final MessageDigest DIGEST;

    static {
        try {
            DIGEST = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] digest(String str) {
        DIGEST.reset();
        return DIGEST.digest(str.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testAddModify() throws Exception {

        final Patch cp1 = generateCP("base", "cp1", ModificationType.ADD);
        final Patch cp2 = generateCP("cp1", "cp2", ModificationType.MODIFY);
        final Patch merged = PatchMerger.merge(cp1, cp2);

        assertEquals("cp2", merged.getPatchId());
        assertEquals("cp2" + " description", merged.getDescription());

        final IdentityUpgrade identity = merged.getIdentity().forType(PatchType.CUMULATIVE, Identity.IdentityUpgrade.class);
        assertEquals("base", identity.getVersion());
        assertEquals("cp2", identity.getResultingVersion());
        assertEquals(PatchType.CUMULATIVE, identity.getPatchType());

        final List<PatchElement> elements = merged.getElements();
        assertEquals(1, elements.size());
        final PatchElement e = elements.get(0);
        assertEquals("base-" + "cp2", e.getId());

        final PatchElementProvider provider = e.getProvider();
        assertEquals("base", provider.getName());
        assertEquals(PatchType.CUMULATIVE, provider.getPatchType());
        assertEquals(LayerType.Layer, provider.getLayerType());

        assertEquals(3, e.getModifications().size());

        for(ContentModification mod : e.getModifications()) {
            assertEquals(ModificationType.ADD, mod.getType());
            final ContentItem item = mod.getItem();
            assertEquals(0, mod.getTargetHash().length);
            if(ContentType.MODULE.equals(item.getContentType())) {
                Assert.assertArrayEquals(moduleHash("cp2"), item.getContentHash());
            } else if(ContentType.MISC.equals(item.getContentType())) {
                Assert.assertArrayEquals(miscHash("cp2"), item.getContentHash());
            } else {
                Assert.assertArrayEquals(bundleHash("cp2"), item.getContentHash());
            }
        }
    }

    @Test
    public void testAddRemove() throws Exception {

        final Patch cp1 = generateCP("base", "cp1", ModificationType.ADD);
        final Patch cp2 = generateCP("cp1", "cp2", ModificationType.REMOVE);
        final Patch merged = PatchMerger.merge(cp1, cp2);

        assertEquals("cp2", merged.getPatchId());
        assertEquals("cp2" + " description", merged.getDescription());

        final IdentityUpgrade identity = merged.getIdentity().forType(PatchType.CUMULATIVE, Identity.IdentityUpgrade.class);
        assertEquals("base", identity.getVersion());
        assertEquals("cp2", identity.getResultingVersion());
        assertEquals(PatchType.CUMULATIVE, identity.getPatchType());

        final List<PatchElement> elements = merged.getElements();
        assertEquals(1, elements.size());
        final PatchElement e = elements.get(0);
        assertEquals("base-" + "cp2", e.getId());

        final PatchElementProvider provider = e.getProvider();
        assertEquals("base", provider.getName());
        assertEquals(PatchType.CUMULATIVE, provider.getPatchType());
        assertEquals(LayerType.Layer, provider.getLayerType());

        //assertEquals(0, e.getModifications().size());
        // for modules remove is effectively a modify which changes the module xml to indicate an absent module
        // so, it will remain an add of an absent module
        assertEquals(1, e.getModifications().size());
        final ContentModification mod = e.getModifications().iterator().next();
        assertEquals(ModificationType.ADD, mod.getType());
        Assert.assertArrayEquals(PatchUtils.getAbsentModuleContentHash((ModuleItem) mod.getItem()), mod.getItem().getContentHash());
    }

    @Test
    public void testModifyRemove() throws Exception {

        final Patch cp1 = generateCP("base", "cp1", ModificationType.MODIFY);
        final Patch cp2 = generateCP("cp1", "cp2", ModificationType.REMOVE);
        final Patch merged = PatchMerger.merge(cp1, cp2);

        assertEquals("cp2", merged.getPatchId());
        assertEquals("cp2" + " description", merged.getDescription());

        final IdentityUpgrade identity = merged.getIdentity().forType(PatchType.CUMULATIVE, Identity.IdentityUpgrade.class);
        assertEquals("base", identity.getVersion());
        assertEquals("cp2", identity.getResultingVersion());
        assertEquals(PatchType.CUMULATIVE, identity.getPatchType());

        final List<PatchElement> elements = merged.getElements();
        assertEquals(1, elements.size());
        final PatchElement e = elements.get(0);
        assertEquals("base-" + "cp2", e.getId());

        final PatchElementProvider provider = e.getProvider();
        assertEquals("base", provider.getName());
        assertEquals(PatchType.CUMULATIVE, provider.getPatchType());
        assertEquals(LayerType.Layer, provider.getLayerType());

        assertEquals(3, e.getModifications().size());

        for(ContentModification mod : e.getModifications()) {
            assertEquals(ModificationType.REMOVE, mod.getType());
            final ContentItem item = mod.getItem();
            assertEquals(0, item.getContentHash().length);
            if(ContentType.MODULE.equals(item.getContentType())) {
                Assert.assertArrayEquals(moduleHash("base"), mod.getTargetHash());
            } else if(ContentType.MISC.equals(item.getContentType())) {
                Assert.assertArrayEquals(miscHash("base"), mod.getTargetHash());
            } else {
                Assert.assertArrayEquals(bundleHash("base"), mod.getTargetHash());
            }
        }
    }

    @Test
    public void testRemoveAdd() throws Exception {

        final Patch cp1 = generateCP("base", "cp1", ModificationType.REMOVE);
        final Patch cp2 = generateCP("cp1", "cp2", ModificationType.ADD);
        final Patch merged = PatchMerger.merge(cp1, cp2);

        assertEquals("cp2", merged.getPatchId());
        assertEquals("cp2" + " description", merged.getDescription());

        final IdentityUpgrade identity = merged.getIdentity().forType(PatchType.CUMULATIVE, Identity.IdentityUpgrade.class);
        assertEquals("base", identity.getVersion());
        assertEquals("cp2", identity.getResultingVersion());
        assertEquals(PatchType.CUMULATIVE, identity.getPatchType());

        final List<PatchElement> elements = merged.getElements();
        assertEquals(1, elements.size());
        final PatchElement e = elements.get(0);
        assertEquals("base-" + "cp2", e.getId());

        final PatchElementProvider provider = e.getProvider();
        assertEquals("base", provider.getName());
        assertEquals(PatchType.CUMULATIVE, provider.getPatchType());
        assertEquals(LayerType.Layer, provider.getLayerType());

        assertEquals(3, e.getModifications().size());

        for(ContentModification mod : e.getModifications()) {
            assertEquals(ModificationType.MODIFY, mod.getType());
            final ContentItem item = mod.getItem();
            if(ContentType.MODULE.equals(item.getContentType())) {
                Assert.assertArrayEquals(moduleHash("base"), mod.getTargetHash());
                Assert.assertArrayEquals(moduleHash("cp2"), item.getContentHash());
            } else if(ContentType.MISC.equals(item.getContentType())) {
                Assert.assertArrayEquals(miscHash("base"), mod.getTargetHash());
                Assert.assertArrayEquals(miscHash("cp2"), item.getContentHash());
            } else {
                Assert.assertArrayEquals(bundleHash("base"), mod.getTargetHash());
                Assert.assertArrayEquals(bundleHash("cp2"), item.getContentHash());
            }
        }
    }

    @Test
    public void testModifyModify() throws Exception {

        final Patch cp1 = generateCP("base", "cp1", ModificationType.MODIFY);
        final Patch cp2 = generateCP("cp1", "cp2", ModificationType.MODIFY);
        final Patch merged = PatchMerger.merge(cp1, cp2);

        assertEquals("cp2", merged.getPatchId());
        assertEquals("cp2" + " description", merged.getDescription());

        final IdentityUpgrade identity = merged.getIdentity().forType(PatchType.CUMULATIVE, Identity.IdentityUpgrade.class);
        assertEquals("base", identity.getVersion());
        assertEquals("cp2", identity.getResultingVersion());
        assertEquals(PatchType.CUMULATIVE, identity.getPatchType());

        final List<PatchElement> elements = merged.getElements();
        assertEquals(1, elements.size());
        final PatchElement e = elements.get(0);
        assertEquals("base-" + "cp2", e.getId());

        final PatchElementProvider provider = e.getProvider();
        assertEquals("base", provider.getName());
        assertEquals(PatchType.CUMULATIVE, provider.getPatchType());
        assertEquals(LayerType.Layer, provider.getLayerType());

        assertEquals(3, e.getModifications().size());

        for(ContentModification mod : e.getModifications()) {
            assertEquals(ModificationType.MODIFY, mod.getType());
            final ContentItem item = mod.getItem();
            if(ContentType.MODULE.equals(item.getContentType())) {
                Assert.assertArrayEquals(moduleHash("base"), mod.getTargetHash());
                Assert.assertArrayEquals(moduleHash("cp2"), item.getContentHash());
            } else if(ContentType.MISC.equals(item.getContentType())) {
                Assert.assertArrayEquals(miscHash("base"), mod.getTargetHash());
                Assert.assertArrayEquals(miscHash("cp2"), item.getContentHash());
            } else {
                Assert.assertArrayEquals(bundleHash("base"), mod.getTargetHash());
                Assert.assertArrayEquals(bundleHash("cp2"), item.getContentHash());
            }
        }
    }

    protected Patch generateCP(final String currentCP, final String nextCP, ModificationType type) {
        final PatchBuilder patchBuilder = PatchBuilder.create().setPatchId(nextCP).setDescription(nextCP + " description");

        patchBuilder.upgradeIdentity("identity", currentCP, nextCP);
        final PatchElementBuilder elementBuilder = patchBuilder.upgradeElement("base-" + nextCP, "base", false);
        if(ModificationType.ADD.equals(type)) {
            elementBuilder.addModule("org.jboss.test", "main", moduleHash(nextCP))
                .addBundle("org.jboss.test", "main", bundleHash(nextCP))
                .addFile("test.txt", Arrays.asList(new String[]{"org","jboss","test"}), miscHash(nextCP), false);
        } else if(ModificationType.MODIFY.equals(type)) {
            elementBuilder.modifyModule("org.jboss.test", "main", moduleHash(currentCP), moduleHash(nextCP))
                .modifyBundle("org.jboss.test", "main", bundleHash(currentCP), bundleHash(nextCP))
                .modifyFile("test.txt", Arrays.asList(new String[]{"org","jboss","test"}), miscHash(currentCP), miscHash(nextCP), false);
        } else {
            elementBuilder.removeModule("org.jboss.test", "main", moduleHash(currentCP))
                .removeBundle("org.jboss.test", "main", bundleHash(currentCP))
                .removeFile("test.txt", Arrays.asList(new String[] { "org", "jboss", "test" }), miscHash(currentCP), false);
        }

        return patchBuilder.build();
    }

    protected byte[] miscHash(final String nextCP) {
        return digest("file:" + nextCP + ":org.jboss.test");
    }

    protected byte[] bundleHash(final String nextCP) {
        return digest("bundle:" + nextCP + ":org.jboss.test:main");
    }

    protected byte[] moduleHash(final String nextCP) {
        return digest("module:" + nextCP + ":org.jboss.test:main");
    }
}
