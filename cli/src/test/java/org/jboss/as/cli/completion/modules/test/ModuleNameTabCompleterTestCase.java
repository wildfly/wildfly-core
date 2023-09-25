/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.modules.test;

import org.jboss.as.cli.handlers.ModuleNameTabCompleter;
import org.jboss.as.cli.handlers.module.ModuleConfigImpl;
import org.jboss.staxmapper.FormattingXMLStreamWriter;
import org.junit.After;
import org.junit.Test;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Bartosz Spyrko-Smietanko
 */
public class ModuleNameTabCompleterTestCase {

    public static final String MODULES_DIR = "test-modules";
    private final ModuleNameTabCompleter completer =
            ModuleNameTabCompleter.completer(new File(MODULES_DIR))
                                  .excludeNonModuleFolders(true)
                                  .includeSystemModules(true)
                                  .build();

    @After
    public void tearDown() {
        final File modules = new File(MODULES_DIR);
        if (modules.exists()) {
            remove(modules);
        }
    }

    private void remove(File p) {
        for (File child : p.listFiles()) {
            if (child.isDirectory()) {
                remove(child);
            } else {
                child.delete();
            }
        }

        p.delete();
    }

    @Test
    public void suggestAllFoldersIfBufferIsNull() throws Exception {
        createModules("org", "com");

        assertEquals(Arrays.asList("com", "org"), complete(null));
    }

    @Test
    public void suggestAllFoldersIfBufferIsEmpty() throws Exception {
        createModules("org", "com");

        assertEquals(Arrays.asList("com", "org"), complete(""));
    }

    @Test
    public void suggestOnlyMatchingFoldersIfBufferIsNotEmpty() throws Exception {
        createModules("org", "foo", "foo2");

        assertEquals(Arrays.asList("foo", "foo2"), complete("f"));
    }

    @Test
    public void ignoreSystemFolder() throws Exception {
        createModules("org", "com");
        new File(MODULES_DIR + "/system/bar").mkdirs();

        assertEquals(Arrays.asList("com", "org"), complete(null));
    }

    @Test
    public void returnEmptyListIfNoMatchesFound() throws Exception {
        createModules("com");

        assertEquals(Collections.emptyList(), complete("foo"));
    }

    @Test
    public void includeLayerModules() throws Exception {
        createLayerModules("base", "org", "com");

        assertEquals(Arrays.asList("com", "org"), complete(null));
    }

    @Test
    public void includeAddonsModulesInSuggestions() throws Exception {
        createAddonsModules("addon1", "org", "com");

        assertEquals(Arrays.asList("com", "org"), complete(null));
    }

    @Test
    public void suggestOnlyFirstPartOfTheModuleName() throws Exception {
        createModules("com/module1", "com/module2");

        assertEquals(Arrays.asList("com."), complete(null));
    }

    @Test
    public void suggestOnlyFirstPartOfModulesMatchingBuffer() throws Exception {
        createModules("com/module1", "com/module2", "org/foo");

        assertEquals(Arrays.asList("com."), complete("co"));
    }

    @Test
    public void suggestOnlyFirstModuleWhenHasNestedModulesAndBufferDoesntMatchExactly() throws Exception {
        createModules("com/module1", "com/module1/module2");

        assertEquals(Arrays.asList("com.module1"), complete("com.module"));
    }

    @Test
    public void suggestBothMatchedModuleAndModuleWithSeparatorWhenHasNestedModuleAndBufferMatchesModule() throws Exception {
        createModules("com/module1", "com/module1/module2");

        assertEquals(Arrays.asList("com.module1", "com.module1."), complete("com.module1"));
    }

    @Test
    public void suggestNestedFolderIfBufferEndsWithSeparator() throws Exception {
        createModules("org/root");
        createLayerModules("base", "org/layer");

        assertEquals(Arrays.asList("org.layer", "org.root"), complete("org."));
    }

    @Test
    public void includeModulesFromMultipleLayers() throws Exception {
        createLayerModules("base", "layer1");
        createLayerModules("another", "layer2");

        assertEquals(Arrays.asList("layer1", "layer2"), complete(null));
    }

    @Test
    public void ignoreDuplicatedFolderNames() throws Exception {
        createLayerModules("base", "module");
        createLayerModules("another", "module");

        assertEquals(Arrays.asList("module"), complete(null));
    }

    @Test
    public void escapeSpacesInFileNames() throws Exception {
        createModules("module 1", "module 2");

        assertEquals(Arrays.asList("module\\ 1", "module\\ 2"), complete(null));
    }

    @Test
    public void moduleNamesExcludeSlotName() throws Exception {
        createModules("foo/bar");

        assertEquals(Collections.emptyList(), complete("foo.bar."));
    }

    @Test
    public void ignorePatchesInLayers() throws Exception {
        createPatchedModules(ModuleNameTabCompleter.LAYERS_DIR, "abc", "foo/bar");

        assertEquals(Collections.emptyList(), complete(""));
    }

    @Test
    public void ignorePatchesInAddons() throws Exception {
        createPatchedModules(ModuleNameTabCompleter.ADDONS_DIR, "abc", "foo/bar");

        assertEquals(Collections.emptyList(), complete(""));
    }

    @Test
    public void excludeSystemModulesIfFlagIsSet() throws Exception {
        createLayerModules("another", "module");
        createModules("foo/bar");

        ModuleNameTabCompleter completer = ModuleNameTabCompleter.completer(new File(MODULES_DIR))
                .excludeNonModuleFolders(true)
                .includeSystemModules(false)
                .build();
        assertEquals(Arrays.asList("foo."), completer.complete(""));
    }

    @Test
    public void strictlyMatchNameUntilLastNamePart() throws Exception {
        createModules("foo/bar", "faa/bar2");

        assertEquals(Collections.emptyList(), complete("f."));
        assertEquals(Collections.emptyList(), complete("f.b"));
    }

    private static void createModules(String... names) throws IOException, XMLStreamException {
        doCreateModule(MODULES_DIR + "/", names);
    }

    private static void createLayerModules(String layerName, String... names) throws IOException, XMLStreamException {
        doCreateModule(MODULES_DIR  + "/" + ModuleNameTabCompleter.LAYERS_DIR + "/" + layerName + "/", names);
    }

    private static void createAddonsModules(String addonName, String... names) throws IOException, XMLStreamException {
        doCreateModule(MODULES_DIR + "/" + ModuleNameTabCompleter.ADDONS_DIR + "/" + addonName + "/", names);
    }

    private static void createPatchedModules(String base, String baseName, String... names) throws IOException, XMLStreamException {
        doCreateModule(MODULES_DIR + "/" + base + "/" + baseName + "/patches/", names);
    }

    private static void doCreateModule(String prefix, String[] names) throws IOException, XMLStreamException {
        for (String name : names) {
            new File(prefix + name + "/main").mkdirs();
            final File file = new File(prefix + name + "/main/module.xml");
            file.createNewFile();

            try(FileOutputStream fos = new FileOutputStream(file);
                OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {

                final FormattingXMLStreamWriter xmlWriter = new FormattingXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(writer));
                final ModuleConfigImpl moduleConfig = new ModuleConfigImpl(name.replace('/', '.'));
                moduleConfig.writeContent(xmlWriter, moduleConfig);
            }
        }
    }

    private List<String> complete(String buffer) {
        return completer.complete(buffer);
    }
}