package org.jboss.as.patching.installation;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.version.ProductConfig;

/**
 * The installation manager.
 *
 * @author Emanuel Muckenhuber
 */
public class InstallationManagerImpl extends InstallationManager {

    private final InstalledImage installedImage;
    private InstalledIdentity defaultIdentity;

    private List<File> moduleRoots;
    private List<File> bundleRoots;

    /**
     * This field is set to true when a patch is applied/rolled back at runtime.
     * It prevents another patch to be applied and overrides the modifications brought by the previous one
     * unless the process is restarted first
     *
     * This field has to be {@code static} in order to survive server reloads.
     */
    private static final AtomicBoolean restartRequired = new AtomicBoolean(false);

    public InstallationManagerImpl(InstalledImage installedImage, final List<File> moduleRoots, final List<File> bundlesRoots, final ProductConfig productConfig)
            throws IOException {
        this.installedImage = installedImage;

        this.moduleRoots = moduleRoots;
        this.bundleRoots = bundlesRoots;

        defaultIdentity = LayersFactory.load(installedImage, productConfig, moduleRoots, bundleRoots);
    }

    /**
     * This method returns the installed identity with the requested name and version.
     * If the product name is null, the default identity will be returned.
     *
     * If the product name was recognized and the requested version was not null,
     * the version comparison will take place. If the version of the currently installed product
     * doesn't match the requested one, the exception will be thrown.
     * If the requested version is null, the currently installed identity with the requested name
     * will be returned.
     *
     * If the product name was not recognized among the registered ones, a new installed identity
     * with the requested name will be created and returned. (This is because the patching system
     * is not aware of how many and what the patching streams there are expected).
     *
     * @param productName
     * @param productVersion
     * @return
     * @throws PatchingException
     */
    @Override
    public InstalledIdentity getInstalledIdentity(String productName, String productVersion) throws PatchingException {
        final String defaultIdentityName = defaultIdentity.getIdentity().getName();
        if(productName == null) {
            productName = defaultIdentityName;
        }

        final File productConf = new File(installedImage.getInstallationMetadata(), productName + Constants.DOT_CONF);
        final String recordedProductVersion;
        if(!productConf.exists()) {
            recordedProductVersion = null;
        } else {
            final Properties props = loadProductConf(productConf);
            recordedProductVersion = props.getProperty(Constants.CURRENT_VERSION);
        }

        if(defaultIdentityName.equals(productName)) {
            if(recordedProductVersion != null && !recordedProductVersion.equals(defaultIdentity.getIdentity().getVersion())) {
                // this means the patching history indicates that the current version is different from the one specified in the server's version module,
                // which could happen in case:
                // - the last applied CP didn't include the new version module or
                // - the version module version included in the last CP didn't match the version specified in the CP's metadata, or
                // - the version module was updated from a one-off, or
                // - the patching history was edited somehow
                // In any case, here I decided to rely on the patching history.
                defaultIdentity = loadIdentity(productName, recordedProductVersion);
            }
            if(productVersion != null && !defaultIdentity.getIdentity().getVersion().equals(productVersion)) {
                throw new PatchingException(PatchLogger.ROOT_LOGGER.productVersionDidNotMatchInstalled(
                        productName, productVersion, defaultIdentity.getIdentity().getVersion()));
            }
            return defaultIdentity;
        }

        if(recordedProductVersion != null && !Constants.UNKNOWN.equals(recordedProductVersion)) {
            if(productVersion != null) {
                if (!productVersion.equals(recordedProductVersion)) {
                    throw new PatchingException(PatchLogger.ROOT_LOGGER.productVersionDidNotMatchInstalled(productName, productVersion, recordedProductVersion));
                }
            } else {
                productVersion = recordedProductVersion;
            }
        }

        return loadIdentity(productName, productVersion);
    }

    private InstalledIdentity loadIdentity(String productName, String productVersion) throws PatchingException {
        try {
            return LayersFactory.load(installedImage,
                    new ProductConfig(productName, productVersion == null ? Constants.UNKNOWN : productVersion, null),
                    moduleRoots, bundleRoots);
        } catch (IOException e) {
            throw new PatchingException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(productName), e);
        }
    }

    private Properties loadProductConf(File productConf) throws PatchingException {
        final Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(productConf)){
            props.load(fis);
        } catch(IOException e) {
            throw new PatchingException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(productConf.getAbsolutePath()), e);
        }
        return props;
    }

    @Override
    public InstalledIdentity getDefaultIdentity() {
        return defaultIdentity;
    }

    /**
     * This method will return a list of installed identities for which
     * the corresponding .conf file exists under .installation directory.
     * The list will also include the default identity even if the .conf
     * file has not been created for it.
     */
    @Override
    public List<InstalledIdentity> getInstalledIdentities() throws PatchingException {

        List<InstalledIdentity> installedIdentities;

        final File metadataDir = installedImage.getInstallationMetadata();
        if(!metadataDir.exists()) {
            installedIdentities = Collections.singletonList(defaultIdentity);
        } else {
            final String defaultConf = defaultIdentity.getIdentity().getName() + Constants.DOT_CONF;
            final File[] identityConfs = metadataDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isFile() &&
                            pathname.getName().endsWith(Constants.DOT_CONF) &&
                            !pathname.getName().equals(defaultConf);
                }
            });
            if(identityConfs == null || identityConfs.length == 0) {
                installedIdentities = Collections.singletonList(defaultIdentity);
            } else {
                installedIdentities = new ArrayList<InstalledIdentity>(identityConfs.length + 1);
                installedIdentities.add(defaultIdentity);
                for(File conf : identityConfs) {
                    final Properties props = loadProductConf(conf);
                    String productName = conf.getName();
                    productName = productName.substring(0, productName.length() - Constants.DOT_CONF.length());
                    final String productVersion = props.getProperty(Constants.CURRENT_VERSION);

                    InstalledIdentity identity;
                    try {
                        identity = LayersFactory.load(installedImage, new ProductConfig(productName, productVersion, null), moduleRoots, bundleRoots);
                    } catch (IOException e) {
                        throw new PatchingException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(productName), e);
                    }
                    installedIdentities.add(identity);
                }
            }
        }
        return installedIdentities;
    }

    @Override
    public InstalledImage getInstalledImage() {
        return installedImage;
    }

    public boolean requiresRestart() {
        return restartRequired.get();
    }

    public boolean restartRequired() {
        return restartRequired.compareAndSet(false, true);
    }

    public void clearRestartRequired() {
        restartRequired.set(false);
    }
}
