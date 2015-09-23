package org.jboss.as.server.loaders;

import org.jboss.modules.IterableResourceLoader;

import java.io.File;
import java.util.Iterator;

/**
 * Resource loader.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ResourceLoader extends IterableResourceLoader {

    ResourceLoader getParent();

    Iterator<String> iteratePaths(String startPath, boolean recursive);

    void addOverlay(String path, File content);

}
