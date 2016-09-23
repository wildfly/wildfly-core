/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.management.client.content;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.vfs.VirtualFile;

/**
 * {@link Resource} implementation for the root resource of a tree of resources that store managed DMR content
 * (e.g. named rollout plans.)
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagedDMRContentTypeResource implements Resource.ResourceEntry {

    private final PathAddress address;
    private final String childType;
    private final ContentRepository contentRepository;
    private final Map<String, ManagedContent> content = new TreeMap<String, ManagedContent>();
    private final ModelNode model = new ModelNode();
    private final MessageDigest messageDigest;

    @Deprecated
    public ManagedDMRContentTypeResource(final PathElement pathElement, final String childType,
                                         final byte[] initialHash, final ContentRepository contentRepository) {
        this(PathAddress.pathAddress(pathElement), childType, initialHash, contentRepository);
    }

    public ManagedDMRContentTypeResource(final PathAddress address, final String childType, final byte[] initialHash, final ContentRepository contentRepository) {
        this.childType = childType;
        this.contentRepository = contentRepository;
        this.address = address;

        try {
            this.messageDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw ManagedDMRContentLogger.ROOT_LOGGER.messageDigestAlgorithmNotAvailable(e);
        }

        // Establish an initial hash attribute
        model.get(ModelDescriptionConstants.HASH);

        if (initialHash != null) {
            loadContent(initialHash);
        } // else leave attribute undefined
    }

    private ManagedDMRContentTypeResource(final ManagedDMRContentTypeResource toCopy) {
        this.childType = toCopy.childType;
        this.contentRepository = toCopy.contentRepository;
        this.messageDigest = toCopy.messageDigest;
        this.address = toCopy.address;
        synchronized (toCopy.content) {
            for (Map.Entry<String, ManagedContent> entry : toCopy.content.entrySet()) {
                ManagedContent value = entry.getValue();
                this.content.put(entry.getKey(), new ManagedContent(value.getContent(), value.getHash()));
            }
        }
        this.model.set(toCopy.model);
    }

    @Override
    public ModelNode getModel() {
        return model.clone();
    }

    @Override
    public void writeModel(ModelNode newModel) {
        if (model.hasDefined(ModelDescriptionConstants.HASH)) {
            throw ControllerLogger.ROOT_LOGGER.immutableResource();
        } else {
            // ApplyRemoteMasterDomainModelHandler is writing us
            byte[] initialHash = newModel.hasDefined(ModelDescriptionConstants.HASH) ? newModel.get(ModelDescriptionConstants.HASH).asBytes() : null;
            if (initialHash != null) {
                loadContent(initialHash);
            }
        }
    }

    @Override
    public boolean isModelDefined() {
        return true;
    }

    @Override
    public Resource getChild(PathElement element) {
        if (hasChildren(element.getKey())) {
            synchronized (content) {
                String name = element.getValue();
                ManagedContent managedContent = content.get(name);
                if (managedContent != null) {
                    return getChildEntry(name);
                }
            }
        }
        return null;
    }

    @Override
    public Resource requireChild(PathElement address) {
        final Resource resource = getChild(address);
        if (resource == null) {
            throw new NoSuchResourceException(address);
        }
        return resource;
    }

    @Override
    public Resource navigate(PathAddress address) {
        if (address.size() == 0) {
            return this;
        } else {
            Resource child = requireChild(address.getElement(0));
            return address.size() == 1 ? child : child.navigate(address.subAddress(1));
        }
    }

    @Override
    public Set<String> getChildTypes() {
        return Collections.singleton(childType);
    }

    @Override
    public Set<Resource.ResourceEntry> getChildren(String childType) {
        if (!hasChildren(childType)) {
            return Collections.emptySet();
        } else {
            Set<Resource.ResourceEntry> result = new HashSet<ResourceEntry>();
            synchronized (content) {
                for (String name : content.keySet()) {
                    result.add(getChildEntry(name));
                }
            }
            return result;
        }
    }

    @Override
    public final boolean hasChildren(String childType) {
        return this.childType.equals(childType);
    }

    @Override
    public boolean hasChild(PathElement element) {
        return getChildrenNames(element.getKey()).contains(element.getValue());
    }

    @Override
    public Set<String> getChildrenNames(String childType) {
        if (hasChildren(childType)) {
            synchronized (content) {
                return new HashSet<String>(content.keySet());
            }
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public void registerChild(PathElement address, Resource resource) {
        if (!childType.equals(address.getKey())) {
            throw ManagedDMRContentLogger.ROOT_LOGGER.illegalChildType(address.getKey(), childType);
        }
        if (! (resource instanceof ManagedDMRContentResource)) {
            throw ManagedDMRContentLogger.ROOT_LOGGER.illegalChildClass(resource.getClass());
        }

        // Just attach ourself to this child so during the course of this operation it can access data
        ManagedDMRContentResource child = ManagedDMRContentResource.class.cast(resource);
        child.setParent(this);
    }

    @Override
    public void registerChild(PathElement address, int index, Resource resource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Resource removeChild(PathElement address) {
        final Resource toRemove = getChild(address);
        if (toRemove != null) {
            synchronized (content) {
                content.remove(address.getValue());
            }
            try{
                storeContent();
            } catch (IOException e) {
                throw new ContentStorageException(e);
            }
        }
        return toRemove;
    }

    @Override
    public boolean isRuntime() {
        return false;
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public Set<String> getOrderedChildTypes() {
        return Collections.emptySet();
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public Resource clone() {
        return new ManagedDMRContentTypeResource(this);
    }

    @Override
    public String getName() {
        return this.address.getLastElement().getValue();
    }

    @Override
    public PathElement getPathElement() {
        return this.address.getLastElement();
    }

    ManagedContent getManagedContent(final String name) {
        return content.get(name);
    }

    byte[] storeManagedContent(final String name, final ModelNode content) throws IOException {
        final byte[] hash = hashContent(content);
        synchronized (this.content) {
            this.content.put(name, new ManagedContent(content, hash));
        }
        storeContent();
        return hash;
    }

    private void loadContent(byte[] initialHash) {
        VirtualFile vf = contentRepository.getContent(initialHash);
        if (vf == null) {
            throw ManagedDMRContentLogger.ROOT_LOGGER.noContentFoundWithHash(HashUtil.bytesToHexString(initialHash));
        }
        InputStream is = null;
        try {
            is = vf.openStream();
            ModelNode node = ModelNode.fromStream(is);
            if (node.isDefined()) {
                for (Property prop : node.asPropertyList()) {
                    ModelNode value = prop.getValue();
                    byte[] hash = hashContent(value);
                    synchronized (content) {
                        content.put(prop.getName(), new ManagedContent(value, hash));
                    }
                }
            }
            this.model.get(ModelDescriptionConstants.HASH).set(initialHash);
            contentRepository.addContentReference(new ContentReference(address.toCLIStyleString(), initialHash));
        } catch (IOException e) {
            throw new ContentStorageException(e);
        } finally {
            safeClose(is);
        }
    }

    private void storeContent() throws IOException {
        final ModelNode node = new ModelNode();
        boolean hasContent;
        ContentReference oldReference = null;
        if(this.model.hasDefined(HASH)) {
            oldReference = new ContentReference(address.toCLIStyleString(), this.model.get(HASH).asBytes());
        }
        synchronized (content) {
            hasContent = !content.isEmpty();
            if (hasContent) {
                for (Map.Entry<String, ManagedContent> entry : content.entrySet()) {
                    node.get(entry.getKey()).set(entry.getValue().content);
                }
            }
        }
        if (hasContent) {
            ByteArrayInputStream bais = new ByteArrayInputStream(node.toString().getBytes(StandardCharsets.UTF_8));
            byte[] ourHash = contentRepository.addContent(bais);
            this.model.get(HASH).set(ourHash);
            this.contentRepository.addContentReference(new ContentReference(address.toCLIStyleString(), ourHash));
        } else {
            this.model.get(HASH).clear();
        }
        if(oldReference != null) {
            this.contentRepository.removeContent(oldReference);
        }
    }

    private byte[] hashContent(ModelNode content) throws IOException {
        byte[] sha1Bytes;
        OutputStream os = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                // just discard
            }
        };
        synchronized (messageDigest) {
            messageDigest.reset();

            DigestOutputStream dos = new DigestOutputStream(os, messageDigest);
            ByteArrayInputStream bis = new ByteArrayInputStream(content.toString().getBytes(StandardCharsets.UTF_8));
            byte[] bytes = new byte[8192];
            int read;
            while ((read = bis.read(bytes)) > -1) {
                dos.write(bytes, 0, read);
            }

            sha1Bytes = messageDigest.digest();
        }
        return sha1Bytes;
    }

    private ResourceEntry getChildEntry(String name) {
        return new ManagedDMRContentResource(PathElement.pathElement(childType, name) ,this);
    }

    private static void safeClose(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    static class ManagedContent {
        private final ModelNode content;
        private final byte[] hash;

        ManagedContent(ModelNode content, byte[] hash) {
            this.content = content;
            this.hash = hash;
        }

        ModelNode getContent() {
            return content.clone();
        }

        byte[] getHash() {
            return Arrays.copyOf(hash, hash.length);
        }
    }
}
