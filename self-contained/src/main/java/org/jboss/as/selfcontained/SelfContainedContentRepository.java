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

package org.jboss.as.selfcontained;

import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A content-repository capable of providing a static bit of content.
 *
 * @see org.jboss.as.selfcontained.ContentProvider
 *
 * @author Bob McWhirter
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class SelfContainedContentRepository implements ContentRepository, Service<ContentRepository> {

    /** Install the service. */
    public static void addService(ServiceTarget serviceTarget) {
        SelfContainedContentRepository contentRepository = new SelfContainedContentRepository();
        serviceTarget.addService(SERVICE_NAME, contentRepository)
                .addDependency( ContentProvider.NAME, ContentProvider.class, contentRepository.getContentProviderInjector() )
                .install();
    }

    private InjectedValue<ContentProvider> contentProviderInjector = new InjectedValue<>();

    public Injector<ContentProvider> getContentProviderInjector() {
        return this.contentProviderInjector;
    }

    @Override
    public byte[] addContent(InputStream stream) throws IOException {
        return new byte[0];
    }

    @Override
    public void addContentReference(ContentReference reference) {
    }

    @Override
    public File getContent(byte[] hash) {
        // A single-element array is the sentinal
        if ( hash.length == 1 ) {
            return this.contentProviderInjector.getValue().getContent( hash[0] );
        }
        return null;
    }

    @Override
    public boolean hasContent(byte[] hash) {
        if ( hash.length == 1 ) {
            return this.contentProviderInjector.getValue().getContent( hash[0] ) != null;
        }
        return false;
    }

    @Override
    public boolean syncContent(ContentReference reference) {
        return true;
    }

    @Override
    public void removeContent(ContentReference reference) {

    }

    @Override
    public Map<String, Set<String>> cleanObsoleteContent() {
        HashMap<String, Set<String>> result = new HashMap<>();
        result.put( ContentRepository.MARKED_CONTENT, Collections.<String>emptySet());
        result.put( ContentRepository.DELETED_CONTENT, Collections.<String>emptySet());
        return result;
    }

    @Override
    public void start(StartContext startContext) throws StartException {

    }

    @Override
    public void stop(StopContext stopContext) {

    }

    @Override
    public ContentRepository getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

}
