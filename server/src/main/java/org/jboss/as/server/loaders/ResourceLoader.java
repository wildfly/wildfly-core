package org.jboss.as.server.loaders;

import org.jboss.modules.IterableResourceLoader;

import java.io.File;
import java.net.URL;
import java.util.Iterator;

/**
 * Resource loader. Every resource laoder is associated with either deployment, subdeployment or resource inside the deployment.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ResourceLoader extends IterableResourceLoader {

    /**
     * Returns loader's root file. It can be either directory or packaged jar archive.
     * @return loaders's root file
     */
    File getRoot();

    /**
     * Returns loader's root URL. The URL protocol is either 'jar' or 'file'.
     * @return root URL
     */
    URL getRootURL();

    /**
     * Returns loader path. It is either empty string if the loader is associated with top level deployment,
     * or it is loader's relative path to its parent
     * @return loader path
     */
    String getPath();

    /**
     * Returns parent loader of this loader. It returns null if loader is associated with top deployment.
     * @return parent loader of this loader
     */
    ResourceLoader getParent();

    /**
     * Returns child loader of this loader associated with given path.
     * @param path to lookup loader for
     * @return child loader associated with the path
     */
    ResourceLoader getChild(String path);

    /**
     * Iterates sub paths under given start path.
     * @param startPath to search for sub paths
     * @param recursive whether search is recursive or not
     * @return sub paths of given path
     */
    Iterator<String> iteratePaths(String startPath, boolean recursive);

    /**
     * Registers overlay with this loader. The overlay is automatically propagated to child loaders.
     * @param path to register overlay for
     * @param content the given overlay
     */
    void addOverlay(String path, File content);

}
