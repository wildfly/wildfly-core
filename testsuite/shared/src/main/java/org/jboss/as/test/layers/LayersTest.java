/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.layers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.as.test.shared.TimeoutUtil;
import org.w3c.dom.Document;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.xml.sax.SAXException;

/**
 *
 * @author jdenise@redhat.com
 */
public class LayersTest {

    private static final boolean VALIDATE_INPUTS = Boolean.parseBoolean(System.getProperty("org.wildfly.layers.test.validate-inputs", "true"));

    private static final String MAVEN_REPO_LOCAL = "maven.repo.local";
    /** The name of the directory that contains the installation that was provisioned to
     *  to provide the feature pack's OOTB standalone.xml and its requirements.*/
    public static final String REFERENCE = "test-standalone-reference";
    /** The name of the directory that contains the installation that was provisioned to
     *  include 'all' layers. (It may not be 'all' as some are alternatives to others.) */
    public static final String ALL_LAYERS = "test-all-layers";
    private static final String END_LOG_SUCCESS = "WFLYSRV0025";
    private static final String END_LOG_FAILURE = "WFLYSRV0026";

    /**
     * Scan and check an installation.
     * @param root Path to installation
     * @param unreferenced The set of modules that are present in a default installation (with all modules
     * installed) but are not referenced from the module graph. They are not referenced because they are not used,
     * or they are only injected at runtime into deployment unit or are part of extension not present in the
     * default configuration (eg: deployment-scanner in core standalone.xml configuration). We are checking that
     * the default configuration (that contains all modules) doesn't have more unreferenced modules than this set. If
     * there are more it means that some new modules have been introduced and we must understand why (eg: a subsystem inject
     * a new module into a Deployment Unit, the subsystem must advertise it and the test must be updated with this new unreferenced module).
     * @param unused The set of modules that are OK to not be provisioned when all layers are provisioned.
     * If more modules than this set are not provisioned, it means that we are missing some modules and
     * an error occurs.
     * @throws Exception on failure
     *
     * @see #testLayersBoot(String)
     * @see #testLayersModuleUse(Set, ScanContext)
     * @see #testUnreferencedModules(Set, ScanContext)
     *
     * @deprecated the method aggregates the other public test methods, which should be used directly
     */
    @Deprecated(forRemoval = true)
    public static void test(String root, Set<String> unreferenced, Set<String> unused) throws Exception {
        testLayersBoot(root);
        ScanContext context = new ScanContext(root);
        testLayersModuleUse(unused, context);
        testUnreferencedModules(unreferenced, context);
    }

    /**
     * Checks that all modules that were provisioned in the @{code test-standalone-reference} installation
     * are also provisioned in @{test-all-layers}, except those included in the {@code unused} parameter's set.
     * The goal of this test is to check for new modules that should be provided by layers but are not
     * and to encourage inclusion of existing modules not used in a layer to have an associated layer.
     *
     * @param unused The set of modules that are OK to not be provisioned when all layers are provisioned.
     *               If more modules than this set are not provisioned, it means that we are missing some modules and
     *               an error occurs. If any of these modules are not present in the reference installation it means
     *               the value of this param is invalid and an error occurs.
     * @param scanContext contextual object that can and should be reused across invocations of methods in this class.
     *                    Creating a single context for a given root and reusing it for different tests saves
     *                    overhead involved in analyzing the installations in that root.
     * @throws Exception on failure
     */
    public static void testLayersModuleUse(Set<String> unused, ScanContext scanContext) throws Exception {

        scanContext.initialize();

        String root = scanContext.installationRoot;
        Result reference = scanContext.reference;
        Result allLayers = scanContext.allLayers;
        Map<String, Result> layers = scanContext.layers;

        assertNotNull("No " + REFERENCE + " installation found in " + root, reference);
        assertNotNull("No " + ALL_LAYERS + " installation found in " + root, allLayers);

        StringBuilder exceptionBuilder = new StringBuilder();
        AtomicBoolean empty = new AtomicBoolean(true);

        StringBuilder builder = new StringBuilder();
        appendResult("REFERENCE", reference, builder, null);
        // Format "all layers" result and compute the set of modules that have not been provisioned
        // against the reference.
        Set<String> deltaModules = appendResult("ALL LAYERS", allLayers, builder, reference);

        for (String k : layers.keySet()) {
            appendResult(k, layers.get(k), builder, reference);
        }


        // The only modules that are expected to be not provisioned are the un-used ones.
        // If more are not provisioned, then we are missing some modules.
        final Set<String> referenceAliases = reference.getAliases();  // don't require callers to include alias modules in 'unused'
        String missingRequired = listModules(deltaModules, m -> !unused.contains(m) && !referenceAliases.contains(m));
        if (!missingRequired.isEmpty()) {
            String error = "Some expected modules have not been provisioned in " + ALL_LAYERS;
            builder.append("#!!!!!ERROR ").append(error).append("\n");
            builder.append("error_missing_modules=").append(missingRequired).append("\n");
            appendExceptionMsg(exceptionBuilder, error + ": " + missingRequired, empty);
        }

        if (VALIDATE_INPUTS) {

            // Confirm that 'unused' modules actually exist in the reference installation
            final Set<String> allRefModules = reference.getModules();
            String missingDeclared = listModules(unused, m -> !allRefModules.contains(m));
            if (!missingDeclared.isEmpty()) {
                String error = "Some expected modules have not been provisioned in " + REFERENCE;
                builder.append("#!!!!!ERROR ").append(error).append("\n");
                builder.append("error_missing_modules=").append(missingDeclared).append("\n");
                appendExceptionMsg(exceptionBuilder, error + ": " + missingDeclared, empty);
            }

            // Confirm that 'unused' modules do not exist in the all-layers installation
            final Set<String> allLayersModules = allLayers.getModules();
            String wrongUnused = listModules(unused, allLayersModules::contains);
            if (!wrongUnused.isEmpty()) {
                String error = "Some expected to be unused modules have been provisioned in " + ALL_LAYERS;
                builder.append("#!!!!!ERROR ").append(error).append("\n");
                builder.append("error_missing_modules=").append(wrongUnused).append("\n");
                appendExceptionMsg(exceptionBuilder, error + ": " + wrongUnused, empty);
            }
        }

        File resFile = new File(root, "results.properties");
        Files.write(resFile.toPath(), builder.toString().getBytes());

        String exception = exceptionBuilder.toString();
        if (!exception.isEmpty()) {
            fail(exception);
        }
    }

    /**
     * Checks that all modules in the @{code test-standalone-reference} installation are referenced from
     * the installation root module or extension modules configured in standalone.xml, except those
     * included in the {@code unreferenced} parameter's set. The goal of this test is to prevent the
     * accumulation of 'orphaned' modules that are not usable.
     *
     * @param unreferenced The set of modules that are present in a default installation (with all modules
     * installed) but are not referenced from the module graph. They are not referenced because they are not used,
     * or they are only injected at runtime into deployment unit or are part of extension not present in the
     * default configuration (eg: deployment-scanner in core standalone.xml configuration). We are checking that
     * the default configuration (that contains all modules) doesn't have more unreferenced modules than this set. If
     * there are more it means that some new modules have been introduced, and we must understand why (eg: a subsystem injects
     * a new module into a Deployment Unit, the subsystem must advertise it and the test must be updated with this new unreferenced module).
     * @param scanContext contextual object that can and should be reused across invocations of methods in this class.
     *                    Creating a single context for a given root and reusing it for different tests saves
     *                    overhead involved in analyzing the installations in that root.
     * @throws Exception on failure
     */
    public static void testUnreferencedModules(Set<String> unreferenced, ScanContext scanContext) throws Exception {

        scanContext.initialize();

        String root = scanContext.installationRoot;
        Result reference = scanContext.reference;

        assertNotNull("No " + REFERENCE + " installation found in " + root, reference);

        StringBuilder exceptionBuilder = new StringBuilder();
        AtomicBoolean empty = new AtomicBoolean(true);

        // Check that the reference has no more un-referenced modules than the expected ones.
        Set<String> allUnReferenced = new HashSet<>(unreferenced);
        final Set<String> refUnreferenced = reference.getNotReferenced();
        final Set<String> referenceAliases = reference.getAliases(); // don't require callers to include alias modules in 'unreferenced'
        String invalidUnref = listModules(refUnreferenced, m -> !allUnReferenced.contains(m) && !referenceAliases.contains(m));
        if (!invalidUnref.isEmpty()) {
            appendExceptionMsg(exceptionBuilder, "Some unreferenced modules are unexpected " + invalidUnref, empty);
        }

        // Check that alias modules are not referenced
        String referencedAlias = listModules(referenceAliases, m -> !refUnreferenced.contains(m));
        if (!referencedAlias.isEmpty()) {
            appendExceptionMsg(exceptionBuilder, "Some alias modules are referenced " + referencedAlias, empty);
        }

        if (VALIDATE_INPUTS) {

            // Confirm that 'unreferenced' modules actually exist in the reference installation
            final Set<String> allRefModules = reference.getModules();
            String missingDeclared = listModules(allUnReferenced, m -> !allRefModules.contains(m));
            if (!missingDeclared.isEmpty()) {
                String error = "Some expected modules have not been provisioned in " + REFERENCE;
                appendExceptionMsg(exceptionBuilder, error + ": " + missingDeclared, empty);
            }

            // Confirm that 'unreferenced' modules are not referenced in the reference installation
            String wrongUnreferenced = listModules(unreferenced, m -> !refUnreferenced.contains(m));
            if (!wrongUnreferenced.isEmpty()) {
                String error = "Some expected to be unreferenced modules are referenced in " + REFERENCE;
                appendExceptionMsg(exceptionBuilder, error + ": " + wrongUnreferenced, empty);
            }
        }

        String exception = exceptionBuilder.toString();
        if (!exception.isEmpty()) {
            fail(exception);
        }

    }

    /**
     * @deprecated use {@link #testLayersBoot(String)}; this method just calls that one.
     */
    @Deprecated(forRemoval = true)
    public static void testExecution(String root) throws Exception {
        testLayersBoot(root);
    }


    /**
     * Checks that the installations found in the given {@code root} directory can all be started without errors, i.e.
     * with the {@code WFLYSRV0025} log message in the server's stdout stream.
     *
     * The @{code test-standalone-reference} installation is not tested as that kind of installation is heavily
     * tested elsewhere.
     *
     * @param root Installations root directory
     * @throws Exception on failure
     */
    public static void testLayersBoot(String root) throws Exception {
        File[] installations = new File(root).listFiles(File::isDirectory);
        assertNotNull("No installations found in " + root, installations);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (File f : installations) {
                Path installation = f.toPath();
                if (!f.getName().equals(REFERENCE)) {
                    checkExecution(executor, installation);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Utility method to combine various module name arrays into sets for use
     * as parameters to this class' methods.
     *
     * @param first the first array to combine. Cannot be {@code null}
     * @param others other arrays to combine. Can be {@code null}
     * @return a set containing all of the elements in the arrays
     */
    public static Set<String> concatArrays(String[] first, String[]... others) {
        if (others == null || others.length == 0) {
            return Set.of(first);
        } else {
            Stream<String> stream = Arrays.stream(first);
            for (String[] array : others) {
                stream = Stream.concat(stream, Arrays.stream(array));
            }
            return stream.collect(Collectors.toSet());
        }
    }

    private static void checkExecution(ExecutorService executor,
            Path installation) throws Exception {
        StringBuilder str = new StringBuilder();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(installation);
                    String localRepo = System.getProperty(MAVEN_REPO_LOCAL);
                    if (localRepo != null) {
                        builder.addJavaOption("-D" + MAVEN_REPO_LOCAL + "=" + localRepo);
                    } else {
                        System.out.println("Warning, no Maven local repository set.");
                    }
                    ProcessBuilder p = new ProcessBuilder(builder.build());
                    p.redirectErrorStream(true);
                    Process process = p.start();
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        boolean ended = false;
                        while ((line = reader.readLine()) != null) {
                            str.append(line).append("\n");
                            // We are only checking for server view on errors.
                            // Some other errors could occur (eg: multicast load-balancer on some platforms)
                            // but these errors are not caused by server configuration.
                            if (line.contains(END_LOG_FAILURE)) {
                                throw new Exception("Process for " + installation.getFileName() +
                                    " started with errors.");
                            } else {
                                if (line.contains(END_LOG_SUCCESS)) {
                                    ended = true;
                                    break;
                                }
                            }
                        }
                        if (!ended) {
                            throw new Exception("Process for " + installation.getFileName() +
                                    " not terminated properly.");
                        }
                    } finally {
                        process.destroyForcibly();
                        process.waitFor();
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
        try {
            executor.submit(r).get(TimeoutUtil.adjust(1), TimeUnit.MINUTES);
        } catch (Exception ex) {
            throw new Exception("Exception checking " + installation.getFileName().toString()
                    + "\n Server log \n" + str, ex);
        }
    }

    public static void recursiveDelete(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                        // ignore
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e == null) {
                        try {
                            Files.delete(dir);
                        } catch (IOException ex) {
                            // ignore
                        }
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Data holder used by methods in this class to hold information about scanned installations
     * located in a particular directory. Purpose is to avoid re-creating that information in
     * different methods.
     */
    public static final class ScanContext {
        private final String installationRoot;
        private Result reference;
        private Result allLayers;
        private Map<String, Result> layers;

        /**
         * Creates a new ScanContext
         * @param installationRoot path to the root directory containing installations to scan
         * */
        public ScanContext(String installationRoot) {
            this.installationRoot = installationRoot;
        }

        /** Performs a scan of the installation root, if one hasn't already been done. */
        private synchronized void initialize() throws Exception {
            if (layers == null) {
                File[] installations = new File(installationRoot).listFiles(File::isDirectory);
                assertNotNull("No installations found in " + installationRoot, installations);
                layers = new TreeMap<>();
                for (File f : installations) {
                    Path installation = f.toPath();
                    Result res = Scanner.scan(installation, getConf(installation));
                    if (f.getName().equals(REFERENCE)) {
                        reference = res;
                    } else if (f.getName().equals(ALL_LAYERS)) {
                        allLayers = res;
                    } else {
                        layers.put(f.getName(), res);
                    }
                }
            }
        }
    }

    private static Path getConf(Path home) {
        return Paths.get(home.toString(), "standalone", "configuration", "standalone.xml");
    }

    private static Set<String> appendResult(String title, Result result, StringBuilder builder, Result reference) {
        long sizeDelta;
        int numModulesdelta;
        Map<String, Set<String>> optionals = new TreeMap<>();
        builder.append("# ").append(title).append("\n");
        StringBuilder extensions = new StringBuilder();
        for (Result.ExtensionResult r : result.getExtensions()) {
            extensions.append(r.getModule()).append(",");
        }
        builder.append("extensions=").append(extensions).append("\n");
        Set<String> deltaModules = new TreeSet<>();
        if (reference != null) { // Compare against reference.
            sizeDelta = result.getSize() - reference.getSize();
            numModulesdelta = result.getModules().size() - reference.getModules().size();
            // Compute the set of unreferenced optionals that are only present in the checked installation.
            for (Map.Entry<String, Set<String>> entry : result.getUnresolvedOptional().entrySet()) {
                if (!reference.getUnresolvedOptional().containsKey(entry.getKey())) {
                    optionals.put(entry.getKey(), entry.getValue());
                }
            }

            // Compute the set of modules that have not been provisioned.
            for (String m : reference.getModules()) {
                if (!result.getModules().contains(m)) {
                    deltaModules.add(m);
                }
            }
            builder.append("size=").append(result.getSize()).append("\n");
            builder.append("size_delta=").append(sizeDelta).append("\n");
            builder.append("num_modules=").append(result.getModules().size()).append("\n");
            builder.append("num_modules_delta=").append(numModulesdelta).append("\n");
            builder.append("num_new_unresolved=").append(optionals.size()).append("\n");
        } else {
            optionals = result.getUnresolvedOptional();
            builder.append("size=").append(result.getSize()).append("\n");
            builder.append("num_modules=").append(result.getModules().size()).append("\n");
            builder.append("num_modules_not_referenced=").append(result.getNotReferenced().size()).append("\n");
            StringBuilder notReferenced = new StringBuilder();
            for (String s : result.getNotReferenced()) {
                notReferenced.append(s).append(",");
            }
            builder.append("unreferenced_modules=").append(notReferenced).append("\n");
            StringBuilder aliases = new StringBuilder();
            for (String s : result.getAliases()) {
                aliases.append(s).append(",");
            }
            builder.append("alias_modules=").append(aliases).append("\n");
            builder.append("num_unresolved=").append(optionals.size()).append("\n");
        }
        for (Map.Entry<String, Set<String>> entry : optionals.entrySet()) {
            StringBuilder roots = new StringBuilder();
            for (String s : entry.getValue()) {
                roots.append(s).append(",");
            }
            builder.append(entry.getKey()).append("=").append(roots).append("\n");
        }

        builder.append("num_not_provisioned_modules=").append(deltaModules.size()).append("\n");

        if (!deltaModules.isEmpty()) {
            StringBuilder mods = new StringBuilder();
            for (String s : deltaModules) {
                mods.append(s).append(",");
            }
            builder.append("not_provisioned_modules=").append(mods).append("\n");
        }

        builder.append("\n");
        return deltaModules;
    }

    private static void appendExceptionMsg(StringBuilder existing, String toAppend, AtomicBoolean empty) {
        if (!empty.getAndSet(false)) {
            existing.append('\n');
        }
        existing.append(toAppend);
    }

    private static String listModules(Set<String> set, Predicate<String> predicate) {
        return set.stream()
                .filter(predicate)
                .collect(Collectors.joining(","));
    }

    /**
     * Walks the modules directory of each installation getting as result the list of banned modules found.
     *
     * @param root             The root path of the installations.
     * @param bannedModuleConf A HashMap with the banned module as a key and an optional list with the installation names
     *                         that should be ignored if contains the banned module name.
     *
     * @return An empty Hash map if no banned modules were found or a hash map containing as keys the path where the
     * banned module was found and as values the banned module name.
     * @throws ParserConfigurationException if a DocumentBuilder cannot be created.
     * @throws IOException                  if an I/O error occurs or if the module.xml cannot be parsed.
     */
    public static HashMap<String, String> checkBannedModules(String root, HashMap<String, List<String>> bannedModuleConf) throws ParserConfigurationException, IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        HashMap<String, String> results = new HashMap<>();
        File[] installations = new File(root).listFiles(File::isDirectory);
        assertNotNull("No installations found in " + root, installations);
        for (File installation : installations) {
            Files.walkFileTree(installation.toPath().resolve("modules"), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals("module.xml")) {
                        Document doc;
                        try {
                            doc = dBuilder.parse(file.toFile());
                            doc.getDocumentElement().normalize();
                            String moduleName = doc.getDocumentElement().getAttribute("name");
                            List<String> ignoredInstallations = bannedModuleConf.get(moduleName);
                            if (ignoredInstallations != null && !ignoredInstallations.contains(installation.getName())) {
                                results.put(file.toString(), moduleName);
                            }
                        } catch (SAXException e) {
                            throw new IOException(e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return results;
    }
}
