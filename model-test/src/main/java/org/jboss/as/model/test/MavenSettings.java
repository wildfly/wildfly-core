/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.model.test;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.wildfly.common.xml.XMLInputFactoryUtil;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
final class MavenSettings {
    private static final Object settingLoaderMutex = new Object();

    private static volatile MavenSettings mavenSettings;

    private Path localRepository = null;
    private final List<Repository> remoteRepositories = new LinkedList<>();
    private final Map<String, Profile> profiles = new HashMap<>();
    private final List<String> activeProfileNames = new LinkedList<>();
    private final Map<String, Server> servers = new HashMap<>();

    MavenSettings() {
        configureDefaults();
    }

    static MavenSettings getSettings() {
        if (mavenSettings != null) {
            return mavenSettings;
        }
        synchronized (settingLoaderMutex) {
            if (mavenSettings != null) {
                return mavenSettings;
            }

            return mavenSettings = doIo(() -> {
                MavenSettings settings = new MavenSettings();

                Path m2 = Paths.get(System.getProperty("user.home"), ".m2");
                Path settingsPath = m2.resolve("settings.xml");

                if (Files.notExists(settingsPath)) {
                    String mavenHome = System.getenv("M2_HOME");
                    if (mavenHome != null) {
                        settingsPath = Paths.get(mavenHome, "conf", "settings.xml");
                    }
                }
                if (Files.exists(settingsPath)) {
                    parseSettingsXml(settingsPath, settings);
                }
                if (settings.getLocalRepository() == null) {
                    Path repository = m2.resolve("repository");
                    settings.setLocalRepository(repository);
                }
                settings.resolveActiveSettings();
                return settings;
            });
        }
    }

    private static <T> T doIo(PrivilegedExceptionAction<T> action) throws RuntimeException {
        try {
            return AccessController.doPrivileged(action);
        } catch (PrivilegedActionException e) {
            try {
                throw e.getCause();
            } catch (IOException | RuntimeException | Error e1) {
                throw new RuntimeException(e1);
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t);
            }
        }
    }

    static MavenSettings parseSettingsXml(Path settings, MavenSettings mavenSettings) throws IOException {
        try {


            //reader.setFeature(FEATURE_PROCESS_NAMESPACES, false);
            InputStream source = Files.newInputStream(settings, StandardOpenOption.READ);
            XMLStreamReader reader = XMLInputFactoryUtil.create().createXMLStreamReader(source);

            int eventType;
            while ((eventType = reader.next()) != END_DOCUMENT) {
                switch (eventType) {
                    case START_ELEMENT: {
                        switch (reader.getLocalName()) {
                            case "settings": {
                                parseSettings(reader, mavenSettings);
                                break;
                            }
                        }
                    }
                    default: {
                        break;
                    }
                }
            }
            return mavenSettings;
        } catch (XMLStreamException e) {
            throw new IOException("Could not parse maven settings.xml", e);
        }

    }

    private static void parseSettings(final XMLStreamReader reader, MavenSettings mavenSettings) throws XMLStreamException, IOException {
        int eventType;
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            switch (eventType) {
                case END_ELEMENT: {
                    return;
                }
                case START_ELEMENT: {
                    switch (reader.getLocalName()) {
                        case "localRepository": {
                            String localRepository = reader.getElementText();
                            if (localRepository != null && !localRepository.trim().isEmpty()) {
                                mavenSettings.setLocalRepository(Paths.get(localRepository));
                            }
                            break;
                        }
                        case "profiles": {
                            while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                                if (eventType == START_ELEMENT) {
                                    switch (reader.getLocalName()) {
                                        case "profile": {
                                            parseProfile(reader, mavenSettings);
                                            break;
                                        }
                                    }
                                } else {
                                    break;
                                }
                            }
                            break;
                        }
                        case "activeProfiles": {
                            while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                                if (eventType == START_ELEMENT) {
                                    switch (reader.getLocalName()) {
                                        case "activeProfile": {
                                            mavenSettings.addActiveProfile(reader.getElementText());
                                            break;
                                        }
                                    }
                                } else {
                                    break;
                                }

                            }
                            break;
                        }
                        case "servers":
                            while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                                if (eventType == START_ELEMENT) {
                                    switch (reader.getLocalName()) {
                                        case "server": {
                                            parseServer(reader, mavenSettings);
                                            break;
                                        }
                                    }
                                } else {
                                    break;
                                }
                            }
                            break;
                        default: {
                            skip(reader);
                        }
                    }
                    break;
                }
                default: {
                    throw new XMLStreamException("Unexpected content", reader.getLocation());
                }
            }
        }
        throw new XMLStreamException("Unexpected end of document", reader.getLocation());
    }

    private static void parseProfile(final XMLStreamReader reader, MavenSettings mavenSettings) throws XMLStreamException, IOException {
        int eventType;
        Profile profile = new Profile();
        boolean activeByDefault = false;
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            if (eventType == START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "id": {
                        profile.setId(reader.getElementText());
                        break;
                    }
                    case "repositories": {
                        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                            if (eventType == START_ELEMENT) {
                                switch (reader.getLocalName()) {
                                    case "repository": {
                                        parseRepository(reader, profile);
                                        break;
                                    }
                                }
                            } else {
                                break;
                            }

                        }
                        break;
                    }
                    case "activation":
                        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                            if (eventType == START_ELEMENT) {
                                switch (reader.getLocalName()) {
                                    case "activeByDefault": {
                                        activeByDefault = Boolean.parseBoolean(reader.getElementText());
                                        break;
                                    }
                                }
                            } else {
                                break;
                            }

                        }
                        break;
                    default: {
                        skip(reader);
                    }
                }
            } else {
                break;
            }
        }
        mavenSettings.addProfile(profile);
        if (activeByDefault) {
            mavenSettings.addActiveProfile(profile.getId());
        }
    }

    private static void parseRepository(final XMLStreamReader reader, Profile profile) throws XMLStreamException, IOException {
        int eventType;
        Repository repository = new Repository();
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            if (eventType == START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "id": {
                        repository.setId(reader.getElementText());
                        break;
                    }
                    case "url": {
                        String url = reader.getElementText();
                        if (!url.endsWith("/")) {
                            url += "/";
                        }
                        repository.setUrl(url);
                        break;
                    }
                    default: {
                        skip(reader);
                    }
                }
            } else {
                break;
            }
        }
        profile.addRepository(repository);
    }

    private static void parseServer(final XMLStreamReader reader, MavenSettings mavenSettings) throws XMLStreamException, IOException {
        int eventType;
        Server server = new Server();
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            if (eventType == START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "id": {
                        server.setId(reader.getElementText());
                        break;
                    }
                    case "username": {
                        server.setUsername(reader.getElementText());
                        break;
                    }
                    case "password": {
                        server.setPassword(reader.getElementText());
                        break;
                    }
                    default: {
                        skip(reader);
                    }
                }
            } else {
                break;
            }
        }
        mavenSettings.addServer(server);
    }

    private static void skip(XMLStreamReader parser) throws XMLStreamException, IOException {
        if (parser.getEventType() != XMLStreamReader.START_ELEMENT) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XMLStreamReader.END_ELEMENT:
                    depth--;
                    break;
                case XMLStreamReader.START_ELEMENT:
                    depth++;
                    break;
            }
        }
    }

    private void configureDefaults() {
        //always add maven central
        remoteRepositories.add(new Repository("central", "https://repo1.maven.org/maven2/"));
        String localRepositoryPath = System.getProperty("localRepository");
        if (localRepositoryPath != null && !localRepositoryPath.trim().isEmpty()) {
            localRepository = Paths.get(localRepositoryPath);
        }
        localRepositoryPath = System.getProperty("maven.repo.local");
        if (localRepositoryPath != null && !localRepositoryPath.trim().isEmpty()) {
            localRepository = Paths.get(localRepositoryPath);
        }
        String remoteRepository = System.getProperty("remote.maven.repo");
        if (remoteRepository != null) {
            int i = 0;
            for (String repo : remoteRepository.split(",")) {
                if (!repo.endsWith("/")) {
                    repo += "/";
                }
                remoteRepositories.add(new Repository("repo-" + (++i), repo));
            }
        }
    }

    private void setLocalRepository(Path localRepository) {
        this.localRepository = localRepository;
    }

    Path getLocalRepository() {
        return localRepository;
    }

    List<Repository> getRemoteRepositories() {
        return remoteRepositories;
    }

    private void addProfile(Profile profile) {
        this.profiles.put(profile.getId(), profile);
    }

    private void addServer(Server server) {
        this.servers.put(server.getId(), server);
    }

    Map<String, Server> getServers() {
        return servers;
    }

    private void addActiveProfile(String profileName) {
        activeProfileNames.add(profileName);
    }

    void resolveActiveSettings() {
        for (String name : activeProfileNames) {
            Profile p = profiles.get(name);
            if (p != null) {
                remoteRepositories.addAll(p.getRepositories());
            }
        }
    }


    static final class Profile {
        private String id;
        final List<Repository> repositories = new LinkedList<>();

        Profile() {

        }

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        void addRepository(Repository repository) {
            repositories.add(repository);
        }

        List<Repository> getRepositories() {
            return repositories;
        }
    }

    static final class Repository {
        private String id;
        private String url;

        public Repository() {
        }

        public Repository(String id, String url) {
            this.id = id;
            this.url = url;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    static final class Server {
        private String id;
        private String username;
        private String password;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
