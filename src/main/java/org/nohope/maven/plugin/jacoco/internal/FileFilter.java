package org.nohope.maven.plugin.jacoco.internal;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A file filter using includes/excludes patterns.
 */
public class FileFilter {

    private static final String DEFAULT_INCLUDES = "**";
    private static final String DEFAULT_EXCLUDES = "";

    private final List<String> includes;
    private final List<String> excludes;

    /**
     * Construct a new FileFilter
     *
     * @param includes
     *            list of includes patterns
     * @param excludes
     *            list of excludes patterns
     */
    public FileFilter(final List<String> includes, final List<String> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    /**
     * Returns a list of files.
     *
     * @param directory
     *            the directory to scan
     * @return a list of files
     * @throws IOException
     *             if file system access fails
     */
    @SuppressWarnings("unchecked")
    public List<String> getFileNames(final File directory) throws IOException {
        return FileUtils.getFileNames(directory, getIncludes(), getExcludes(), false);
    }

    /**
     * Get the includes pattern
     *
     * @return the pattern
     */
    public String getIncludes() {
        return buildPattern(this.includes, DEFAULT_INCLUDES);
    }

    /**
     * Get the excludes pattern
     *
     * @return the pattern
     */
    public String getExcludes() {
        return buildPattern(this.excludes, DEFAULT_EXCLUDES);
    }

    private static String buildPattern(final List<String> patterns,
                                       final String defaultPattern) {
        String pattern = defaultPattern;
        if (patterns != null && !pattern.isEmpty()) {
            pattern = StringUtils.join(patterns.iterator(), ",");
        }
        return pattern;
    }
}
