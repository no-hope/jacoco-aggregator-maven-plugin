package org.nohope.maven.plugin.jacoco.internal;

/**
 * @author <a href="mailto:ketoth.xupack@gmail.com">Ketoth Xupack</a>
 * @since 2013-10-29 23:09
 */

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Creates an IBundleCoverage.
 */
public final class BundleCreator {

    private final MavenProject project;
    private final FileFilter fileFilter;

    /**
     * Construct a new BundleCreator given the MavenProject and FileFilter.
     *
     * @param project
     *            the MavenProject
     * @param fileFilter
     *            the FileFilter
     */
    public BundleCreator(final MavenProject project, final FileFilter fileFilter) {
        this.project = project;
        this.fileFilter = fileFilter;
    }

    /**
     * Create an IBundleCoverage for the given ExecutionDataStore.
     *
     * @param executionDataStore
     *            the execution data.
     * @return the coverage data.
     * @throws java.io.IOException
     *             if class files can't be read
     */
    public IBundleCoverage createBundle(
            final ExecutionDataStore executionDataStore) throws IOException {
        final CoverageBuilder builder = new CoverageBuilder();
        final Analyzer analyzer = new Analyzer(executionDataStore, builder);
        final File classesDir = new File(this.project.getBuild().getOutputDirectory());

        @SuppressWarnings("unchecked")
        final List<File> filesToAnalyze = FileUtils.getFiles(classesDir,
                fileFilter.getIncludes(), fileFilter.getExcludes());

        for (final File file : filesToAnalyze) {
            analyzer.analyzeAll(file);
        }

        return builder.getBundle(this.project.getName());
    }
}
