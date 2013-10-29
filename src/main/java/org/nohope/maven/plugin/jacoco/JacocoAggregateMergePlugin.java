package org.nohope.maven.plugin.jacoco;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.nohope.maven.plugin.jacoco.internal.ExecFileLoader;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:ketoth.xupack@gmail.com">Ketoth Xupack</a>
 * @since 2013-10-28 10:45
 */
@Mojo(name="aggregate-merge",
        aggregator = true,
        defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST,
        requiresProject = true,
        threadSafe = true
)
public class JacocoAggregateMergePlugin extends AbstractMojo {
    @Parameter(property = "reactorProjects", readonly = true)
    private List<MavenProject> reactorProjects;

    /** Maven project. */
    @Component
    private MavenProject project;

    /**
     * Output directory for the reports. Note that this parameter is only
     * relevant if the goal is run from the command line or from the default
     * build lifecycle. If the goal is run indirectly as part of a site
     * generation, the output directory configured in the Maven Site Plugin is
     * used instead.
     */
    @Parameter(defaultValue = "${project.reporting.outputDirectory}/jacoco")
    private File outputDirectory;

    /**
     * This mojo accepts any number of execution data file sets.
     */
    @Parameter(property = "jacoco.outputFiles")
    private List<OutputFile> outputFiles;

    @Parameter(defaultValue = "true")
    private boolean strict;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final ExecFileLoader loader = new ExecFileLoader();

        getLog().warn("context: " + getPluginContext());

        if (!canMergeReports()) {
            return;
        }

        loadAndSave(loader);
    }

    private boolean canMergeReports() {
        if (outputFiles == null || outputFiles.isEmpty()) {
            getLog().info("No output files passed. Skipping");
            return false;
        }
        return true;
    }

    private void loadAndSave(final ExecFileLoader loader) throws MojoExecutionException {
        for (final OutputFile outputFile : outputFiles) {
            for (final MavenProject reactorProject : reactorProjects) {
                if (project.equals(reactorProject) || "pom".equals(reactorProject.getPackaging())) {
                    continue;
                }

                for (final String execFile: outputFile.getExecFiles()) {
                    final File inputFile = new File(reactorProject.getBasedir(), execFile);

                    if (!inputFile.isFile()) {
                        if (strict) {
                            throw new MojoExecutionException(inputFile + " not found or it's not a file");
                        }
                        continue;
                    }

                    try {
                        getLog().info("Loading execution data execFile " + inputFile.getAbsolutePath());
                        loader.load(inputFile);
                    } catch (final IOException e) {
                        throw new MojoExecutionException("Unable to load " + inputFile.getAbsolutePath(), e);
                    }
                }
            }

            final File destFile = new File(outputDirectory, outputFile.getOutputFile());
            if (loader.getExecutionDataStore().getContents().isEmpty()) {
                if (strict) {
                    throw new MojoExecutionException("No files found to write " + destFile);
                }
                getLog().warn("No files found to write " + destFile);
                continue;
            }

            getLog().info("Writing merged execution data to " + destFile.getAbsolutePath());
            try {
                loader.save(destFile, false);
            } catch (final IOException e) {
                throw new MojoExecutionException("Unable to write merged file " + destFile.getAbsolutePath(), e);
            }
        }
    }
}
