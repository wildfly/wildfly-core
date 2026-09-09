/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.IoUtils.safeClose;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.installation.PatchableTarget;

/**
 * @author Emanuel Muckenhuber
 */
public final class PatchUtils {

    public static String readRef(final Properties properties, final String name) {
        final String ref = (String) properties.get(name);
        if(ref == null) {
            return Constants.BASE;
        }
        return ref;
    }

    public static List<String> readRefs(final Properties properties) {
        return readRefs(properties, Constants.PATCHES);
    }

    public static List<String> readRefs(final Properties properties, final String property) {
        String layersProp = (String) properties.get(property);
        if (layersProp == null || (layersProp = layersProp.trim()).length() == 0) {
            return Collections.emptyList();
        } else {
            final String[] names = layersProp.split(",");
            final List<String> patches = new ArrayList<String>();
            for (final String name : names) {
                patches.add(name);
            }
            return Collections.unmodifiableList(patches);
        }
    }

    public static String asString(final List<String> values) {
        final StringBuilder builder = new StringBuilder();
        for (final String value : values) {
            builder.append(value);
            builder.append(',');
        }
        if (builder.length() > 0) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    public static void writeRefs(final File file, final List<String> refs, boolean append) throws IOException {
        mkdir(file.getParentFile());
        final OutputStream os = new FileOutputStream(file, append);
        try {
            writeRefs(os, refs);
            os.flush();
            os.close();
        } finally {
            safeClose(os);
        }
    }

    public static void writeRefs(final File file, final List<String> refs) throws IOException {
        writeRefs(file, refs, false);
    }

    static void writeRefs(final OutputStream os, final List<String> refs) throws IOException {
        for(final String ref : refs) {
            writeLine(os, ref);
        }
    }

    static void writeLine(final OutputStream os, final String s) throws IOException {
        os.write(s.getBytes(StandardCharsets.UTF_8));
        os.write('\n');
    }

    static File[] getModulePath(final DirectoryStructure structure, final PatchableTarget.TargetInfo info) {
        final List<File> path = new ArrayList<File>();
        final List<String> patches = info.getPatchIDs();
        for (final String patch : patches) {
            path.add(structure.getModulePatchDirectory(patch));
        }
        final String ref = info.getCumulativePatchID();
        if (!BASE.equals(ref)) {
            path.add(structure.getModulePatchDirectory(ref));
        }
        path.add(structure.getModuleRoot());
        return path.toArray(new File[path.size()]);
    }

    public static void writeProperties(final File file, final Properties properties) throws IOException {
        final OutputStream os = new FileOutputStream(file);
        try {
            final Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            properties.store(writer, "read only");
            writer.close();
        } finally {
            safeClose(os);
        }
    }

    public static Properties loadProperties(final File file) throws IOException {
        if (! file.exists()) {
            return new Properties();
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            final Properties props = new Properties();
            props.load(reader);
            return props;
        } finally {
            safeClose(reader);
        }
    }
}
