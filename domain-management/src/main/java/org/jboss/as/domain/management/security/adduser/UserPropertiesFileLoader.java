/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.controller.services.path.PathManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

/**
 * An extension of {@link PropertiesFileLoader} that is realm aware.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class UserPropertiesFileLoader extends PropertiesFileLoader {

    private static final String REALM_COMMENT_PREFIX = "$REALM_NAME=";
    private static final String REALM_COMMENT_SUFFIX = "$";
    private static final String REALM_COMMENT_COMMENT = " This line is used by the add-user utility to identify the realm name already used in this file.";

    private String realmName;
    private List<String> enabledUserNames = new ArrayList<String>();
    private List<String> disabledUserNames = new ArrayList<String>();

    /*
     * State maintained during persistence.
     */
    private boolean realmWritten = false;

    /*
     * End of state maintained during persistence.
     */

    public UserPropertiesFileLoader(final Supplier<PathManager> pathManagerSupplier, final String path, final String relativeTo) {
        super(pathManagerSupplier, path, relativeTo);
    }

    public UserPropertiesFileLoader(final String path) {
        super(null, path, null);
    }

    public String getRealmName() throws IOException {
        loadAsRequired();

        return realmName;
    }

    public void setRealmName(final String realmName) {
        this.realmName = realmName;
    }

    public List<String> getUserNames() throws IOException {
        loadAsRequired();

        List<String> userNames = new ArrayList<String>();
        userNames.addAll(enabledUserNames);
        userNames.addAll(disabledUserNames);
        return userNames;
    }

    public List<String> getEnabledUserNames() throws IOException {
        loadAsRequired();

        return enabledUserNames;
    }

    public List<String> getDisabledUserNames() throws IOException {
        loadAsRequired();

        return disabledUserNames;
    }

    @Override
    protected void load() throws IOException {
        super.load();

        String realmName = null;
        BufferedReader br = null;
        disabledUserNames.clear();
        enabledUserNames.clear();
        try {
            br = Files.newBufferedReader(propertiesFile.toPath(), StandardCharsets.UTF_8);
            String currentLine;
            while ((currentLine = br.readLine()) != null) {
                final String trimmed = currentLine.trim();
                final Matcher matcher = PROPERTY_PATTERN.matcher(currentLine.trim());
                if (matcher.matches()) {
                    final String username = cleanKey(matcher.group(1));
                    if (trimmed.startsWith(COMMENT_PREFIX)) {
                        disabledUserNames.add(username);
                    } else {
                        enabledUserNames.add(username);
                    }
                }
                if (trimmed.startsWith(COMMENT_PREFIX) && trimmed.contains(REALM_COMMENT_PREFIX)) {
                    int start = trimmed.indexOf(REALM_COMMENT_PREFIX) + REALM_COMMENT_PREFIX.length();
                    int end = trimmed.indexOf(REALM_COMMENT_SUFFIX, start);
                    if (end > -1) {
                        if (realmName != null) {
                            throw ROOT_LOGGER.multipleRealmDeclarations(propertiesFile.getAbsolutePath());
                        }
                        realmName = trimmed.substring(start, end);
                    }
                }
            }
        } finally {
            safeClose(br);
        }
        this.realmName = realmName;
    }

    @Override
    protected void beginPersistence() throws IOException {
        super.beginPersistence();

        realmWritten = false;
    }

    @Override
    protected void write(BufferedWriter writer, String line, boolean newLine) throws IOException {
        if (realmWritten == false) {
            // Once we know it has been written we can skip subsequent checks.
            String trimmed = line.trim();
            if (trimmed.startsWith(COMMENT_PREFIX) && trimmed.contains(REALM_COMMENT_PREFIX)) {
                realmWritten = true;
            }
        }
        // We currently do not support replacing the realm name as that would involve new passwords for all current users.

        super.write(writer, line, newLine);
    }

    @Override
    protected void endPersistence(BufferedWriter writer) throws IOException {
        // Allow super class to write any remaining users first.
        super.endPersistence(writer);

        if (realmWritten == false) {
            writeRealm(writer, realmName);
        }
    }

    /**
     * Remove the realm name block.
     *
     * @see PropertiesFileLoader#addLineContent(java.io.BufferedReader, java.util.List, String)
     */
    @Override
    protected void addLineContent(BufferedReader bufferedFileReader, List<String> content, String line) throws IOException {
        // Is the line an empty comment "#" ?
        if (line.startsWith(COMMENT_PREFIX) && line.length() == 1) {
            String nextLine = bufferedFileReader.readLine();
            if (nextLine != null) {
                // Is the next line the realm name "#$REALM_NAME=" ?
                if (nextLine.startsWith(COMMENT_PREFIX) && nextLine.contains(REALM_COMMENT_PREFIX)) {
                    // Realm name block detected!
                    // The next line must be and empty comment "#"
                    bufferedFileReader.readLine();
                    // Avoid adding the realm block
                } else {
                    // It's a user comment...
                    content.add(line);
                    content.add(nextLine);
                }
            } else {
                super.addLineContent(bufferedFileReader, content, line);
            }
        } else {
            super.addLineContent(bufferedFileReader, content, line);
        }
    }

    private void writeRealm(final BufferedWriter bw, final String realmName) throws IOException {
        bw.append(COMMENT_PREFIX);
        bw.newLine();
        bw.append(COMMENT_PREFIX);
        bw.append(REALM_COMMENT_PREFIX);
        bw.append(realmName);
        bw.append(REALM_COMMENT_SUFFIX);
        bw.append(REALM_COMMENT_COMMENT);
        bw.newLine();
        bw.append(COMMENT_PREFIX);
        bw.newLine();
    }

}
