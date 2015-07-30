/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.server.deployment.module;

import java.io.IOException;
import java.io.InputStream;

/**
 * Appends a new line char to the stream if it doesn't have one as his last byte.
 *
 * @author ehsavoie
 */
final class PaddedManifestStream extends InputStream {

    private final InputStream realStream;
    private int previousChar = -1;

    PaddedManifestStream(InputStream realStream) {
        this.realStream = realStream;
    }

    @Override
    public int read() throws IOException {
        int value = this.realStream.read();
        while(value == '\0') {
            value = this.realStream.read();
        }
        if (value == -1 && previousChar != '\n' && previousChar != -1) {
            previousChar = '\n';
            return '\n';
        }
        previousChar = value;
        return value;
    }

    @Override
    public void close() throws IOException {
        super.close();
        realStream.close();
    }
}
