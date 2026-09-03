/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;

/**
 * Validates deployment content source URLs and filesystem paths to prevent file exfiltration, SSRF attacks, and unauthorized
 * access to server-internal files via deployment operations.
 * <p>
 * Two modes of operation:
 * <ul>
 * <li><b>Default mode</b> (when {@code jboss.deployment.source.allow} is not set): enforces a URL scheme allowlist
 * ({@code http}, {@code https}, {@code file}, {@code ftp}), blocks filesystem paths under protected server directories, and
 * rejects HTTP/HTTPS URLs targeting link-local addresses (SSRF protection), unless
 * {@code jboss.deployment.source.allow.link-local.address} is set to {@code true}.</li>
 * <li><b>Regex override mode</b> (when {@code jboss.deployment.source.allow} is set): the property value is compiled as a Java
 * regular expression and matched against the full URL string. Scheme and blocked-path checks are bypassed. Link-local URLs are
 * still rejected unless {@code jboss.deployment.source.allow.link-local.address} is set to {@code true}.</li>
 * </ul>
 * <p>
 * Instances are created via {@link #forStandalone()} or {@link #forDomain()} factory methods and injected into deployment
 * handler constructors during server bootstrap.
 */
public final class DeploymentSourceValidator {

    /**
     * System property for regex override. When set and non-empty, replaces default scheme and path checks.
     */
    static final String PROP_ALLOW = "jboss.deployment.source.allow";

    /**
     * System property to allow link-local addresses. Defaults to {@code false}.
     */
    static final String PROP_ALLOW_LINK_LOCAL_ADDRESS = "jboss.deployment.source.allow.link-local.address";

    /**
     * Default allowed URL schemes in default mode.
     */
    private static final Set<String> DEFAULT_ALLOWED_SCHEMES = Set.of("http", "https", "file", "ftp");

    /**
     * Standalone-mode blocked-path system property names.
     */
    private static final String[] STANDALONE_BLOCKED_PROPERTIES = { "jboss.server.config.dir", "jboss.server.data.dir",
            "jboss.server.log.dir", "jboss.server.temp.dir", "jboss.server.content.dir", };

    /**
     * Domain HC-mode blocked-path system property names.
     */
    private static final String[] DOMAIN_BLOCKED_PROPERTIES = { "jboss.domain.config.dir", "jboss.domain.data.dir",
            "jboss.domain.log.dir", "jboss.domain.temp.dir", "jboss.domain.servers.dir", "jboss.domain.content.dir", };

    private final Set<Path> blockedPaths;

    /**
     * Creates a validator with the given configuration.
     */
    DeploymentSourceValidator(String[] blockedProperties, String crossModeDirName) {
        Set<Path> blockedPathsPaths = new HashSet<>();
        for (String prop : blockedProperties) {
            String value = System.getProperty(prop);
            if (value != null) {
                blockedPathsPaths.add(Path.of(value));
            }
        }
        String homeDir = System.getProperty("jboss.home.dir");
        if (homeDir != null) {
            blockedPathsPaths.add(Path.of(homeDir, crossModeDirName));
        }
        this.blockedPaths = Collections.unmodifiableSet(blockedPathsPaths);
    }

    /**
     * Returns the shared {@code DeploymentSourceValidator} instance for standalone server mode.
     *
     * @return the shared validator instance for standalone mode
     */
    public static DeploymentSourceValidator forStandalone() {
        return StandaloneHolder.INSTANCE;
    }

    /**
     * Returns the shared {@code DeploymentSourceValidator} instance for domain host-controller mode.
     *
     * @return the shared validator instance for domain HC mode
     */
    public static DeploymentSourceValidator forDomain() {
        return DomainHolder.INSTANCE;
    }

    private static final class StandaloneHolder {
        static final DeploymentSourceValidator INSTANCE = new DeploymentSourceValidator(STANDALONE_BLOCKED_PROPERTIES,
                "domain");
    }

    private static final class DomainHolder {
        static final DeploymentSourceValidator INSTANCE = new DeploymentSourceValidator(DOMAIN_BLOCKED_PROPERTIES,
                "standalone");
    }

    /**
     * Validates a URL string before it is opened by a deployment handler.
     * <p>
     * In regex override mode, the SSRF check (if enabled) runs first, then the regex. In default mode, the SSRF check runs
     * first, then the scheme check, then the blocked-path check.
     *
     * @param urlSpec the URL string to validate
     * @throws OperationFailedException if the URL is rejected by any validation rule
     */
    public void validateUrl(String urlSpec) throws OperationFailedException {
        if (!this.isLinkLocalAddressAllowed()) {
            checkSSRF(urlSpec);
        }

        Pattern overridePattern = getOverridePattern();
        if (overridePattern != null) {
            if (!overridePattern.matcher(urlSpec).matches()) {
                throw ControllerLogger.ROOT_LOGGER.deploymentSourceUrlNotMatchingAllowPattern(urlSpec);
            }
            return;
        }

        String scheme;
        try {
            scheme = new URL(urlSpec).getProtocol();
        } catch (MalformedURLException e) {
            // Cannot parse URL; let it fail downstream at url.openStream()
            return;
        }
        if (!DEFAULT_ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ENGLISH))) {
            throw ControllerLogger.ROOT_LOGGER.deploymentSourceSchemeNotAllowed(scheme,
                    String.join(", ", DEFAULT_ALLOWED_SCHEMES));
        }

        if ("file".equalsIgnoreCase(scheme)) {
            checkBlockedPath(Path.of(URI.create(urlSpec)));
        }
    }

    /**
     * Validates a filesystem path for unmanaged deployment content.
     * <p>
     * In regex override mode, matches the path against the configured pattern. In default mode, checks against blocked
     * directory trees. Do not check if file scheme is allowed.
     *
     * @param path the filesystem path to validate
     * @param relativeTo the base directory for relative paths (maybe {@code null} if the path is absolute)
     * @throws OperationFailedException if the path is rejected by any validation rule
     */
    public void validatePath(String path, String relativeTo) throws OperationFailedException {
        Path pathToCheck = relativeTo != null ? Paths.get(relativeTo).resolve(path) : Paths.get(path);
        Pattern overridePattern = getOverridePattern();
        if (overridePattern != null) {
            // If the path exists, get the real path to follow the symlinks
            if (Files.exists(pathToCheck)) {
                try {
                    Path realPathToCheck = pathToCheck.toRealPath();
                    if (!overridePattern.matcher(realPathToCheck.toString()).matches()) {
                        throw ControllerLogger.ROOT_LOGGER
                                .deploymentSourceUrlNotMatchingAllowPattern(realPathToCheck.toString());
                    }
                } catch (IOException e) {
                    // Only possible due to an IO error, at this point we already know that the Path exists
                    throw ControllerLogger.ROOT_LOGGER.cannotResolvePath(pathToCheck.toString(), e);
                }
            } else {
                String pathToCheckStr = pathToCheck.toAbsolutePath().normalize().toString();
                if (!overridePattern.matcher(pathToCheckStr).matches()) {
                    throw ControllerLogger.ROOT_LOGGER.deploymentSourceUrlNotMatchingAllowPattern(pathToCheckStr);
                }
            }
            return;
        }

        checkBlockedPath(pathToCheck);
    }

    /**
     * Checks if an HTTP/HTTPS URL targets a link-local address (SSRF protection).
     */
    private void checkSSRF(String urlSpec) throws OperationFailedException {
        URL url;
        try {
            url = new URL(urlSpec);
        } catch (MalformedURLException e) {
            return; // Not a valid URL for SSRF purposes; let downstream handle the error
        }
        String scheme = url.getProtocol();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return; // SSRF check only applies to http/https
        }
        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            return;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLinkLocalAddress()) {
                throw ControllerLogger.ROOT_LOGGER.deploymentSourceSsrfRejected(urlSpec);
            }
        } catch (UnknownHostException e) {
            throw ControllerLogger.ROOT_LOGGER.deploymentSourceSsrfRejected(urlSpec);
        }
    }

    /**
     * Checks if a filesystem path is under any blocked directory tree. Uses real path resolution to defeat symlink bypass.
     */
    private void checkBlockedPath(Path path) throws OperationFailedException {
        Path realPath = toRealPath(path);
        for (Path blockedPath : this.blockedPaths) {
            Path realBlockedPath = toRealPath(blockedPath);
            if (realPath.startsWith(realBlockedPath)) {
                throw ControllerLogger.ROOT_LOGGER.deploymentSourcePathBlocked(path.toString());
            }
        }
    }

    private Path toRealPath(Path path) throws OperationFailedException {
        if (Files.exists(path)) {
            try {
                return path.toRealPath();
            } catch (IOException e) {
                throw ControllerLogger.ROOT_LOGGER.cannotResolvePath(path.toString(), e);
            }
        }
        return path.toAbsolutePath().normalize();
    }

    private Pattern getOverridePattern() {
        Pattern result = null;
        String allowValue = System.getProperty(PROP_ALLOW, "");
        if (!allowValue.isEmpty()) {
            try {
                result = Pattern.compile(allowValue.trim());
            } catch (PatternSyntaxException e) {
                ControllerLogger.ROOT_LOGGER.invalidDeploymentSourceRegExpr(allowValue);
            }
        }
        return result;
    }

    private boolean isLinkLocalAddressAllowed() {
        return Boolean.parseBoolean(System.getProperty(PROP_ALLOW_LINK_LOCAL_ADDRESS, "false"));
    }
}
