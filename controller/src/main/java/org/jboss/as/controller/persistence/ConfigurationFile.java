/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.controller.persistence;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.persistence.ConfigurationPersister.SnapshotInfo;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Encapsulates the configuration file and manages its history
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry
 */
public class ConfigurationFile {

    /**
     * Policy controlling how to deal with the configuration file
     */
    public enum InteractionPolicy {
        /** The typical case; require that the specified file exist and allow updates to it */
        STANDARD(true, false, false, false),
        /** Delete the existing file if it exists and create a new empty file */
        DISCARD(false, false, true, false),
        /** Fail if there is an existing file and it is non-empty; otherwise create a new empty file */
        NEW(false, true, false, false),
        /** Require that the specified file exist, but do not update it */
        READ_ONLY(true, false, false, true);


        private final boolean requireExisting;
        private final boolean rejectExisting;
        private final boolean removeExisting;
        private final boolean readOnly;

        private InteractionPolicy(boolean requireExisting, boolean rejectExisting, boolean removeExisting, boolean readOnly) {
            this.requireExisting = requireExisting;
            this.rejectExisting = rejectExisting;
            this.removeExisting = removeExisting;
            this.readOnly = readOnly;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        private boolean isRequireExisting() {
            return requireExisting;
        }

        private boolean isRejectExisting() {
            return rejectExisting;
        }

        private boolean isRemoveExisting() {
            return removeExisting;
        }
    }

    private static final String LAST = "last";
    private static final String INITIAL = "initial";
    private static final String BOOT = "boot";

    private static final String LAST_SUFFIX = LAST + ".xml";
    private static final String INITIAL_SUFFIX = INITIAL + ".xml";
    private static final String ORIGINAL_SUFFIX = BOOT + ".xml";

    private static final int CURRENT_HISTORY_LENGTH = 100;
    private static final int HISTORY_DAYS = 30;
    private static final String CURRENT_HISTORY_LENGTH_PROPERTY = "jboss.config.current-history-length";
    private static final String HISTORY_DAYS_PROPERTY = "jboss.config.history-days";
    private static final String TIMESTAMP_STRING = "\\d\\d\\d\\d\\d\\d\\d\\d-\\d\\d\\d\\d\\d\\d\\d\\d\\d";
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(TIMESTAMP_STRING);
    private static final String TIMESTAMP_FORMAT = "yyyyMMdd-HHmmssSSS";
    private static final Pattern VERSION_PATTERN = Pattern.compile("v\\d+");
    private static final Pattern FILE_WITH_VERSION_PATTERN = Pattern.compile("\\S*\\.v\\d+\\.xml");
    private static final Pattern SNAPSHOT_XML = Pattern.compile(TIMESTAMP_STRING + "\\S*\\.xml");


    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicBoolean doneBootup = new AtomicBoolean();
    private final File configurationDir;
    private final String rawFileName;
    private final String bootFileName;
    // File from which boot operations should be parsed; null if currently undetermined
    private volatile File bootFile;
    /* Whether the next determination of the bootFile should use the .last file in history
       instead of the {@link #mainFile}. Only relevant with {@link InteractionPolicy#READ_ONLY} */
    private volatile boolean reloadUsingLast;
    // Whether {@link #bootFile has been reset from its first value
    private volatile boolean bootFileReset;
    private final File mainFile;
    private final File historyRoot;
    private final File currentHistory;
    private final File snapshotsDirectory;
    // Policy governing how to interact with the physical file
    private final InteractionPolicy interactionPolicy;
    /* Backup copy of the most recent configuration, stored in the history dir.
       May be used as {@link #bootFile}; see {@link #reloadUsingLast} */
    private volatile File lastFile;

    /**
     * Creates a new ConfigurationFile.
     *
     * @param configurationDir directory in which configuration files are stored. Cannot be {@code null} and must exist
     *                         and be a directory
     * @param rawName default name for configuration files of the type handled by this object.
     *                Cannot be {@code null} or an empty string
     * @param name user provided name of the configuration file to use
     * @param persistOriginal {@code true} if configuration modifications should be persisted back to the main
     *                                    configuration file; {@code false} if they should only be persisted
     *                                    to the configuration history directory
     */
    public ConfigurationFile(final File configurationDir, final String rawName, final String name, final boolean persistOriginal) {
        this(configurationDir, rawName, name, persistOriginal ? InteractionPolicy.STANDARD : InteractionPolicy.READ_ONLY);
    }

    /**
     * Creates a new ConfigurationFile.
     *
     * @param configurationDir directory in which configuration files are stored. Cannot be {@code null} and must exist
     *                         and be a directory
     * @param rawName default name for configuration files of the type handled by this object.
     *                Cannot be {@code null} or an empty string
     * @param name user provided name of the configuration file to use
     * @param interactionPolicy policy governing interaction with the configuration file
     */
    public ConfigurationFile(final File configurationDir, final String rawName, final String name,
                             final InteractionPolicy interactionPolicy) {
        if (!configurationDir.exists() || !configurationDir.isDirectory()) {
            throw ControllerLogger.ROOT_LOGGER.directoryNotFound(configurationDir.getAbsolutePath());
        }
        assert rawName != null && rawName.length() > 0;
        this.rawFileName = rawName;
        this.bootFileName = name != null ? name : rawName;
        this.configurationDir = configurationDir;
        this.historyRoot = new File(configurationDir, rawName.replace('.', '_') + "_history");
        this.currentHistory = new File(historyRoot, "current");
        this.snapshotsDirectory = new File(historyRoot, "snapshot");
        this.interactionPolicy = interactionPolicy == null ? InteractionPolicy.STANDARD : interactionPolicy;
        final File file = determineMainFile(rawName, name);
        try {
            this.mainFile = file.getCanonicalFile();
        } catch (IOException ioe) {
            throw ControllerLogger.ROOT_LOGGER.canonicalMainFileNotFound(ioe, file);
        }
    }

    /**
     * Reset so the next call to {@link #getBootFile()} will re-determine the appropriate file to use for
     * parsing boot operations.
     *
     * @param reloadUsingLast {@code true} if the next call to {@link #getBootFile()} should use the last file from
     *                                    the history. Only relevant if this object is not persisting changes
     *                                    back to the original source file
     */
    public synchronized void resetBootFile(boolean reloadUsingLast) {
        this.bootFile = null;
        this.bootFileReset = true;
        this.reloadUsingLast = reloadUsingLast;
    }

    /**
     * Gets the file from which boot operations should be parsed.
     * @return  the file. Will not be {@code null}
     */
    public File getBootFile() {
        if (bootFile == null) {
            synchronized (this) {
                if (bootFile == null) {
                    // If it's a reload and we're persisting our config, we boot from mainFile,
                    // as that's where we persist
                    if (bootFileReset && !interactionPolicy.isReadOnly()) {
                        // we boot from mainFile
                        bootFile = mainFile;
                    } else {
                        // It's either first boot or we're not persisting our config.
                        // So we need to figure out which file we're meant to boot from

                        String bootFileName = this.bootFileName;
                        if (interactionPolicy.isReadOnly() && reloadUsingLast) {
                            //If we were reloaded, and it is not a persistent configuration we want to use the last from the history
                            bootFileName = "last";
                        }
                        boolean usingRawFile = bootFileName.equals(rawFileName);
                        if (usingRawFile) {
                            bootFile = mainFile;
                        } else {
                            bootFile = determineBootFile(configurationDir, bootFileName);
                            try {
                                bootFile = bootFile.getCanonicalFile();
                            } catch (IOException ioe) {
                                throw ControllerLogger.ROOT_LOGGER.canonicalBootFileNotFound(ioe, bootFile);
                            }
                        }


                        if (!bootFile.exists()) {
                            if (!usingRawFile) { // TODO there's no reason usingRawFile should be an exception,
                                                 // but the test infrastructure stuff is built around an assumption
                                                 // that ConfigurationFile doesn't fail if test files are not
                                                 // in the normal spot
                                if (bootFileReset || interactionPolicy.isRequireExisting()) {
                                    throw ControllerLogger.ROOT_LOGGER.fileNotFound(bootFile.getAbsolutePath());
                                }
                            }
                            // Create it for the NEW and DISCARD cases
                            if (!bootFileReset && !interactionPolicy.isRequireExisting()) {
                                createBootFile(bootFile);
                            }
                        } else if (!bootFileReset) {
                            if (interactionPolicy.isRejectExisting() && bootFile.length() > 0) {
                                throw ControllerLogger.ROOT_LOGGER.rejectEmptyConfig(bootFile.getAbsolutePath());
                            } else if (interactionPolicy.isRemoveExisting() && bootFile.length() > 0) {
                                if (!bootFile.delete()) {
                                    throw ControllerLogger.ROOT_LOGGER.cannotDelete(bootFile.getAbsoluteFile());
                                }
                                createBootFile(bootFile);
                            }
                        } // else after first boot we want the file to exist
                    }
                }
            }
        }
        return bootFile;
    }

    public InteractionPolicy getInteractionPolicy() {
        return interactionPolicy;
    }

    /**
     * Given {@code name}, determine the intended main configuration file. Handles special cases, including
     * "last", "initial", "boot", "v1", and, if persistence to the original file is not supported, absolute paths.
     *
     * @param rawName default name for the main configuration file. Cannot be {@code null}
     * @param name user provided name of the main configuration, or {@code null} if not was provided
     */
    private File determineMainFile(final String rawName, final String name) {

        assert rawName != null;

        String mainName = null;

        if (name == null) {
            // Just use the default
            mainName = rawName;
        } else if (name.equals(LAST) || name.equals(INITIAL) || name.equals(BOOT)) {
            // Search for a *single* file in the configuration dir with suffix == name.xml
            mainName = findMainFileFromBackupSuffix(historyRoot, name);
        } else if (VERSION_PATTERN.matcher(name).matches()) {
            // Search for a *single* file in the currentHistory dir with suffix == name.xml
            mainName = findMainFileFromBackupSuffix(currentHistory, name);
        }

        if (mainName == null) {
            // Search for a *single* file in the snapshots dir with prefix == name.xml
            mainName = findMainFileFromSnapshotPrefix(name);
        }
        if (mainName == null) {
            // Try the basic case, where name is the name
            final File directoryFile = new File(configurationDir, name);
            if (directoryFile.exists()) {
                mainName = stripPrefixSuffix(name); // TODO what if the stripped name doesn't exist? And why would there be a file like configuration/standalone.last.xml?
            } else if (interactionPolicy.isReadOnly()) {
                // We allow absolute paths in this case
                final File absoluteFile = new File(name);
                if (absoluteFile.exists()) {
                    return absoluteFile;
                }
            }
        }
        if (mainName == null && !interactionPolicy.isRequireExisting()) {
            mainName = stripPrefixSuffix(name);
        }
        if (mainName != null) {
            return new File(configurationDir, new File(mainName).getName());
        }

        throw ControllerLogger.ROOT_LOGGER.mainFileNotFound(name != null ? name : rawName, configurationDir);
    }

    /**
     * Finds a single file in {@code searchDir} whose name ends with "{@code .backupType.xml}"
     * and returns its name with {@code .backupType} removed.
     *
     * @param searchDir  the directory to search
     * @param backupType the backup type; {@link #LAST}, {@link #BOOT}, {@link #INITIAL} or {@code v\d+}
     * @return the single file that meets the criteria. Will not return {@code null}
     * @throws IllegalStateException    if no files meet the criteria or more than one does
     * @throws IllegalArgumentException if they file that meets the criteria's full name is "{@code backupType.xml}"
     */
    private String findMainFileFromBackupSuffix(File searchDir, String backupType) {

        final String suffix = "." + backupType + ".xml";
        File[] files = null;
        if (searchDir.exists() && searchDir.isDirectory()) {
            files = searchDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(suffix);
                }

            });
        }

        if (files == null || files.length == 0) {
            throw ControllerLogger.ROOT_LOGGER.configurationFileNotFound(suffix, searchDir);
        } else if (files.length > 1) {
            throw ControllerLogger.ROOT_LOGGER.ambiguousConfigurationFiles(backupType, searchDir, suffix);
        }

        String matchName = files[0].getName();
        if (matchName.equals(suffix)) {
            throw ControllerLogger.ROOT_LOGGER.configurationFileNameNotAllowed(backupType);
        }
        String prefix = matchName.substring(0, matchName.length() - suffix.length());
        return prefix + ".xml";
    }

    /**
     * Finds a single file in the snapshots directory whose name starts with {@code prefix} and
     * returns its name with the prefix removed.
     *
     * @param prefix the prefix
     * @return the single file that meets the criterion {@code null} if none do
     * @throws IllegalStateException if more than one file meets the criteria
     */
    private String findMainFileFromSnapshotPrefix(final String prefix) {

        File[] files = null;
        if (snapshotsDirectory.exists() && snapshotsDirectory.isDirectory()) {
            files = snapshotsDirectory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(prefix);
                }

            });
        }

        if (files == null || files.length == 0) {
            return null;
        } else if (files.length > 1) {
            throw ControllerLogger.ROOT_LOGGER.ambiguousConfigurationFiles(prefix, snapshotsDirectory, prefix);
        }

        String matchName = files[0].getName();
        return matchName.substring(TIMESTAMP_FORMAT.length());
    }

    private String stripPrefixSuffix(String name) {
        if (SNAPSHOT_XML.matcher(name).matches()) {
            name = name.substring(TIMESTAMP_FORMAT.length());
        }
        if (FILE_WITH_VERSION_PATTERN.matcher(name).matches()) {
            int last = name.lastIndexOf('v');
            name = name.substring(0, last) + "xml";
        } else if (name.endsWith(LAST_SUFFIX)) {
            name = name.substring(0, name.length() - (LAST_SUFFIX).length()) + "xml";
        } else if (name.endsWith(ORIGINAL_SUFFIX)) {
            name = name.substring(0, name.length() - (ORIGINAL_SUFFIX).length()) + "xml";
        } else if (name.endsWith(INITIAL_SUFFIX)) {
            name = name.substring(0, name.length() - (INITIAL_SUFFIX).length()) + "xml";
        }
        return name;
    }

    private File determineBootFile(final File configurationDir, final String name) {
        final File directoryFile = new File(configurationDir, name);
        File result;
        if (name.equals(LAST) || name.equals(INITIAL) || name.equals(BOOT)) {
            result = addSuffixToFile(new File(historyRoot, mainFile.getName()), name);
        } else if (VERSION_PATTERN.matcher(name).matches()) {
            result = getVersionedFile(mainFile, name);
        } else {
            result = findSnapshotWithPrefix(name, false);
            if (result == null) {
                if (directoryFile.exists()) {
                    result = directoryFile;
                } else if (interactionPolicy.isReadOnly()) {
                    File absoluteFile = new File(name);
                    if (absoluteFile.exists()) {
                        result = absoluteFile;
                    }
                }
            }
        }

        if (result == null) {
            // We know directoryFile doesn't exist or the above logic would have set result to it.
            // But we use that as our last alternative. Let the caller object if non-existence is a problem
            result = directoryFile;
        }

        return result;
    }

    private static void createBootFile(File toCreate) {
        IOException cause = null;
        try {
            if (toCreate.createNewFile()) {
                return;
            }
        } catch (IOException e) {
            cause = e;
        }
        throw ControllerLogger.ROOT_LOGGER.cannotCreateEmptyConfig(toCreate.getAbsolutePath(), cause);
    }

    /** Gets the file to which modifications would be persisted, if this object is persisting changes outside the history directory */
    File getMainFile() {
        return mainFile;
    }

    /** Notification that boot has completed successfully and the configuration history should be updated */
    void successfulBoot() throws ConfigurationPersistenceException {
        synchronized (this) {
            if (doneBootup.get()) {
                return;
            }

            final File copySource;
            if (!interactionPolicy.isReadOnly()) {
                copySource = mainFile;
            } else {
                // TODO WFCORE-515 in the !persistOriginal case, mainFile may not be in the
                // configuration dir and writing to its dir may not be legal.
                // Why not use the configuration dir?
                copySource = new File(mainFile.getParentFile(), mainFile.getName() + ".boot");
                FilePersistenceUtils.deleteFile(copySource);
            }

            try {
                if (!bootFile.equals(copySource)) {
                    FilePersistenceUtils.copyFile(bootFile, copySource);
                }

                createHistoryDirectory();

                final File historyBase = new File(historyRoot, mainFile.getName());
                lastFile = addSuffixToFile(historyBase, LAST);
                final File boot = addSuffixToFile(historyBase, BOOT);
                final File initial = addSuffixToFile(historyBase, INITIAL);

                if (!initial.exists()) {
                    FilePersistenceUtils.copyFile(copySource, initial);
                }

                FilePersistenceUtils.copyFile(copySource, lastFile);
                FilePersistenceUtils.copyFile(copySource, boot);
            } catch (IOException e) {
                throw ControllerLogger.ROOT_LOGGER.failedToCreateConfigurationBackup(e, bootFile);
            } finally {
                if (interactionPolicy.isReadOnly()) {
                    //Delete the temporary file
                    try {
                        FilePersistenceUtils.deleteFile(copySource);
                    } catch (Exception ignore) {
                    }
                }
            }
            doneBootup.set(true);
        }
    }


    /** Backup the current version of the configuration to the versioned configuration history */
    void backup() throws ConfigurationPersistenceException {
        if (!doneBootup.get()) {
            return;
        }
        try {
            if (!interactionPolicy.isReadOnly()) {
                //Move the main file to the versioned history
                moveFile(mainFile, getVersionedFile(mainFile));
            } else {
                //Copy the Last file to the versioned history
                moveFile(lastFile, getVersionedFile(mainFile));
            }
            int seq = sequence.get();
            // delete unwanted backup files
            int currentHistoryLength = getInteger(CURRENT_HISTORY_LENGTH_PROPERTY, CURRENT_HISTORY_LENGTH, 0);
            if (seq > currentHistoryLength) {
                for (int k = seq - currentHistoryLength; k > 0; k--) {
                    File delete = getVersionedFile(mainFile, k);
                    if (! delete.exists()) {
                        break;
                    }
                    delete.delete();
                }
            }
        } catch (IOException e) {
            throw ControllerLogger.ROOT_LOGGER.failedToBackup(e, mainFile);
        }
    }

    /**
     * Commit the contents of the given temp file to either the main file, or, if we are not persisting
     * to the main file, to the .last file in the configuration history
     * @param temp temp file containing the latest configuration. Will not be {@code null}
     * @throws ConfigurationPersistenceException
     */
    void commitTempFile(File temp) throws ConfigurationPersistenceException {
        if (!doneBootup.get()) {
            return;
        }
        if (!interactionPolicy.isReadOnly()) {
            FilePersistenceUtils.moveTempFileToMain(temp, mainFile);
        } else {
            FilePersistenceUtils.moveTempFileToMain(temp, lastFile);
        }
    }

    /** Notification that the configuration has been written, and its current content should be stored to the .last file */
    void fileWritten() throws ConfigurationPersistenceException {
        if (!doneBootup.get() || interactionPolicy.isReadOnly()) {
            return;
        }
        try {
            FilePersistenceUtils.copyFile(mainFile, lastFile);
        } catch (IOException e) {
            throw ControllerLogger.ROOT_LOGGER.failedToBackup(e, mainFile);
        }
    }


    private void moveFile(final File file, final File backup) throws IOException {

        if (backup.exists()) {
            backup.delete();
        }

        FilePersistenceUtils.rename(file, backup);
    }

    String snapshot() throws ConfigurationPersistenceException {
        String name = getTimeStamp(new Date()) + mainFile.getName();
        File snapshot = new File(snapshotsDirectory, name);
        File source = interactionPolicy.isReadOnly() ? lastFile : mainFile;
        try {
            FilePersistenceUtils.copyFile(source, snapshot);
        } catch (IOException e) {
            throw ControllerLogger.ROOT_LOGGER.failedToTakeSnapshot(e, source, snapshot);
        }
        return snapshot.toString();
    }

    SnapshotInfo listSnapshots() {
        return new BackupSnapshotInfo();
    }

    void deleteSnapshot(final String prefix) {
        if (prefix.equals("all")) {
            if (snapshotsDirectory.exists() && snapshotsDirectory.isDirectory()) {
                for (String curr : snapshotsDirectory.list()) {
                    new File(snapshotsDirectory, curr).delete();
                }
            }

        } else {
            findSnapshotWithPrefix(prefix, true).delete();
        }
    }

    private File findSnapshotWithPrefix(final String prefix, boolean errorIfNoFiles) {
        List<String> names = new ArrayList<String>();
        if (snapshotsDirectory.exists() && snapshotsDirectory.isDirectory()) {
            for (String curr : snapshotsDirectory.list()) {
                if (curr.startsWith(prefix)) {
                    names.add(curr);
                }
            }
        }
        if (names.size() == 0 && errorIfNoFiles) {
            throw ControllerLogger.ROOT_LOGGER.fileNotFoundWithPrefix(prefix, snapshotsDirectory.getAbsolutePath());
        }
        if (names.size() > 1) {
            throw ControllerLogger.ROOT_LOGGER.ambiguousName(prefix, snapshotsDirectory.getAbsolutePath(), names);
        }

        return names.size() > 0 ? new File(snapshotsDirectory, names.get(0)) : null;
    }


    private void createHistoryDirectory() throws IOException {
        mkdir(this.historyRoot);
        mkdir(this.snapshotsDirectory);
        if (currentHistory.exists()) {
            if (!currentHistory.isDirectory()) {
                throw ControllerLogger.ROOT_LOGGER.notADirectory(currentHistory.getAbsolutePath());
            }

            //Copy any existing history directory to a timestamped backup directory
            Date date = new Date();
            File[] currentHistoryFiles = currentHistory.listFiles();
            if (currentHistoryFiles != null && currentHistoryFiles.length > 0) {
                String backupName = getTimeStamp(date);
                File old = new File(historyRoot, backupName);
                if (!new File(currentHistory.getAbsolutePath()).renameTo(old)) {
                    if (old.exists()) {
                        // AS7-5801. Unit tests sometimes fail on File.renameTo due to only having 100 ms
                        // precision on the timestamps we use for dir names on some systems. So, if that happens,
                        // we bump the timestamp once and try again before failing
                        date = new Date(date.getTime() + 100);
                        backupName = getTimeStamp(date);
                        old = new File(historyRoot, backupName);
                        if (!new File(currentHistory.getAbsolutePath()).renameTo(old)) {
                            throw ControllerLogger.ROOT_LOGGER.cannotRename(currentHistory.getAbsolutePath(), old.getAbsolutePath());
                        }
                    } else {
                        throw ControllerLogger.ROOT_LOGGER.cannotRename(currentHistory.getAbsolutePath(), old.getAbsolutePath());
                    }
                }
            }

            //Delete any old history directories
            int historyDays = getInteger(HISTORY_DAYS_PROPERTY, HISTORY_DAYS, 0);
            final String cutoffFileName = getTimeStamp(subtractDays(date, historyDays));
            for (String name : historyRoot.list()) {
                if (name.length() == cutoffFileName.length() && TIMESTAMP_PATTERN.matcher(name).matches() && name.compareTo(cutoffFileName) < 0) {
                    deleteRecursive(new File(historyRoot, name));
                }
            }
        }

        //Create the history directory
        currentHistory.mkdir();
        if (!currentHistory.exists()) {
            throw ControllerLogger.ROOT_LOGGER.cannotCreate(currentHistory.getAbsolutePath());
        }
    }

    private int getInteger(final String name, final int defaultValue, final int minimalValue) {
        int retVal = getInteger(name, defaultValue);
        return (retVal < minimalValue) ? defaultValue : retVal;
    }

    private int getInteger(final String name, final int defaultValue) {
        final String val = WildFlySecurityManager.getPropertyPrivileged(name, null);
        try {
            return val == null ? defaultValue : Integer.parseInt(val);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private void deleteRecursive(final File file) {
        if (file.isDirectory()) {
            for (String name : file.list()) {
                deleteRecursive(new File(file, name));
            }
        }
        if (!file.delete()) {
            throw ControllerLogger.ROOT_LOGGER.cannotDelete(file);
        }
    }

    private File getVersionedFile(final File file) {
        return getVersionedFile(file, sequence.incrementAndGet());
    }

    private File getVersionedFile(final File file, int i) {
        return addSuffixToFile(new File(currentHistory, file.getName()), "v" + i);
    }

    private File getVersionedFile(final File file, String versionString) {
        return addSuffixToFile(new File(currentHistory, file.getName()), versionString);
    }

    private File addSuffixToFile(final File file, final String suffix) {
        final String path = file.getAbsolutePath();
        int index = path.lastIndexOf(".");
        if (index == -1) {
            return new File(file.getAbsolutePath() + "." + suffix);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(path.substring(0, index));
        sb.append(".");
        sb.append(suffix);
        sb.append(path.substring(index));
        return new File(sb.toString());
    }

    private Date subtractDays(final Date date, final int days) {
        final Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        final int doy = calendar.get(Calendar.DAY_OF_YEAR);
        calendar.set(Calendar.DAY_OF_YEAR, doy - days);
        return calendar.getTime();
    }

    private static String getTimeStamp(final Date date) {
        final SimpleDateFormat sfd = new SimpleDateFormat(TIMESTAMP_FORMAT);
        return sfd.format(date);
    }

    private File mkdir(final File dir) {
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                throw ControllerLogger.ROOT_LOGGER.cannotCreate(historyRoot.getAbsolutePath());
            }
        } else if (!dir.isDirectory()) {
            throw ControllerLogger.ROOT_LOGGER.notADirectory(dir.getAbsolutePath());
        }
        return dir;
    }

    private class BackupSnapshotInfo implements SnapshotInfo {
        final ArrayList<String> names = new ArrayList<String>();

        public BackupSnapshotInfo() {
            for (String name : snapshotsDirectory.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return SNAPSHOT_XML.matcher(name).matches();
                }
            })) {
                names.add(name);
            }
        }

        @Override
        public String getSnapshotDirectory() {
            return snapshotsDirectory.getAbsolutePath();
        }

        @Override
        public List<String> names() {
            return names;
        }
    }


}
