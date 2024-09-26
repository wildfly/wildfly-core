/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.version;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;

/**
 * Common location to manage the AS based product name and version.
 *
 */
public class ProductConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String version;
    private String banner;
    private final String consoleSlot;
    private final Stability defaultStability;
    private final Set<Stability> stabilities;
    private boolean isProduct;

    public static ProductConfig fromFilesystemSlot(ModuleLoader loader, String home, Map<?, ?> providedProperties) {
        return new ProductConfig(loader, getProductConfProperties(home), providedProperties);
    }

    public static ProductConfig fromKnownSlot(String slot, ModuleLoader loader, Map<?, ?> providedProperties) {
        return new ProductConfig(loader, new ProductConfProps(slot), providedProperties);
    }
    private ProductConfig(ModuleLoader loader, ProductConfProps productConfProps, Map<?, ?> providedProperties) {
        String productName = null;
        String projectName = null;
        String productVersion = null;
        String consoleSlot = null;
        Stability defaultStability = Stability.COMMUNITY;
        Stability minStability = Stability.EXPERIMENTAL;
        Stability maxStability = Stability.DEFAULT;

        InputStream manifestStream = null;
        try {

            if (productConfProps.productModuleId != null) {
                Module module = loader.loadModule(productConfProps.productModuleId);

                manifestStream = module.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF");
                Manifest manifest = null;
                if (manifestStream != null) {
                    manifest = new Manifest(manifestStream);
                }

                if (manifest != null) {
                    productName = manifest.getMainAttributes().getValue("JBoss-Product-Release-Name");
                    productVersion = manifest.getMainAttributes().getValue("JBoss-Product-Release-Version");
                    consoleSlot = manifest.getMainAttributes().getValue("JBoss-Product-Console-Slot");
                    projectName = manifest.getMainAttributes().getValue("JBoss-Project-Release-Name");
                    String defaultStabilityValue = manifest.getMainAttributes().getValue("JBoss-Product-Stability");
                    if (defaultStabilityValue != null) {
                        defaultStability = Stability.fromString(defaultStabilityValue);
                    }
                    String minStabilityValue = manifest.getMainAttributes().getValue("JBoss-Product-Minimum-Stability");
                    if (minStabilityValue != null) {
                        minStability = Stability.fromString(minStabilityValue);
                    }
                }
                getBannerFile(module);
            }

            setSystemProperties(productConfProps.miscProperties, providedProperties);
        } catch (Exception e) {
            // Don't care
        } finally {
            safeClose(manifestStream);
        }
        isProduct = productName != null && !productName.isEmpty() && projectName == null;
        name = isProduct ? productName : projectName;
        version = productVersion;
        this.consoleSlot = consoleSlot;
        this.defaultStability = defaultStability;
        this.stabilities = Collections.unmodifiableSet(EnumSet.range(maxStability, minStability));
    }

    private void getBannerFile(Module module) throws IOException {
        try (final InputStream inputStream = module.getClassLoader().getResourceAsStream("banner.txt");
             final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int nRead;
            byte[] data = new byte[4096];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                outputStream.write(data, 0, nRead);
            }
            banner = System.lineSeparator() + outputStream.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Don't care
        }
    }

    private static String getProductConf(String home) {
        final String defaultVal = home + File.separator + "bin" + File.separator + "product.conf";
        PrivilegedAction<String> action = new PrivilegedAction<String>() {
            public String run() {
                String env = System.getenv("JBOSS_PRODUCT_CONF");
                if (env == null) {
                    env = defaultVal;
                }
                return env;
            }
        };

        return System.getSecurityManager() == null ? action.run() : AccessController.doPrivileged(action);
    }

    private static ProductConfProps getProductConfProperties(String home) {
        Properties props = new Properties();
        BufferedReader reader = null;
        try {
            reader = Files.newBufferedReader(Paths.get(getProductConf(home)), StandardCharsets.UTF_8);
            props.load(reader);
        } catch (Exception e) {
            // Don't care
        } finally {
            safeClose(reader);
        }
        return new ProductConfProps(props);
    }

    /** Solely for use in unit testing */
    public ProductConfig(final String productName, final String productVersion, final String consoleSlot) {
        this.name = productName;
        this.version = productVersion;
        this.consoleSlot = consoleSlot;
        this.defaultStability = Stability.COMMUNITY;
        this.stabilities = EnumSet.allOf(Stability.class).stream()
                .filter(this.defaultStability::enables)
                .collect(Collectors.toUnmodifiableSet());
        this.banner = null;
    }

    public String getProductName() {
        return name;
    }

    public String getProductVersion() {
        return version;
    }

    public boolean isProduct() {
        return isProduct;
    }

    public String getConsoleSlot() {
        return consoleSlot;
    }

    /**
     * Returns the presumed stability level of this product.
     * @return a stability level
     */
    public Stability getDefaultStability() {
        return this.defaultStability;
    }

    /**
     * Returns the set of permissible stability levels for this product.
     * @return a set of stability levels
     */
    public Set<Stability> getStabilitySet() {
        return this.stabilities;
    }

    /**
     * The product ASCII banner defined in the 'banner.txt' resource, using \n as line ending.
     * @return the ASCII banner.
     */
    public String getBanner() {
        return banner;
    }

    public String getPrettyVersionString() {
        if (name != null) {
            return String.format("%s %s (WildFly Core %s)", name, version, Version.AS_VERSION);
        }
        if (Version.UNKNOWN_CODENAME.equals(Version.AS_RELEASE_CODENAME)) {
            return String.format("WildFly Core %s", Version.AS_VERSION);
        }
        return String.format("WildFly Core %s \"%s\"", Version.AS_VERSION, Version.AS_RELEASE_CODENAME);
    }

    public String resolveVersion() {
        return version != null ? version : Version.AS_VERSION;
    }

    public String resolveName() {
        return name != null ? name : "WildFly";
    }

    public static String getPrettyVersionString(final String name, String version1, String version2) {
        if(name != null) {
            return String.format("JBoss %s %s (WildFly %s)", name, version1, version2);
        }
        return String.format("WildFly %s \"%s\"", version1, version2);
    }

    private void setSystemProperties(final Properties propConfProps, final Map providedProperties) {
        if (propConfProps.size() == 0) {
            return;
        }

        PrivilegedAction<Void> action = new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                for (Map.Entry<Object, Object> entry : propConfProps.entrySet()) {
                    String key = (String)entry.getKey();
                    if (!key.equals("slot") && System.getProperty(key) == null) {
                        //Only set the property if it was not defined by other means
                        //System properties defined in standalone.xml, domain.xml or host.xml will overwrite
                        //this as specified in https://issues.jboss.org/browse/AS7-6380

                        System.setProperty(key, (String)entry.getValue());

                        //Add it to the provided properties used on reload by the server environment
                        providedProperties.put(key, entry.getValue());
                    }
                }
                return null;
            }
        };
        if (System.getSecurityManager() == null) {
            action.run();
        } else {
            AccessController.doPrivileged(action);
        }
    }

    private static void safeClose(Closeable c) {
        if (c != null) try {
            c.close();
        } catch (Throwable ignored) {}
    }

    private static class ProductConfProps {
        private final Properties miscProperties;
        private final ModuleIdentifier productModuleId;

        private ProductConfProps(String slot) {
            this.productModuleId = slot == null ? null : ModuleIdentifier.create("org.jboss.as.product", slot);
            this.miscProperties = new Properties();
        }

        private ProductConfProps(Properties properties) {
            this(properties.getProperty("slot"));
            if (productModuleId != null) {
                properties.remove("slot");
            }
            if (!properties.isEmpty()) {
                for (String key : properties.stringPropertyNames()) {
                    this.miscProperties.setProperty(key, properties.getProperty(key));
                }
            }
        }
    }

}
