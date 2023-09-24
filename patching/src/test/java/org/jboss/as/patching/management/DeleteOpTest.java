/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.management;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests "safe" delete operation {@link DeleteOp}
 *
 * @author Bartosz Spyrko-Smietanko
 */
public class DeleteOpTest {
    public static final boolean NOT_PROTECTED = false;
    public static final boolean PROTECTED = true;
    public static final FilenameFilter INCLUDE_ALL = (d,f) -> true;
    private static String TEST_DIR = "test";
    public static final String BACKUP_ROOT_FILE_NAME = TEST_DIR + "/" + ".bkp";

    @Before
    public void setUp() {
        final File testDir = new File(TEST_DIR);
        if (testDir.exists()) {
            recursiveCleanUp(testDir);
        }

        testDir.mkdir();
    }

    @After
    public void tearDown() {
        final File testDir = new File(TEST_DIR);
        if (testDir.exists()) {
            recursiveCleanUp(testDir);
        }
    }

    private void recursiveCleanUp(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                recursiveCleanUp(child);
            }
        }
        file.delete();
    }

    @Test
    public void removeNonProtectedFileWithoutErrors() throws Exception {
        final File origin = createFile("test.txt", NOT_PROTECTED);

        delete(origin, INCLUDE_ALL);

        assertOriginalFileRemoved("test.txt");
    }

    @Test
    public void failToDeleteProtectedFile() throws Exception {
        final File origin = createFile("test.txt", PROTECTED);

        delete(origin, INCLUDE_ALL);

        assertOriginalFileExists("test.txt");
    }

    @Test
    public void removeFolderWithUnprotectedFiles() throws Exception {
        File dir = createDir("parent", createFile("parent/test.txt", NOT_PROTECTED));

        delete(dir, INCLUDE_ALL);

        assertOriginalFileRemoved("parent");
        assertBackupFolderWasRemoved();
    }

    @Test
    public void failToRemoveParentIfChildIsProtected() throws Exception {
        File dir = createDir("parent", createFile("parent/test.txt", PROTECTED));

        delete(dir, INCLUDE_ALL);

        assertOriginalFileExists("parent");
        assertOriginalFileExists("parent/test.txt");
        assertBackupFolderWasRemoved();
    }

    @Test
    public void doNotDeleteAnyFilesIfOneFileIsProtected() throws Exception {
        File dir = createDir("parent", createFile("parent/test2.txt", NOT_PROTECTED), createFile("parent/test.txt", PROTECTED));

        delete(dir, INCLUDE_ALL);

        assertOriginalFileExists("parent");
        assertOriginalFileExists("parent/test.txt");
        assertOriginalFileExists("parent/test2.txt");
        assertBackupFolderWasRemoved();
    }

    @Test
    public void executeTwoDeleteOpsWithSameBackupDir() throws Exception {
        final File dir1 = createDir("dir1", createFile("dir1/test.txt", NOT_PROTECTED));
        final File dir2 = createDir("dir2", createFile("dir2/test.txt", PROTECTED));

        final DeleteOp op1 = new DeleteOp(dir1, INCLUDE_ALL);
        final DeleteOp op2 = new DeleteOp(dir2, INCLUDE_ALL);
        try {
            DeleteOp.execute(Arrays.asList(op1, op2));

            fail("DeleteOp should throw an exception");
        } catch(IOException e) {
            assertOriginalFileExists("dir1/test.txt");
            assertOriginalFileExists("dir2/test.txt");
        }
    }

    @Test
    public void dontRemoveIgnoredFiles() throws Exception {
        final File dir = createDir("dir1", createFile("dir1/test1.txt", NOT_PROTECTED), createFile("dir1/test2.txt", NOT_PROTECTED));

        boolean operationResult = delete(dir, exclude("test1.txt"));

        assertTrue("The delete with ignored files should be succesful", operationResult);
        assertOriginalFileExists("dir1/test1.txt");
        assertOriginalFileRemoved("dir1/test2.txt");
    }

    @Test
    public void dontRemoveIgnoredDirectories() throws Exception {
        final File dir = createDir("dir1",
                            createDir("dir2",
                                    createFile("dir1/dir2/test1.txt", NOT_PROTECTED)),
                            createFile("dir1/test2.txt", NOT_PROTECTED));

        delete(dir, exclude("dir2"));

        assertOriginalFileExists("dir1/dir2/test1.txt");
        assertOriginalFileRemoved("dir1/test2.txt");
    }

    @Test
    public void completeSuccesfullyIfAllFilesWereIgnored() throws Exception {
        final File dir = createDir("dir1");

        final DeleteOp op1 = new DeleteOp(dir, (d,f)->false); // exclude all files
        DeleteOp.execute(Arrays.asList(op1));

        assertOriginalFileExists("dir1");
        assertBackupFolderWasRemoved();
    }

    @Test
    public void removeBackupFolderAfterCommit() throws Exception {
        file("dir1").mkdir();
        file("dir1", "test1.txt").createNewFile();

        boolean operationResult = delete(file("dir1"), INCLUDE_ALL);

        assertTrue("The delete should have succeeded", operationResult);
        assertBackupFolderWasRemoved();
    }

    @Test
    public void removeBackupFolderAfterRollback() throws Exception {
        final File dir1 = createDir("dir1", createFile("dir1/test.txt", PROTECTED));

        boolean operationResult = delete(dir1, INCLUDE_ALL);

        assertFalse("The delete should have failed", operationResult);
        assertBackupFolderWasRemoved();
    }

    @Test
    public void failDeleteIfDeletedFileExistsInBackupFolder() throws Exception {
        createDir(".bkp", createDir("dir1", createFile(".bkp/dir1/test.txt", NOT_PROTECTED)));
        final File dir1 = createDir("dir1", createFile("dir1/test.txt", NOT_PROTECTED));

        boolean operationResult = delete(dir1, INCLUDE_ALL);

        assertFalse("The delete should have failed", operationResult);
    }

    private static void assertOriginalFileExists(String path) {
        final String fullPath = TEST_DIR + "/" + path;
        final File file = new File(fullPath);

        assertTrue("Original file [" + fullPath +  "] should not be removed", file.exists());
    }

    private static void assertOriginalFileRemoved(String path) {
        final String fullPath = TEST_DIR + "/" + path;
        final File file = new File(fullPath);

        assertFalse("Original file [" + fullPath +  "] should be removed", file.exists());
    }

    private static void assertBackupFolderWasRemoved() {
        final File file = new File(BACKUP_ROOT_FILE_NAME);

        assertFalse("Backup folder [" + BACKUP_ROOT_FILE_NAME  + "] should be removed", file.exists());
    }

    private boolean delete(File origin, FilenameFilter filter) throws IOException {
        final DeleteOp op = new DeleteOp(origin, filter);
        try {
            DeleteOp.execute(Arrays.asList(op));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static File file(String... parts) {
        StringBuilder sb = new StringBuilder(TEST_DIR);
        for (String part : parts) {
            sb.append("/").append(part);
        }
        return new File(sb.toString());
    }

    private static FilenameFilter exclude(String fileName) {
        return (d,f) -> !f.equals(fileName);
    }

    private static File createDir(String name, File... files) {
        String fileName = TEST_DIR + "/" + name;
        File dir = new MockFile(fileName, false, files);
        if (!dir.exists()) {
            dir.mkdir();
        }
        return dir;
    }

    private static File createFile(String name, boolean deleteProtected) throws IOException {
        createParents(name);

        String fileName = TEST_DIR + "/" + name;
        File origin = new MockFile(fileName, deleteProtected);
        origin.createNewFile();
        return origin;
    }

    private static void createParents(String name) {
        File parent = new File(TEST_DIR);
        final String[] split = name.split("/");
        for (int i = 0; i < split.length - 1; i++) {
            String p = split[i];
            parent = new File(parent, p);
            parent.mkdir();
        }
    }

    /*
    Since exact behaviour of File depends on the underlying platform, File.setReadonly / File.setWritable does not
    guarantee move protection. MockFile is to help simulate those issues.
     */
    private static class MockFile extends File {
        private File[] children;
        private boolean protect;

        MockFile(String name, boolean protect) {
            super(name);
            this.protect = protect;
        }

        MockFile(String name, boolean protect, File... children) {
            this(name, protect);
            this.children = children;
        }

        @Override
        public boolean delete() {
            if (protect) {
                return false;
            } else {
                return super.delete();
            }
        }

        @Override
        public boolean renameTo(File dest) {
            if (protect) {
                return false;
            } else {
                return super.renameTo(dest);
            }
        }

        @Override
        public File[] listFiles(FileFilter filter) {
            final File[] files = super.listFiles(filter);

            // need to return the mock ones, but skip ones that were removed
            ArrayList<File> filtered = new ArrayList<>();
            for (File file : files) {
                for (File child : children) {
                    if (file.getAbsolutePath().equals(child.getAbsolutePath())) {
                        filtered.add(child);
                    }
                }
            }

            return filtered.toArray(new File[]{});
        }

        @Override
        public File[] listFiles(FilenameFilter filter) {
            final File[] files = super.listFiles(filter);

            // need to return the mock ones, but skip ones that were removed
            ArrayList<File> filtered = new ArrayList<>();
            for (File file : files) {
                for (File child : children) {
                    if (file.getAbsolutePath().equals(child.getAbsolutePath())) {
                        filtered.add(child);
                    }
                }
            }

            return filtered.toArray(new File[]{});
        }
    }
}
