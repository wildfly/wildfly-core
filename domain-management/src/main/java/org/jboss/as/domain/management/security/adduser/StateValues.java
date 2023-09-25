/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.domain.management.security.adduser.AddUser.FileMode;
import org.jboss.as.domain.management.security.adduser.AddUser.Interactiveness;
import org.jboss.as.domain.management.security.adduser.AddUser.RealmMode;

/**
 * Place holder object to pass between the state
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class StateValues {
    private final RuntimeOptions options;
    private AddUser.Interactiveness howInteractive = Interactiveness.INTERACTIVE;
    private AddUser.RealmMode realmMode = RealmMode.DEFAULT;
    private String realm;
    private String userName;
    private String password;
    private AddUser.FileMode fileMode = FileMode.UNDEFINED;
    private String groups;
    private boolean existingUser = false;
    private List<File> userFiles;
    private List<File> groupFiles;
    private Set<String> enabledKnownUsers = new HashSet<String>();
    private Set<String> disabledKnownUsers = new HashSet<String>();
    private Map<String, String> knownGroups;

    public StateValues() {
        options = new RuntimeOptions();
    }

    public StateValues(final RuntimeOptions options) {
        this.options = options;
    }

    public boolean isSilentOrNonInteractive() {
        return (howInteractive == AddUser.Interactiveness.NON_INTERACTIVE) || isSilent();
    }

    public void setHowInteractive(AddUser.Interactiveness howInteractive) {
        this.howInteractive = howInteractive;
    }


    public boolean isSilent() {
        return (howInteractive == AddUser.Interactiveness.SILENT);
    }

    public boolean isInteractive() {
        return howInteractive == Interactiveness.INTERACTIVE;
    }

    public boolean isExistingUser() {
        return existingUser;
    }

    public void setExistingUser(boolean existingUser) {
        this.existingUser = existingUser;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public RealmMode getRealmMode() {
        return realmMode;
    }

    public void setRealmMode(final RealmMode realmMode) {
        this.realmMode = realmMode;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public AddUser.FileMode getFileMode() {
        return fileMode;
    }

    public void setFileMode(AddUser.FileMode fileMode) {
        this.fileMode = fileMode;
    }

    public String getGroups() {
        return groups;
    }

    public void setGroups(String groups) {
        this.groups = groups;
    }

    public List<File> getUserFiles() {
        return userFiles;
    }

    public void setUserFiles(List<File> userFiles) {
        this.userFiles = userFiles;
    }

    public List<File> getGroupFiles() {
        return groupFiles;
    }

    public void setGroupFiles(List<File> groupFiles) {
        this.groupFiles = groupFiles;
    }

    public boolean groupPropertiesFound() {
        return groupFiles != null && !groupFiles.isEmpty();
    }

    public Set<String> getEnabledKnownUsers() {
        return enabledKnownUsers;
    }

    public void setEnabledKnownUsers(Set<String> enabledKnownUsers) {
        this.enabledKnownUsers = enabledKnownUsers;
    }

    public Set<String> getDisabledKnownUsers() {
        return disabledKnownUsers;
    }

    public void setDisabledKnownUsers(Set<String> disabledKnownUsers) {
        this.disabledKnownUsers = disabledKnownUsers;
    }

    public boolean isExistingEnabledUser() {
        return getEnabledKnownUsers().contains(getUserName());
    }

    public boolean isExistingDisabledUser() {
        return getDisabledKnownUsers().contains(getUserName());
    }

    public Map<String, String> getKnownGroups() {
        return knownGroups;
    }

    public void setKnownGroups(Map<String, String> knownGroups) {
        this.knownGroups = knownGroups;
    }

    public RuntimeOptions getOptions() {
        return options;
    }

}
