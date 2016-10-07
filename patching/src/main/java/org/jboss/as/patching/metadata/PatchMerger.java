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

package org.jboss.as.patching.metadata;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.ZipUtils;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.Patch.PatchType;
import org.jboss.as.patching.runner.PatchUtils;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author Alexey Loubyansky
 */
public class PatchMerger {

    private static final String DIRECTORY_PREFIX = "tmp-";
    public static final String PATCH_XML_SUFFIX = "-patch.xml";
    private static final File TEMP_DIR = new File(WildFlySecurityManager.getPropertyPrivileged("java.io.tmpdir", null));

    public static File merge(File patch1, File patch2, File merged) throws PatchingException {

        final File workDir = createTempDir();
        final File patch1Dir = expandContent(patch1, workDir, "patch1");
        final File patch2Dir = expandContent(patch2, workDir, "patch2");
        final File mergedDir = new File(workDir, "merged");

        final Patch patch1Metadata = parsePatchXml(patch1Dir, patch1);
        final Patch patch2Metadata = parsePatchXml(patch2Dir, patch2);
        final Patch mergedMetadata = merge(patch1Metadata, patch2Metadata);

        // list(patch1Dir);
        // list(patch2Dir);

        if (!mergedDir.mkdirs()) {
            throw new PatchingException("Failed to create directory " + mergedDir.getAbsolutePath());
        }

        // merge with the previous versions
        for (File f : patch1Dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(PATCH_XML_SUFFIX);
            }
        })) {
            Patch patch;
            try {
                patch = PatchXml.parse(f).resolvePatch(null, null);
            } catch (Exception e) {
                throw new PatchingException("Failed to parse " + f.getAbsolutePath(), e);
            }

            patch = merge(patch, patch2Metadata);

            try (Writer writer = Files.newBufferedWriter(new File(mergedDir, f.getName()).toPath(), StandardCharsets.UTF_8)){
                PatchXml.marshal(writer, patch);
            } catch (Exception e) {
                throw new PatchingException("Failed to marshal merged metadata into " + f.getName(), e);
            }

        }

        // the latest patch.xml
        copyFile(new File(patch2Dir, PatchXml.PATCH_XML), new File(mergedDir, patch2Metadata.getIdentity().getVersion() +  PATCH_XML_SUFFIX));
        // merged patch.xml is the metadata from the earliest version to the latest
        try (Writer writer = Files.newBufferedWriter(new File(mergedDir, PatchXml.PATCH_XML).toPath(), StandardCharsets.UTF_8)){
            PatchXml.marshal(writer, mergedMetadata);
        } catch (Exception e) {
            throw new PatchingException("Failed to marshal merged metadata into " + PatchXml.PATCH_XML, e);
        }

        try {
            mergeRootContent(new File(patch1Dir, patch1Metadata.getPatchId()),
                    new File(patch2Dir, patch2Metadata.getPatchId()), new File(mergedDir, patch2Metadata.getPatchId()));
        } catch (IOException e) {
            throw new PatchingException("Failed to merge root modifications", e);
        }

        try {
            mergeElementContent(patch1Dir, patch2Dir, mergedDir, patch1Metadata, patch2Metadata);
        } catch (IOException e) {
            throw new PatchingException("Failed to merge element modifications", e);
        }

        // list(mergedDir);

        ZipUtils.zip(mergedDir, merged);
        IoUtils.recursiveDelete(workDir);
        return merged;
    }

    private static void copyFile(File source, File target) throws PatchingException {
        try {
            Files.copy(source.toPath(), target.toPath());
        } catch (IOException e1) {
            throw new PatchingException("Failed to copy " + source.getAbsolutePath() + " to " + target.getAbsolutePath());
        }
    }

    private static void mergeElementContent(final File patch1Dir, final File patch2Dir, final File mergedDir,
            final Patch patch1Metadata, final Patch patch2Metadata) throws PatchingException, IOException {

        final Map<String, PatchElement> patch2Elements = new HashMap<String, PatchElement>(patch2Metadata.getElements().size());
        for (PatchElement e : patch2Metadata.getElements()) {
            patch2Elements.put(e.getProvider().getName(), e);
        }
        for (PatchElement e1 : patch1Metadata.getElements()) {
            final File e1Dir = new File(patch1Dir, e1.getId());
            final PatchElement e2 = patch2Elements.remove(e1.getProvider().getName());
            if (e2 == null) {
                if (e1Dir.exists()) {
                    IoUtils.copyFile(e1Dir, new File(mergedDir, e1.getId()));
                }
            } else {
                final ContentModifications cp2Mods = new ContentModifications(e2.getModifications());
                for (ContentModification cp1Mod : e1.getModifications()) {
                    final ContentModification cp2Mod = cp2Mods.remove(cp1Mod.getItem());
                    if (cp2Mod == null) {
                        copyModificationContent(patch1Dir, e1.getId(), mergedDir, e2.getId(), cp1Mod);
                    } else {
                        copyModificationContent(patch2Dir, e2.getId(), mergedDir, e2.getId(), cp2Mod);
                    }
                }
                for(ContentModification cp2Mod : cp2Mods.getModifications()) {
                    copyModificationContent(patch2Dir, e2.getId(), mergedDir, e2.getId(), cp2Mod);
                }
            }
        }
        for (PatchElement e2 : patch2Elements.values()) {
            final File e2Dir = new File(patch2Dir, e2.getId());
            if (e2Dir.exists()) {
                IoUtils.copyFile(e2Dir, new File(mergedDir, e2.getId()));
            }
        }
    }

    private static void copyModificationContent(final File srcPatchDir, final String srcElementId,
            final File targetPatchDir, final String targetElementId,
            final ContentModification mod) throws PatchingException {
        final ModificationType type = mod.getType();
        if(type.equals(ModificationType.REMOVE)) {
            return;
        }
        File modSrcDir = new File(srcPatchDir, srcElementId);
        File modTrgDir = new File(targetPatchDir, targetElementId);
        final ContentType contentType = mod.getItem().getContentType();
        if(ContentType.MISC.equals(contentType)) {
            modSrcDir = new File(modSrcDir, Constants.MISC);
            modTrgDir = new File(modTrgDir, Constants.MISC);
            for (final String path : ((MiscContentItem)mod.getItem()).getPath()) {
                modSrcDir = new File(modSrcDir, path);
                modTrgDir = new File(modTrgDir, path);
            }
            copyDir(modSrcDir, modTrgDir);
        } else {
            if (contentType.equals(ContentType.MODULE)) {
                modSrcDir = new File(modSrcDir, Constants.MODULES);
                modTrgDir = new File(modTrgDir, Constants.MODULES);
                for (String name : mod.getItem().getName().split("\\.")) {
                    modSrcDir = new File(modSrcDir, name);
                    modTrgDir = new File(modTrgDir, name);
                }
            } else if (contentType.equals(ContentType.BUNDLE)) {
                modSrcDir = new File(modSrcDir, Constants.BUNDLES);
                modTrgDir = new File(modTrgDir, Constants.BUNDLES);
                for (String name : mod.getItem().getName().split("\\.")) {
                    modSrcDir = new File(modSrcDir, name);
                    modTrgDir = new File(modTrgDir, name);
                }
            } else {
                throw new PatchingException("Unexpected content type " + contentType);
            }
            // copy actual module slots skipping possibly nested modules
            for (File subDir : modSrcDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isDirectory() && new File(pathname, "module.xml").exists();
                }
            })) {
                copyDir(subDir, new File(modTrgDir, subDir.getName()));
            }
        }
    }

    protected static void copyDir(File src, File trg) throws PatchingException {
        try {
            IoUtils.copyFile(src, trg);
        } catch (IOException e) {
            throw new PatchingException("Failed to copy modification content from " + src.getAbsolutePath() + " to " + trg.getAbsolutePath());
        }
    }

    private static void mergeRootContent(final File root1Dir, final File root2Dir, final File mergedRootDir) throws IOException {

        if (root1Dir.exists()) {
            IoUtils.copyFile(root1Dir, mergedRootDir);
        }
        if (root2Dir.exists()) {
            IoUtils.copyFile(root2Dir, mergedRootDir);
        }
    }

    private static Patch parsePatchXml(final File patch1Dir, File patch1) throws PatchingException {
        final File patch1Xml = new File(patch1Dir, PatchXml.PATCH_XML);
        if (!patch1Xml.exists()) {
            throw new PatchingException("Failed to locate " + PatchXml.PATCH_XML + " in " + patch1.getAbsolutePath());
        }
        try {
            return PatchXml.parse(patch1Xml).resolvePatch(null, null);
        } catch (Exception e) {
            throw new PatchingException("Failed to parse " + PatchXml.PATCH_XML + " from " + patch1.getAbsolutePath(), e);
        }
    }

    private static File expandContent(File patchFile, final File workDir, final String expandDirName) throws PatchingException {
        final File patchDir;
        try {
            if (!patchFile.isDirectory()) {
                patchDir = new File(workDir, expandDirName);
                // Save the content
                final File cachedContent = new File(patchDir, "content");
                IoUtils.copy(patchFile, cachedContent);
                // Unpack to the work dir
                ZipUtils.unzip(cachedContent, patchDir);
            } else {
                patchDir = patchFile;
            }
        } catch (IOException e) {
            throw new PatchingException("Failed to unzip " + patchFile.getAbsolutePath());
        }
        return patchDir;
    }

    public static Patch merge(Patch cp1, Patch cp2) throws PatchingException {
        return merge(cp1, cp2, true);
    }

    public static Patch merge(Patch cp1, Patch cp2, boolean nextVersion) throws PatchingException {

        // for now support merging only CPs
        final Identity.IdentityUpgrade cp1Identity = cp1.getIdentity().forType(Patch.PatchType.CUMULATIVE,
                Identity.IdentityUpgrade.class);
        final Identity.IdentityUpgrade cp2Identity = cp2.getIdentity().forType(Patch.PatchType.CUMULATIVE,
                Identity.IdentityUpgrade.class);
        assertUpgrade(cp1Identity.getPatchType());
        assertUpgrade(cp2Identity.getPatchType());

        // for now support merging only CPs targeting the same identity name
        if (!cp1Identity.getName().equals(cp2Identity.getName())) {
            throw new PatchingException("Patches target different identities: " + cp1Identity.getName() + " and "
                    + cp2Identity.getName());
        }

        if (nextVersion && !cp1Identity.getResultingVersion().equals(cp2Identity.getVersion())) {
            throw new PatchingException(cp1.getPatchId() + " upgrades to version " + cp1Identity.getResultingVersion()
                    + " but " + cp2.getPatchId() + " targets version " + cp2Identity.getVersion());
        }

        final PatchBuilder builder = PatchBuilder.create().setPatchId(cp2.getPatchId()).setDescription(cp2.getDescription())
                .setLink(cp2.getLink());

        builder.upgradeIdentity(cp1Identity.getName(), cp1Identity.getVersion(), cp2Identity.getResultingVersion());

        final Map<String, PatchElement> cp2LayerElements = new HashMap<String, PatchElement>();
        final Map<String, PatchElement> cp2AddonElements = new HashMap<String, PatchElement>();
        for (PatchElement pe : cp2.getElements()) {
            final PatchElementProvider provider = pe.getProvider();
            assertUpgrade(provider.getPatchType());
            if (provider.isAddOn()) {
                cp2AddonElements.put(provider.getName(), pe);
            } else {
                cp2LayerElements.put(provider.getName(), pe);
            }
        }

        for (final PatchElement cp1El : cp1.getElements()) {
            final PatchElementProvider provider = cp1El.getProvider();
            assertUpgrade(provider.getPatchType());
            final PatchElement cp2El;
            if (provider.isAddOn()) {
                cp2El = cp2AddonElements.remove(provider.getName());
            } else {
                cp2El = cp2LayerElements.remove(provider.getName());
            }

            if (cp2El == null) {
                builder.addElement(cp1El);
            } else {
                final PatchElementBuilder elementBuilder = builder.upgradeElement(cp2El.getId(), provider.getName(),
                        provider.isAddOn()).setDescription(cp2El.getDescription());

                mergeModifications(elementBuilder, cp1El.getModifications(), cp2El.getModifications(), cp1, cp2);
            }
        }

        for (PatchElement cp2Element : cp2LayerElements.values()) {
            builder.addElement(cp2Element);
        }
        for (PatchElement cp2Element : cp2AddonElements.values()) {
            builder.addElement(cp2Element);
        }

        mergeModifications(builder, cp1.getModifications(), cp2.getModifications(), cp1, cp2);

        return builder.build();
    }

    private static void mergeModifications(final ModificationBuilderTarget<?> elementBuilder,
            Collection<ContentModification> cp1Modifications, Collection<ContentModification> cp2Modifications, Patch cp1,
            Patch cp2) throws PatchingException {
        final ContentModifications cp2Mods = new ContentModifications(cp2Modifications);
        for (ContentModification cp1Mod : cp1Modifications) {
            final ContentModification cp2Mod = cp2Mods.remove(cp1Mod.getItem());
            if (cp2Mod == null) {
                elementBuilder.addContentModification(cp1Mod);
            } else {
                final ModificationType cp1Type = cp1Mod.getType();
                final ModificationType cp2Type = cp2Mod.getType();

                final ModificationType modType;
                if (cp1Type.equals(ModificationType.ADD)) {
                    if (cp2Type.equals(ModificationType.ADD)) {
                        throw new PatchingException("Patch " + cp2.getPatchId() + " adds " + cp1Mod.getItem().getRelativePath()
                                + " already added by patch " + cp1.getPatchId());
                    }
                    if (cp2Type.equals(ModificationType.MODIFY)) {
                        modType = ModificationType.ADD;
                    } else { // remove cancels add
                        if(cp1Mod.getItem().getContentType().equals(ContentType.MODULE)) {
                            // but not for modules where remove is effectively modify (resulting in module.xml indicating an absent module)
                            // so add becomes an add of an absent module
                            modType = ModificationType.ADD;
                        } else {
                            modType = null;
                            continue;
                        }
                    }
                } else if (cp1Type.equals(ModificationType.REMOVE)) {
                    if (cp2Type.equals(ModificationType.REMOVE)) {
                        throw new PatchingException("Patch " + cp2.getPatchId() + " removes "
                                + cp1Mod.getItem().getRelativePath() + " already removed by patch " + cp1.getPatchId());
                    }
                    /*if (cp2Type.equals(ModificationType.MODIFY)) {
                        throw new PatchingException("Patch " + cp2.getPatchId() + " modifies "
                                + cp1Mod.getItem().getRelativePath() + " removed by patch " + cp1.getPatchId());
                        this could happen since the REMOVE will leave a module.xml indicating the module is absent
                        so, to re-add the module, it has to be MODIFY, since ADD will fail
                    }*/
                    // add after remove makes it modify
                    modType = ModificationType.MODIFY;
                } else { // modify
                    if (cp2Type.equals(ModificationType.ADD)) {
                        throw new PatchingException("Patch " + cp2.getPatchId() + " adds " + cp1Mod.getItem().getRelativePath()
                                + " modified by patch " + cp1.getPatchId());
                    }
                    if (cp2Type.equals(ModificationType.REMOVE)) {
                        modType = ModificationType.REMOVE;
                    } else {
                        modType = ModificationType.MODIFY;
                    }
                }

                if (ModificationType.ADD.equals(modType)) {
                    final ContentItem cp2Item = cp2Mod.getItem();
                    if (cp2Item.getContentType().equals(ContentType.MODULE)) {
                        final ModuleItem module = (ModuleItem) cp2Item;
                        if(cp2Type.equals(ModificationType.REMOVE)) {
                            try {
                                elementBuilder.addModule(module.getName(), module.getSlot(), PatchUtils.getAbsentModuleContentHash(module));
                            } catch (IOException e) {
                                throw new PatchingException("Failed to calculate hash for the removed module " + module.getName(), e);
                            }
                        } else {
                            elementBuilder.addModule(module.getName(), module.getSlot(), module.getContentHash());
                        }
                    } else if (cp2Item.getContentType().equals(ContentType.MISC)) {
                        final MiscContentItem misc = (MiscContentItem) cp2Item;
                        elementBuilder.addFile(misc.getName(), Arrays.asList(misc.getPath()), misc.getContentHash(),
                                misc.isDirectory());
                    } else { // bundle
                        final BundleItem bundle = (BundleItem) cp2Item;
                        elementBuilder.addBundle(bundle.getName(), bundle.getSlot(), bundle.getContentHash());
                    }
                } else if (ModificationType.REMOVE.equals(modType)) {
                    final ContentItem cp1Item = cp1Mod.getItem();
                    if (cp1Item.getContentType().equals(ContentType.MODULE)) {
                        final ModuleItem module = (ModuleItem) cp2Mod.getItem();
                        elementBuilder.removeModule(module.getName(), module.getSlot(), cp1Mod.getTargetHash());
                    } else if (cp1Item.getContentType().equals(ContentType.MISC)) {
                        final MiscContentItem misc = (MiscContentItem) cp2Mod.getItem();
                        elementBuilder.removeFile(misc.getName(), Arrays.asList(misc.getPath()), cp1Mod.getTargetHash(),
                                misc.isDirectory());
                    } else { // bundle
                        final BundleItem bundle = (BundleItem) cp2Mod.getItem();
                        elementBuilder.removeBundle(bundle.getName(), bundle.getSlot(), cp1Mod.getTargetHash());
                    }
                } else { // modify
                    final ContentItem cp1Item = cp1Mod.getItem();
                    if (cp1Item.getContentType().equals(ContentType.MODULE)) {
                        final ModuleItem module = (ModuleItem) cp2Mod.getItem();
                        elementBuilder.modifyModule(module.getName(), module.getSlot(), cp1Mod.getTargetHash(),
                                module.getContentHash());
                    } else if (cp1Item.getContentType().equals(ContentType.MISC)) {
                        final MiscContentItem misc = (MiscContentItem) cp2Mod.getItem();
                        elementBuilder.modifyFile(misc.getName(), Arrays.asList(misc.getPath()), cp1Mod.getTargetHash(),
                                misc.getContentHash(), misc.isDirectory());
                    } else { // bundle
                        final BundleItem bundle = (BundleItem) cp2Mod.getItem();
                        elementBuilder.modifyBundle(bundle.getName(), bundle.getSlot(), cp1Mod.getTargetHash(),
                                bundle.getContentHash());
                    }
                }
            }
        }

        for (ContentModification cp2Mod : cp2Mods.getModifications()) {
            elementBuilder.addContentModification(cp2Mod);
        }
    }

    private static void assertUpgrade(final PatchType patchType) throws PatchingException {
        if (!PatchType.CUMULATIVE.equals(patchType)) {
            throw new PatchingException("Merging one-off patches is not supported at this point.");
        }
    }

    static File createTempDir() throws PatchingException {
        return createTempDir(TEMP_DIR);
    }

    static File createTempDir(final File parent) throws PatchingException {
        File workDir = null;
        int count = 0;
        while (workDir == null || workDir.exists()) {
            count++;
            workDir = new File(parent == null ? TEMP_DIR : parent, DIRECTORY_PREFIX + count);
        }
        if (!workDir.mkdirs()) {
            throw new PatchingException(PatchLogger.ROOT_LOGGER.cannotCreateDirectory(workDir.getAbsolutePath()));
        }
        return workDir;
    }

    private static class ContentModifications {

        private final Map<Integer, ContentModification> modifications;

        ContentModifications(Collection<ContentModification> mods) {
            modifications = new HashMap<Integer, ContentModification>(mods.size());
            for (ContentModification mod : mods) {
                modifications.put(getKey(mod.getItem()), mod);
            }
        }

        ContentModification remove(ContentItem item) {
            return modifications.remove(getKey(item));
        }

        Collection<ContentModification> getModifications() {
            return modifications.values();
        }

        private Integer getKey(ContentItem item) {
            final int prime = 31;
            int result = 1;
            result = prime * result + item.getContentType().hashCode();
            result = prime * result + item.getRelativePath().hashCode();
            return result;
        }
    }

/*    private static void ls(final File f) {
        System.out.println(f.getAbsolutePath());
        for(File c : f.listFiles()) {
            ls(c, "  ");
        }
    }

    private static void ls(final File f, String offset) {
        System.out.println(offset + f.getName());
        if(f.isDirectory()) {
            for(File c : f.listFiles()) {
                ls(c, offset + "  ");
            }
        }
    }
*/}
