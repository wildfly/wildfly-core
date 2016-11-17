/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.handlers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class FilenameTabCompleter implements CommandLineCompleter {

    private final CommandContext ctx;

    public FilenameTabCompleter(CommandContext ctx) {
        if(ctx == null) {
            throw new IllegalArgumentException("ctx is null");
        }
        this.ctx = ctx;
    }

    protected int getCandidates(String buffer, List<String> candidates) {
        //First clear the path
        String clearedPath = clearPath(buffer);
        String translated = translatePath(clearedPath);

        final File f = new File(translated);
        final File dir;
        if (translated.endsWith(File.separator)) {
            dir = f;
        } else {
            dir = f.getParentFile();
        }

        final File[] entries = (dir == null) ? new File[0] : dir.listFiles();
        return matchFiles(buffer, translated, entries, candidates);
    }

    public static FilenameTabCompleter newCompleter(CommandContext ctx) {
        return Util.isWindows() ? new WindowsFilenameTabCompleter(ctx)
                : new DefaultFilenameTabCompleter(ctx);
    }
    /**
     * Translate a path that has previously been unescaped and unquoted.
     * That is called at command execution when the calue is retrieved prior to be
     * used as ModelNode value.
     * @param path The unquoted, unescaped path.
     * @return A path with ~ and default dir expanded.
     */
    public String translatePath(String path) {
        String translated;
        // special character: ~ maps to the user's home directory
        if (path.startsWith("~" + File.separator)) {
            translated = System.getProperty("user.home") + path.substring(1);
        } else if (path.startsWith("~")) {
            String userName = path.substring(1);
            translated = new File(new File(System.getProperty("user.home")).getParent(),
                    userName).getAbsolutePath();
            // Keep the path separator in translated or add one if no user home specified
            translated = userName.isEmpty() || path.endsWith(File.separator) ? translated + File.separator : translated;
        } else if (!new File(path).isAbsolute()) {
            translated = ctx.getCurrentDir().getAbsolutePath() + File.separator + path;
        } else {
            translated = path;
        }
        return translated;
    }

    /**
     * Unescape and unquote the path. Ready for translation.
     */
    private static String clearPath(String path) {
        try {
            ExpressionBaseState state = new ExpressionBaseState("EXPR", true, false);
            if (Util.isWindows()) {
                // to not require escaping FS name separator
                state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_OFF);
            } else {
                state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
            }
            // Remove escaping characters
            path = ArgumentWithValue.resolveValue(path, state);
        } catch (CommandFormatException ex) {
            // XXX OK, continue translation
        }
        // Remove quote to retrieve candidates.
        if (path.startsWith("\"")) {
            path = path.substring(1);
        }
        // Could be an escaped " character. We don't take into account this corner case.
        // concider it the end of the quoted part.
        if (path.endsWith("\"")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

   /**
    * Match the specified <i>buffer</i> to the array of <i>entries</i> and
    * enter the matches into the list of <i>candidates</i>. This method can be
    * overridden in a subclass that wants to do more sophisticated file name
    * completion.
    *
    * @param buffer
    *            the untranslated buffer
    * @param translated
    *            the buffer with common characters replaced
    * @param entries
    *            the list of files to match
    * @param candidates
    *            the list of candidates to populate
    *
    * @return the offset of the match
    */
   protected int matchFiles(String buffer, String translated, File[] entries, List<String> candidates) {
       if (entries == null) {
           return -1;
       }

       boolean isDirectory = false;
       for (int i = 0; i < entries.length; i++) {
           if (entries[i].getAbsolutePath().startsWith(translated)) {
               isDirectory = entries[i].isDirectory();
               candidates.add(entries[i].getName());
           }
       }

       // Append File.separator for inlined directory.
       if (candidates.size() == 1) {
           String candidate = candidates.get(0);
           if (isDirectory) {
               candidate = candidate + File.separator;
           }
           candidates.set(0, candidate);
       }

       // inline only the subpath from last File.separator or 0.
       int index = buffer.lastIndexOf(File.separatorChar) + 1;
       return index;
   }

    public static String expand(String path) throws IOException {
        Objects.requireNonNull(path);
        // Can be found on any platform (Windows powershell or shell).
        if (path.startsWith("~")) {
            String home = System.getProperty("user.home");
            if (home == null) {
                throw new IOException("Path " + path + " can't be expanded. "
                        + "No user.home property");
            }
            if (path.startsWith("~" + File.separator)) {
                path = new File(home, path.substring(2)).getAbsolutePath();
            } else {
                int i = path.indexOf(File.separator);
                if (i < 0 || i >= path.length() - 1) {
                    throw new IOException("Invalid file " + path);
                }
                String user = path.substring(1, i);
                File homeDir = new File(new File(home).getParent(), user);
                path = new File(homeDir, path.substring(i + 1)).getAbsolutePath();
            }
        }
        return path;
    }

    void postProcess(String buffer, List<String> candidates) {
        if (candidates.size() == 1) {
            String candidate = candidates.get(0);
            if (!buffer.contains(File.separator)) {
                if (buffer.startsWith("\"~")) {
                    candidate = "\"~" + candidate;
                } else if (buffer.startsWith("\"")) {
                    candidate = "\"" + candidate;
                } else if (buffer.startsWith("~")) {
                    candidate = "~" + candidate;
                }
            }
            candidates.set(0, candidate);
        }
    }

    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        int result = getCandidates(buffer, candidates);
        Collections.sort(candidates);
        completeCandidates(ctx, buffer, cursor, candidates);
        postProcess(buffer, candidates);
        return result;
    }

    abstract void completeCandidates(CommandContext ctx, String buffer, int cursor, List<String> candidates);
}
