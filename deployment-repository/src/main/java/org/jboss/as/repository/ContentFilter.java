/*
 * Copyright 2016 JBoss by Red Hat.
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
package org.jboss.as.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Filter used to filter contents when browsing the content respository.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public interface ContentFilter {

    boolean acceptFile(Path rootPath, Path file) throws IOException;

    boolean acceptFile(Path rootPath, Path file, InputStream in) throws IOException;

    boolean acceptDirectory(Path rootPath, Path path) throws IOException;

    class SimpleContentFilter implements ContentFilter {
        private final int depth;
        private final boolean archiveOnly;

        private SimpleContentFilter(int depth, boolean archiveOnly) {
            this.depth = depth;
            this.archiveOnly = archiveOnly;
        }

        @Override
        public boolean acceptFile(Path rootPath, Path file) throws IOException {
            return acceptFile(rootPath, file, null);
        }

        @Override
        public boolean acceptFile(Path rootPath, Path file, InputStream in) throws IOException {
            Path relativePath = rootPath.relativize(file);
            if(this.depth < 0 || this.depth >= relativePath.getNameCount()) {
               if(archiveOnly) {
                   if(in != null) {
                       return PathUtil.isArchive(in);
                   }
                   return PathUtil.isArchive(file);
               }
               return true;
            }
            return false;
        }

        @Override
        public boolean acceptDirectory(Path rootPath, Path dir) {
            Path relativePath = rootPath.relativize(dir);
            if (!archiveOnly) {
                return (this.depth < 0 || this.depth >= relativePath.getNameCount());
            }
            return false;
        }
    }

    class FileContentFilter extends SimpleContentFilter {

        private FileContentFilter(int depth, boolean archiveOnly) {
            super(depth, archiveOnly);
        }

        @Override
        public boolean acceptDirectory(Path rootPath, Path dir) {
            return false;
        }
    }

    public class Factory {

        public static ContentFilter createFileFilter(int depth, boolean archiveOnly) {
            return  new FileContentFilter(depth, archiveOnly);
        }

        public static ContentFilter createContentFilter(int depth, boolean archiveOnly) {
            return new SimpleContentFilter(depth, archiveOnly);
        }
    }
}
