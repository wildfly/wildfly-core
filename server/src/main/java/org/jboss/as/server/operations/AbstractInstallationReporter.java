/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.operations;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.ARCH;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.AVAILABLE_PROCESSORS;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.CPU;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.HOSTNAME;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.INSTANCE_ID;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.JAVA_VERSION;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.JVM_HOME;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.JVM_VENDOR;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.JVM_VERSION;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.OS;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PRODUCT_CHANNEL_VERSIONS;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PRODUCT_TYPE;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PRODUCT_COMMUNITY_IDENTIFIER;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PRODUCT_HOME;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PRODUCT_INSTALLATION_DATE;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PRODUCT_LAST_UPDATE;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PROJECT_TYPE;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.STANDALONE_DOMAIN_IDENTIFIER;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.interfaces.InetAddressUtil;
import org.jboss.as.controller.operations.common.ProcessEnvironment;
import org.jboss.as.controller.operations.global.GlobalInstallationReportHandler;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.cpu.ProcessorInfo;
import org.wildfly.security.manager.action.ReadEnvironmentPropertyAction;
import org.wildfly.security.manager.action.ReadPropertyAction;

/**
 * Base class to build a report of the current instance installation.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public abstract class AbstractInstallationReporter implements OperationStepHandler {

    protected static final String OPERATION_NAME = GlobalInstallationReportHandler.SUB_OPERATION_NAME;

    /**
     * Create a ModelNode representing the operating system the instance is running on.
     *
     * @return a ModelNode representing the operating system the instance is running on.
     * @throws OperationFailedException
     */
    private ModelNode createOSNode() throws OperationFailedException {
        String osName = getProperty("os.name");
        final ModelNode os = new ModelNode();
        if (osName != null && osName.toLowerCase(Locale.ENGLISH).contains("linux")) {
            try {
                os.set(GnuLinuxDistribution.discover());
            } catch (IOException ex) {
                throw new OperationFailedException(ex);
            }
        } else {
            os.set(osName);
        }
        return os;
    }

    /**
     * Create a ModelNode representing the JVM the instance is running on.
     *
     * @return a ModelNode representing the JVM the instance is running on.
     * @throws OperationFailedException
     */
    private ModelNode createJVMNode() throws OperationFailedException {
        ModelNode jvm = new ModelNode().setEmptyObject();
        jvm.get(NAME).set(getProperty("java.vm.name"));
        jvm.get(JAVA_VERSION).set(getProperty("java.vm.specification.version"));
        jvm.get(JVM_VERSION).set(getProperty("java.version"));
        jvm.get(JVM_VENDOR).set(getProperty("java.vm.vendor"));
        jvm.get(JVM_HOME).set(getProperty("java.home"));
        return jvm;
    }

    /**
     * Create a ModelNode representing the CPU the instance is running on.
     *
     * @return a ModelNode representing the CPU the instance is running on.
     * @throws OperationFailedException
     */
    private ModelNode createCPUNode() throws OperationFailedException {
        ModelNode cpu = new ModelNode().setEmptyObject();
        cpu.get(ARCH).set(getProperty("os.arch"));
        cpu.get(AVAILABLE_PROCESSORS).set(ProcessorInfo.availableProcessors());
        return cpu;
    }

    /**
     * Create a ModelNode representing the instance product and the plateform it is running on.
     *
     * @param context the operation context.
     * @param installation the instance installation configuration.
     * @return a ModelNode representing the instance product and the plateform it is running on.
     * @throws OperationFailedException
     */
    protected ModelNode createProductNode(OperationContext context, InstallationConfiguration installation) throws OperationFailedException {
        assert installation != null;
        assert installation.getEnvironment() != null;

        ProcessEnvironment environment = installation.getEnvironment();
        ModelNode product = new ModelNode().setEmptyObject();
        product.get(HOSTNAME).set(installation.getHostName());
        product.get(INSTANCE_ID).set(environment.getInstanceUuid().toString());
        PathAddress organizationAddress = PathAddress.EMPTY_ADDRESS;
        if (context.getProcessType().isHostController()) {
            organizationAddress = PathAddress.pathAddress(HOST, environment.getHostControllerName());
        }
        ModelNode root = context.readResourceFromRoot(organizationAddress, false).getModel();
        if (!root.hasDefined(ORGANIZATION) && context.getProcessType().isHostController()) {
            root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false).getModel();
        }
        if (root.hasDefined(ORGANIZATION)) {
            product.get(ORGANIZATION).set(root.get(ORGANIZATION).asString());
        } else if (root.hasDefined(DOMAIN_ORGANIZATION)) {
            product.get(ORGANIZATION).set(root.get(DOMAIN_ORGANIZATION).asString());
        }
        if (installation.getConfig() != null) {
            if (installation.getConfig().getProductName() != null) {
                product.get(PRODUCT_NAME).set(installation.getConfig().getProductName());
            }
            if (installation.getConfig().getProductVersion() != null) {
                product.get(PRODUCT_VERSION).set(installation.getConfig().getProductVersion());
            }
            if (installation.getConfig().isProduct()) {
                product.get(PRODUCT_COMMUNITY_IDENTIFIER).set(PRODUCT_TYPE);
            } else {
                product.get(PRODUCT_COMMUNITY_IDENTIFIER).set(PROJECT_TYPE);
            }
        }
        if (context.getProcessType() != ProcessType.SELF_CONTAINED) {
            String home = installation.getInstallationDir();
            if (home != null && !home.isEmpty()) {
                product.get(PRODUCT_HOME).set(home);

                String installationDate = installation.getInstallationDate();
                if (installationDate != null && !installationDate.isEmpty()) {
                    product.get(PRODUCT_INSTALLATION_DATE).set(installationDate);
                }
            }
        }
        String updateDate = installation.getLastUpdateDate();
        if (updateDate != null && !updateDate.isEmpty()) {
            product.get(PRODUCT_LAST_UPDATE).set(updateDate);
        }
        product.get(STANDALONE_DOMAIN_IDENTIFIER).set(context.getProcessType().name());
        product.get(OS).set(createOSNode());
        product.get(CPU).set(createCPUNode());
        product.get(JVM).set(createJVMNode());
        List<ModelNode> channelVersions = installation.getChannelVersions();
        if (channelVersions != null && !channelVersions.isEmpty()) {
            product.get(PRODUCT_CHANNEL_VERSIONS).set(channelVersions);
        }
        return product;
    }

    /**
     * Get a System property by its name.
     *
     * @param name the name of the wanted System property.
     * @return the System property value - null if it is not defined.
     */
    private String getProperty(String name) {
        return System.getSecurityManager() == null ? System.getProperty(name) : doPrivileged(new ReadPropertyAction(name));
    }

    /**
     * Enum to detect what is the current GnuLinux Distribution used.
     */
    private static enum GnuLinuxDistribution {
        ARCH("Arch", "/etc/arch-release"),
        CENTOS("CentOS", "/etc/redhat-release"),
        CENTOS_ALTERNATIVE("CentOS", "/etc/centos-release"),
        DEBIAN("Debian", "/etc/debian_version"),
        DEBIAN_ALTERNATIVE("Debian", "/etc/debian_release"),
        FEDORA("Fedora", "/etc/fedora-release"),
        GENTOO("Gentoo", "/etc/gentoo-release"),
        YELLOWDOG("YellowDog", "/etc/yellowdog-release"),
        KNOPPIX("Knoppix", "knoppix_version"),
        MAGEIA("Mageia", "/etc/mageia-release"),
        MANDRAKE("Mandrake", "/etc/mandrake-release"),
        MANDRIVA("Mandriva", "/etc/mandriva-release"),
        MANDRIVA_ALTERNATIVE("Mandriva", "/etc/version"),
        MINT("LinuxMint", "/etc/lsb-release"),
        PLD("PLD", "/etc/pld-release"),
        REDHAT("Red Hat", "/etc/redhat-release"),
        SLACKWARE("Slackware", "/etc/slackware-version"),
        SLACKWARE_ALTERNATIVE("Slackware", "/etc/slackware-release"),
        SUSE("SUSE", "/etc/SuSE-release"),
        OPEN_SUSE("openSUSE", "/etc/os-release"),
        SUSE_ALTERNATIVE("SUSE", "/etc/os-release"),
        UBUNTU("Ubuntu", "/etc/lsb-release"),
        PUPPY("Puppy", "/etc/puppyversion"),
        DEFAULT("Linux", "/etc/os-release");

        private static final GnuLinuxDistribution[] ALL = new GnuLinuxDistribution[]{
            CENTOS,
            CENTOS_ALTERNATIVE,
            MINT,
            UBUNTU,
            DEBIAN,
            FEDORA,
            GENTOO,
            KNOPPIX,
            MANDRAKE,
            MANDRIVA,
            PLD,
            REDHAT,
            SLACKWARE,
            SLACKWARE_ALTERNATIVE,
            SUSE,
            OPEN_SUSE,
            SUSE_ALTERNATIVE,
            YELLOWDOG,
            ARCH,
            DEBIAN_ALTERNATIVE,
            DEFAULT
        };

        private final String distributionName;
        private final Path releasePath;

        private GnuLinuxDistribution(String distributionName, String releasePath) {
            this.releasePath = new File(releasePath).toPath();
            this.distributionName = distributionName;
        }

        /**
         * Discover what is the underlying Gnu/Linux distribution.
         *
         * @return the distribution description (name and version) of the the underlying Gnu/Linux distribution.
         * @throws IOException
         */
        public static final String discover() throws IOException {
            for (GnuLinuxDistribution distribution : ALL) {
                if (Files.exists(distribution.releasePath)) {
                    Properties lines = new Properties();
                    try (final Reader reader = Files.newBufferedReader(distribution.releasePath, StandardCharsets.UTF_8)) {
                        lines.load(reader);
                        String name = lines.getProperty("DISTRIB_DESCRIPTION", lines.getProperty("PRETTY_NAME"));
                        if (name != null && !name.isEmpty()) {
                            return name.replace('\"', ' ').trim();
                        }
                    }
                }
            }
            return DEFAULT.distributionName;
        }
    }

    protected static final class InstallationConfiguration {

        private final ProcessEnvironment environment;
        private final ProductConfig config;
        private final ModelNode installerInfo;
        private final Path installationDir;

        public InstallationConfiguration(ProcessEnvironment environment, ProductConfig config, ModelNode installerInfo, Path installationDir) {
            assert environment != null;
            assert config != null;
            assert installerInfo != null;
            assert installationDir != null;

            this.environment = environment;
            this.config = config;
            this.installerInfo = installerInfo;
            this.installationDir = installationDir;
        }

        public String getInstallationDate() {
            return "";
        }

        ProcessEnvironment getEnvironment() {
            return environment;
        }

        ProductConfig getConfig() {
            return config;
        }

        String getInstallationDir() {
            if (Files.exists(installationDir)) {
                return installationDir.toAbsolutePath().toString();
            }
            return "";
        }

        String getLastUpdateDate() {
            if (installerInfo.get("result").isDefined()) {
                List<ModelNode> result = Operations.readResult(installerInfo).asList();
                for (ModelNode installerAtt : result) {
                    if (installerAtt.has("timestamp")) {
                        return installerAtt.get("timestamp").asString();
                    }
                }
            }
            return null;
        }

        List<ModelNode> getChannelVersions() {
            if (installerInfo.get("result").isDefined()) {
                List<ModelNode> result = Operations.readResult(installerInfo).asList();
                for (ModelNode installerAtt : result) {
                    if (installerAtt.has("channel-versions")) {
                        return installerAtt.get("channel-versions").asList();
                    }
                }
            }
            return null;
        }

        String getHostName() {
            String hostName = getEnv("HOSTNAME");
            if (hostName == null || hostName.isEmpty()) {
                hostName = getEnv("COMPUTERNAME");
                if (hostName == null || hostName.isEmpty()) {
                    hostName = InetAddressUtil.getLocalHostName();
                    if (hostName == null || hostName.isEmpty()) {
                        hostName = environment.getHostName();
                    }
                }
            }
            return hostName;
        }

        /**
         * Get a System property by its name.
         *
         * @param name the name of the wanted System property.
         * @return the System property value - null if it is not defined.
         */
        private String getEnv(String name) {
            return System.getSecurityManager() == null ? System.getenv(name) : doPrivileged(new ReadEnvironmentPropertyAction(name));
        }
    }
}
