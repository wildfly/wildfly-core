/*
 * Copyright 2019 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.management.persistence;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.ssh.SshTestGitServer;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.jboss.as.repository.PathUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.KeyPairCredential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.WildFlyElytronCredentialStoreProvider;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * @author <a href="mailto:aabdelsa@redhat.com">Ashley Abdel-Sayed</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class RemoteSshGitRepositoryTestCase extends AbstractGitRepositoryTestCase {

    private static Path backupRoot;
    private static Path remoteRoot;
    private static Repository remoteRepository;
    private static SSHServer sshServer;
    protected static int port;
    private static final Path SSH_DIR = Paths.get("src", "test", "resources", "git-persistence", ".ssh").toAbsolutePath();
    private final String AUTH_FILE = Paths.get("src", "test", "resources", "git-persistence", "wildfly-config.xml").toUri().toString();
    private final String RSA_USER = "testRSA";
    private final String RSA_PUBKEY = "id_rsa.pub";
    private static final String EC_USER = "testEC";
    private static final String EC_PUBKEY = "id_ecdsa.pub";
    private final String PKCS_USER = "testPKCS";
    private final String PKCS_PUBKEY = "id_ecdsa_pkcs.pub";
    private final String CS_REF_USER = "testCSRef";
    private final String UNKNOWN_HOSTS_USER = "testUnknownHost";
    private static final String CS_REF_PUBKEY = "id_rsa_cred_store.pub";
    private static File KNOWN_HOSTS;
    private static Path CS_PUBKEY;

    private static final Provider CREDENTIAL_STORE_PROVIDER = new WildFlyElytronCredentialStoreProvider();

    private static final char[] CREDENTIAL_STORE_PASSWORD = "Elytron".toCharArray();

    private static final Map<String, String> stores = new HashMap<>();
    private static final String BASE_STORE_DIRECTORY = "target/ks-cred-stores";

    static {
        stores.put("ONE", BASE_STORE_DIRECTORY + "/openssh-keys-test.jceks");
    }

    static final class Data {

        private String alias;
        private Credential credential;
        private CredentialStore.ProtectionParameter protectionParameter;

        Data(final String alias, final Credential credential, final CredentialStore.ProtectionParameter protectionParameter) {
            this.alias = alias;
            this.credential = credential;
            this.protectionParameter = protectionParameter;
        }

        String getAlias() {
            return alias;
        }

        Credential getCredential() {
            return credential;
        }

        CredentialStore.ProtectionParameter getProtectionParameter() {
            return protectionParameter;
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        backupConfiguration();

        Security.insertProviderAt(CREDENTIAL_STORE_PROVIDER, 1);

        cleanCredentialStores();
        String file = stores.get("ONE");
        String type = "JCEKS";
        ArrayList<Data> data = new ArrayList<>();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(3072, new SecureRandom());
        KeyPairCredential keyPairCredential = new KeyPairCredential(keyPairGenerator.generateKeyPair());

        CS_PUBKEY = SSH_DIR.resolve(CS_REF_PUBKEY);
        Files.write(CS_PUBKEY, Collections.singleton(PublicKeyEntry.toString(keyPairCredential.getKeyPair().getPublic())));

        data.add(new Data("RSAKey", keyPairCredential, null));
        if (file == null) {
            throw new IllegalStateException("file has to be specified");
        }

        KeyStoreCredentialStore storeImpl = new KeyStoreCredentialStore();

        final Map<String, String> map = new HashMap<>();
        map.put("location", file);
        map.put("create", Boolean.TRUE.toString());
        map.put("keyStoreType", type);
        storeImpl.initialize(
                map,
                new CredentialStore.CredentialSourceProtectionParameter(
                        IdentityCredentials.NONE.withCredential(new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, CREDENTIAL_STORE_PASSWORD)))),
                null
        );

        for (Data item : data) {
            storeImpl.store(item.getAlias(), item.getCredential(), item.getProtectionParameter());
        }
        storeImpl.flush();

    }

    static void backupConfiguration() throws IOException {
        Path backUpRoot = Files.createTempDirectory("BackUpConfigurationFiles").resolve("configuration");
        Files.createDirectories(backUpRoot);
        PathUtil.copyRecursively(getJbossServerBaseDir().resolve("configuration"), backUpRoot, true);

        RemoteSshGitRepositoryTestCase.backupRoot = backUpRoot;
    }

    @AfterClass
    public static void afterClass() throws IOException {
        Security.removeProvider(CREDENTIAL_STORE_PROVIDER.getName());
        FileUtils.delete(CS_PUBKEY.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
        PathUtil.deleteRecursively(backupRoot);
    }

    @Before
    public void prepareTest() throws Exception {
        remoteRoot = new File("target", "remote").toPath();
        Path repoConfigDir = remoteRoot.resolve("configuration");
        Files.createDirectories(repoConfigDir);
        File baseDir = remoteRoot.toAbsolutePath().toFile();
        Path jbossConfigDir = new File(System.getProperty("jboss.home", System.getenv("JBOSS_HOME"))).toPath().resolve("standalone").resolve("configuration");
        PathUtil.copyRecursively(jbossConfigDir, repoConfigDir, true);
        Path properties = repoConfigDir.resolve("logging.properties");
        if (Files.exists(properties)) {
            Files.delete(properties);
        }
        Path jbossAuthDir = new File(System.getProperty("jboss.home", System.getenv("JBOSS_HOME"))).toPath().resolve("standalone").resolve("tmp").resolve("auth");
        Files.createDirectories(jbossAuthDir);
        File gitDir = new File(baseDir, Constants.DOT_GIT);
        if (!gitDir.exists()) {
            try (Git git = Git.init().setDirectory(baseDir).setInitialBranch(Constants.MASTER).call()) {
                git.add().addFilepattern("configuration").call();
                git.commit().setSign(false).setMessage("Repository initialized").call();
            }
        }
        remoteRepository = new FileRepositoryBuilder().setWorkTree(baseDir).setGitDir(gitDir).setup().build();

        //Generate new key pair for the server
        ByteArrayOutputStream publicHostKey = new ByteArrayOutputStream();
        JSch jsch = new JSch();
        KeyPair hostKeyPair = KeyPair.genKeyPair(jsch, 2, 2048);
        ByteArrayOutputStream hostPrivateKey = new ByteArrayOutputStream();
        hostKeyPair.writePrivateKey(hostPrivateKey);
        hostPrivateKey.flush();
        hostKeyPair.writePublicKey(publicHostKey, "");

        sshServer = new SSHServer(EC_USER, SSH_DIR.resolve(EC_PUBKEY),
                remoteRepository, hostPrivateKey.toByteArray()); //create key pair gen
        port = sshServer.start();

        //Add new server to known_hosts
        KNOWN_HOSTS = SSH_DIR.resolve("known_hosts").toFile();

        FileWriter fileWritter = new FileWriter(KNOWN_HOSTS, true);
        String knownHostTemplate = "[%s]:" + port + ' ' + publicHostKey.toString(US_ASCII.name()) + "\n";
        try (BufferedWriter bw = new BufferedWriter(fileWritter)) {
            bw.write(String.format(knownHostTemplate, "127.0.0.1"));
            bw.write(String.format(knownHostTemplate, "localhost"));
            bw.write(String.format(knownHostTemplate, InetAddress.getLocalHost().getHostName()));
            if (System.getenv().containsKey("COMPUTERNAME")) {
                bw.write(String.format(knownHostTemplate, System.getenv().get("COMPUTERNAME")));
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        if (container.isStarted()) {
            try {
                removeDeployment();
            } catch (Exception sde) {
                // ignore error undeploying, might not exist
            }
            removeSystemProperty();
            container.stop();
        }
        if (sshServer != null) {
            sshServer.stop();
            sshServer = null;
        }
        FileUtils.delete(KNOWN_HOSTS, FileUtils.RECURSIVE | FileUtils.RETRY);
        closeRepository();
        closeEmptyRemoteRepository();
        closeRemoteRepository();

        restoreConfiguration();
    }

    void restoreConfiguration() throws IOException {
        Path configuration = getJbossServerBaseDir().resolve("configuration");
        PathUtil.deleteRecursively(configuration);
        Files.createDirectories(configuration);

        PathUtil.copyRecursively(backupRoot, getJbossServerBaseDir().resolve("configuration"), true);
    }

    @Test
    public void startGitRepoRemoteSSHAuthTest() throws Exception {
        //add user to server
        sshServer.setTestUser(EC_USER);
        sshServer.setTestUserPublicKey(SSH_DIR.resolve(EC_PUBKEY));

        // start with remote repository containing configuration
        //(--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=Primary
        //--git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
        container.startGitBackedConfiguration("ssh://" + EC_USER + "@127.0.0.1:" + port + "/doesntmatter",
                Constants.MASTER, AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        List<String> commits = listCommits(remoteRepository);
        Assert.assertEquals(1, commits.size());
        addSystemProperty();
        publish(null);
        commits = listCommits(remoteRepository);
        Assert.assertEquals(3, commits.size());
        // create branch in remote repo and change Primary for next test
        try (Git git = new Git(remoteRepository)) {
            git.checkout().setName("my_branch").setCreateBranch(true).call();
        }
        removeSystemProperty();
        publish(null);
        container.stop();
        closeRepository();

        // start with remote repository and branch containing configuration
        // (--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=my_branch
        // --git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
        container.startGitBackedConfiguration("ssh://" + EC_USER + "@127.0.0.1:" + port + "/doesntmatter",
                "my_branch", AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        try {
            //my_branch was created before the system property was removed and so attempting to add the system property
            //should fail as it already exists
            addSystemProperty();
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0212"));
        }
    }

    @Test
    public void startGitRepoRemoteSSHPKCSAuthTest() throws Exception {
        //add user to server
        sshServer.setTestUser(PKCS_USER);
        sshServer.setTestUserPublicKey(SSH_DIR.resolve(PKCS_PUBKEY));

        // start with remote repository containing configuration
        //(--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=Primary
        //--git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
        container.startGitBackedConfiguration("ssh://" + PKCS_USER + "@127.0.0.1:" + port + "/doesntmatter",
                Constants.MASTER, AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        List<String> commits = listCommits(remoteRepository);
        Assert.assertEquals(1, commits.size());
        addSystemProperty();
        publish(null);
        commits = listCommits(remoteRepository);
        Assert.assertEquals(3, commits.size());
        // create branch in remote repo and change Primary for next test
        try (Git git = new Git(remoteRepository)) {
            git.checkout().setName("my_branch").setCreateBranch(true).call();
        }
        removeSystemProperty();
        publish(null);
        container.stop();
        closeRepository();

        // start with remote repository and branch containing configuration
        // (--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=my_branch
        // --git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
        container.startGitBackedConfiguration("ssh://" + PKCS_USER + "@127.0.0.1:" + port + "/doesntmatter",
                "my_branch", AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        try {
            //my_branch was created before the system property was removed and so attempting to add the system property
            //should fail as it already exists
            addSystemProperty();
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0212"));
        }
    }

    @Test
    public void startGitRepoRemoteSshAuthRSATest() throws Exception {
        //add user to server
        sshServer.setTestUser(RSA_USER);
        sshServer.setTestUserPublicKey(SSH_DIR.resolve(RSA_PUBKEY));

        // start with remote repository containing configuration
        //(--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=Primary
        //--git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
        container.startGitBackedConfiguration("ssh://" + RSA_USER + "@127.0.0.1:" + port + "/doesntmatter",
                Constants.MASTER, AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        List<String> commits = listCommits(remoteRepository);
        Assert.assertEquals(1, commits.size());
        addSystemProperty();
        publish(null);
        commits = listCommits(remoteRepository);
        Assert.assertEquals(3, commits.size());
        // create branch in remote repo and change Primary for next test
        try (Git git = new Git(remoteRepository)) {
            git.checkout().setName("my_branch").setCreateBranch(true).call();
        }
        removeSystemProperty();
        publish(null);
        container.stop();
        closeRepository();

        // start with remote repository and branch containing configuration
        //(--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=my_branch
        //--git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
        container.startGitBackedConfiguration("ssh://" + RSA_USER + "@127.0.0.1:" + port + "/doesntmatter",
                "my_branch", AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        try {
            //my_branch was created before the system property was removed and so attempting to add the system property
            //should fail as it already exists
            addSystemProperty();
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0212"));
        }
    }

    @Test
    public void startGitRepoRemoteSSHCredStoreRefTest() throws Exception {
        //add user to server
        sshServer.setTestUser(CS_REF_USER);
        sshServer.setTestUserPublicKey(CS_PUBKEY);

        // start with remote repository containing configuration
        //(--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=Primary
        //--git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
        container.startGitBackedConfiguration("ssh://" + CS_REF_USER + "@127.0.0.1:" + port + "/doesntmatter",
                Constants.MASTER, AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        List<String> commits = listCommits(remoteRepository);
        Assert.assertEquals(1, commits.size());
        addSystemProperty();
        publish(null);
        commits = listCommits(remoteRepository);
        Assert.assertEquals(3, commits.size());
        // create branch in remote repo and change Primary for next test
        try (Git git = new Git(remoteRepository)) {
            git.checkout().setName("my_branch").setCreateBranch(true).call();
        }
        removeSystemProperty();
        publish(null);
        container.stop();
        closeRepository();

        // start with remote repository and branch containing configuration
        //(--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=my_branch
        //--git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
        container.startGitBackedConfiguration("ssh://" + CS_REF_USER + "@127.0.0.1:" + port + "/doesntmatter",
                "my_branch", AUTH_FILE);
        Assert.assertTrue("Directory not found " + getDotGitDir(), Files.exists(getDotGitDir()));
        Assert.assertTrue("File not found " + getDotGitIgnore(), Files.exists(getDotGitIgnore()));
        try {
            //my_branch was created before the system property was removed and so attempting to add the system property
            //should fail as it already exists
            addSystemProperty();
            Assert.fail("Operation should have failed");
        } catch (UnsuccessfulOperationException uoe) {
            Assert.assertTrue(uoe.getMessage().contains("WFLYCTL0212"));
        }
    }

    @Test
    public void startGitRepoRemoteSSHFailedAuthTest() throws Exception {
        //add user to server
        sshServer.setTestUser(EC_USER);
        sshServer.setTestUserPublicKey(SSH_DIR.resolve(RSA_PUBKEY)); //incorrect public key

        try {
            // start with remote repository containing configuration
            //(--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=Primary
            //--git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
            //Trying to access EC_USER, should not be authorized
            container.startGitBackedConfiguration("ssh://" + EC_USER + "@127.0.0.1:" + port + "/doesntmatter",
                    Constants.MASTER, AUTH_FILE);
            Assert.fail("Should have failed authentication");
        } catch (RuntimeException ex) {
            //
        }
    }

    @Test
    public void startGitRepoRemoteUnknownHostTest() throws Exception {
        //Create new empty known hosts file
        Path emptyHosts = SSH_DIR.resolve("empty_hosts");
        Files.write(emptyHosts, Collections.singleton("[localhost]:"));

        //add user to server
        sshServer.setTestUser(UNKNOWN_HOSTS_USER);
        sshServer.setTestUserPublicKey(SSH_DIR.resolve(RSA_PUBKEY));

        try {
            // start with remote repository containing configuration
            //(--git-repo=ssh://testDefault@127.0.0.1:testPort/doesntmatter --git-branch=Primary
            //--git-auth=file:./src/test/resources/git-persistence/ssh-auth/wildfly-config.xml)
            container.startGitBackedConfiguration("ssh://" + UNKNOWN_HOSTS_USER + "@127.0.0.1:" + port + "/doesntmatter",
                    Constants.MASTER, AUTH_FILE);
            Assert.fail("Should have failed to authenticate host");
        } catch (RuntimeException ex) {
            Path serverLog = Paths.get(getJbossServerBaseDir().resolve("log").toString(), "server.log");
            assertLogContains(serverLog, "The authenticity of host", true);
            assertLogContains(serverLog, "cannot be established", true);
        } finally {
            //Delete empty known_hosts file
            FileUtils.delete(emptyHosts.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
        }

    }

    private static class SSHServer extends SshTestGitServer {

        @NotNull
        protected String testUser;
        @NotNull
        protected Repository repository;

        public SSHServer(String testUser, Path testKey, Repository repository, byte[] hostKey) throws IOException, GeneralSecurityException {
            super(testUser, testKey, repository, hostKey);
        }

        public void setTestUser(String testUser) {
            this.testUser = testUser;
        }

        @Override
        protected void configureAuthentication() {
            super.configureAuthentication();
            this.server.setPublickeyAuthenticator((userName, publicKey, session) -> {
                return this.testUser.equals(userName) && KeyUtils.compareKeys(this.testKey, publicKey);
            });
        }
    }

    private void assertLogContains(final Path logFile, final String msg, final boolean expected) throws Exception {
        try (final BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            boolean logFound = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains(msg)) {
                    logFound = true;
                    break;
                }
            }
            Assert.assertTrue(logFound == expected);
        }
    }

    private void closeRemoteRepository() throws Exception {
        if (remoteRepository != null) {
            remoteRepository.close();
        }
        FileUtils.delete(remoteRoot.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
    }

    private static void cleanCredentialStores() {
        File dir = new File(BASE_STORE_DIRECTORY);
        dir.mkdirs();

        for (String f : stores.values()) {
            File file = new File(f);
            file.delete();
        }
    }

}
