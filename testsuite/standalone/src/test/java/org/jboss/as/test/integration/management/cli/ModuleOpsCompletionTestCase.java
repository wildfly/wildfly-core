package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@RunWith(WildflyTestRunner.class)
@Ignore("WFCORE-1821")
public class ModuleOpsCompletionTestCase {

    private static final String MODULE_NAME = "org.jboss.test.cli.climoduletest";

    private static CLIWrapper cli;
    private static File jarFile;

    @BeforeClass
    public static void before() throws Exception {
        cli = new CLIWrapper(true, null, System.in);

        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "Dummy.jar");
        jar.addClass(ModuleTestCase.class);
        jarFile = new File(TestSuiteEnvironment.getTmpDir() + File.separator + "Dummy.jar");
        new ZipExporterImpl(jar).exportTo(jarFile, true);
    }

    @AfterClass
    public static void after() throws Exception {
        cli.close();
    }

    @Test
    public void testModuleAddCompletionSuggestions() throws Exception {
        final CommandContext ctx = cli.getCommandContext();
        final Stream<String> allTopLevelDirs = listTopLevelModuleDirs().map(File::getName);

        // name on add operation should suggest all possible folders (not only valid module names)
        testSuggestion(ctx, allTopLevelDirs, "module add --name=", false);
        testSuggestion(ctx, Arrays.asList("org"), "module add --name=o", false);
        testSuggestion(ctx, Arrays.asList("org", "org."), "module add --name=org", false);
        try {
            // suggest folder without module descriptor
            new File(getModulePath(), "foo").mkdir();

            testSuggestion(ctx, Arrays.asList("foo"), "module add --name=f", false);
        } finally {
            new File(getModulePath(), "foo").delete();
        }
    }

    @Test
    public void testModuleRemoveCompletionSuggestions() throws Exception {
        final CommandContext ctx = cli.getCommandContext();
        // name on remove operation should only suggest valid module names from the modules root directory
        try {
            testAdd("main");

            testSuggestion(ctx, Arrays.asList("org."), "module remove --name=", false);
            testSuggestion(ctx, Arrays.asList("org."), "module remove --name=org", false);
        } finally {
            testRemove("main");
        }
    }

    @Test
    public void testModuleDependenciesCompletionSuggestions() throws Exception {
        final CommandContext ctx = cli.getCommandContext();
        final Stream<String> topLevelDirs = listTopLevelModuleDirs()
                                                .filter(this::isModuleTree)
                                                .map(f->f.getName() + ".");

        // dependencies should suggest all possible modules
        testSuggestion(ctx, topLevelDirs, "module add --name=foo --dependencies=", true);
        // completes started folder names
        testSuggestion(ctx, Arrays.asList("org."), "module add --name=foo --dependencies=o", true);
        testSuggestion(ctx, Arrays.asList("org."), "module add --name=foo --dependencies=bar,o", true);
    }

    private void testAdd(String slotName) throws Exception {
        // create a module
        cli.sendLine("module add --name=" + MODULE_NAME
                + ("main".equals(slotName) ? "" : " --slot=" + slotName)
                + " --resources=" + jarFile.getAbsolutePath()
                );
    }

    private void testRemove(String slotName) throws Exception {
        // remove the module
        cli.sendLine("module remove --name=" + MODULE_NAME
                + ("main".equals(slotName) ? "" : " --slot=" + slotName)
                );
    }

    private Stream<File> listTopLevelModuleDirs() {
        ArrayList<File> res = new ArrayList<>();
        for (File dir : getModulePath().listFiles(f -> !f.getName().equals("system"))) {
            res.add(dir);
        }

        if (new File(getModulePath(), "system/layers/").exists() ) {
            for (File layer : new File(getModulePath(), "system/layers/").listFiles()) {
                for (File dir : layer.listFiles()) {
                    res.add(dir);
                }
            }
        }

        if (new File(getModulePath(), "system/add-ons/").exists() ) {
            for (File layer : new File(getModulePath(), "system/add-ons/").listFiles()) {
                for (File dir : layer.listFiles()) {
                    res.add(dir);
                }
            }
        }

        return res.stream().sorted();
    }

    private boolean isModuleTree(File f) {
        try {
            return Files.find(f.toPath(), Integer.MAX_VALUE, (p, attr)->p.getFileName().toString().equals("module.xml"), FileVisitOption.FOLLOW_LINKS).count() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private void testSuggestion(CommandContext ctx, Stream<String> expected, String buffer, boolean allowMultipleValues) {
        testSuggestion(ctx, expected.collect(Collectors.toList()), buffer, allowMultipleValues);
    }

    private void testSuggestion(CommandContext ctx, List<String> expected, String buffer, boolean allowMultipleValues) {
        List<String> candidates = new ArrayList<>();
        final int offset = ctx.getDefaultCommandCompleter().complete(ctx, buffer, buffer.length(), candidates);

        assertEquals(expected, candidates);
        final int expectedIndex;
        if (allowMultipleValues && buffer.lastIndexOf('=') < buffer.lastIndexOf(',')) {
            expectedIndex = buffer.lastIndexOf(',') + 1;
        } else {
            expectedIndex = buffer.lastIndexOf('=') + 1;
        }
        assertEquals(expectedIndex, offset);
    }

    private File getModulePath() {
        String modulePath = TestSuiteEnvironment.getSystemProperty("module.path", null);
        if (modulePath == null) {
            String jbossHome = TestSuiteEnvironment.getSystemProperty("jboss.dist", null);
            if (jbossHome == null) {
                throw new IllegalStateException(
                        "Neither -Dmodule.path nor -Djboss.home were set");
            }
            modulePath = jbossHome + File.separatorChar + "modules";
        } else {
            modulePath = modulePath.split(File.pathSeparator)[0];
        }
        File moduleDir = new File(modulePath);
        if (!moduleDir.exists()) {
            throw new IllegalStateException(
                    "Determined module path does not exist");
        }
        if (!moduleDir.isDirectory()) {
            throw new IllegalStateException(
                    "Determined module path is not a dir");
        }
        return moduleDir;
    }
}
