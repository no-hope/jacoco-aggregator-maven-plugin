package org.nohope.maven.plugin.jacoco;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.*;
import org.jacoco.report.csv.CSVFormatter;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import org.nohope.maven.plugin.jacoco.internal.BundleCreator;
import org.nohope.maven.plugin.jacoco.internal.FileFilter;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.nohope.maven.plugin.jacoco.ReportFormat.*;

/**
 * @author <a href="mailto:ketoth.xupack@gmail.com">Ketoth Xupack</a>
 * @since 2013-10-28 12:51
 */
public abstract class AbstractJacocoPlugin extends AbstractMojo {

    /** Encoding of the generated reports. */
    @Parameter(property = "project.reporting.outputEncoding", defaultValue = "UTF-8")
    protected String outputEncoding;

    /** Encoding of the source files. */
    @Parameter(property = "project.build.sourceEncoding", defaultValue = "UTF-8")
    protected String sourceEncoding;

    /** {@code true} to fail on missing data files in {@code dataFiles} list. */
    @Parameter
    protected boolean strict = false;

    /** Do not produce report for each module in multi-module project. */
    @Parameter
    protected boolean skipModule = true;

    /** Flg for aggregating each module report in multi-module environment. */
    @Parameter
    protected boolean aggregateRoot = true;

    @Parameter
    protected List<ReportFormat> reportFormats = Collections.emptyList();

    /**
     * A list of class files to include in the report. May use wildcard
     * characters (* and ?). When not specified everything will be included.
     */
    @Parameter
    protected List<String> includes = Collections.emptyList();

    /**
     * A list of class files to exclude from the report. May use wildcard
     * characters (* and ?). When not specified nothing will be excluded.
     */
    @Parameter
    protected List<String> excludes = Collections.emptyList();

    /** Flag used to suppress execution. */
    @Parameter(property = "skip.jacoco", defaultValue = "false")
    protected boolean skip;

    /** Maven project. */
    @Component
    protected MavenProject project;

    /** The projects in the reactor for aggregation report. */
    @Parameter(property = "reactorProjects", readonly = true)
    protected List<MavenProject> reactorProjects = Collections.emptyList();

    @Parameter
    protected List<String> dataFiles = Collections.emptyList();

    @Parameter
    protected List<String> sources = Arrays.asList(
            "src/main/java",
            "src/main/groovy",
            "src/main/scala");

    @Parameter
    protected List<String> excludeModules = Collections.emptyList();

    /**
     * This method is called when the report generation is invoked directly as a
     * standalone Mojo.
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (!canGenerateReport()) {
            return;
        }

        try {
            executeReport(Locale.getDefault());
        } catch (final MavenReportException e) {
            throw new MojoExecutionException("An error has occurred in Jacoco report generation.", e);
        }
    }

    private void initializeAndRenderNonHtml() {
        if (!"pom".equals(project.getPackaging()) && !skipModule && !reportFormats.contains(html)) {
            try {
                executeReport(Locale.getDefault(), false);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            } catch (final MavenReportException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public boolean canGenerateReport() {
        if (skip) {
            getLog().info("Skipping JaCoCo execution");
            return false;
        }

        initializeAndRenderNonHtml();

        if (dataFiles == null) {
            getLog().warn("Skipping JaCoCo execution due to missing execution data file");
            return false;
        }

        return !(!skipModule && !reportFormats.contains(html))
               && !(skipModule && !project.equals(getLastProject()))
               && !(aggregateRoot && !project.equals(getLastProject()));
    }

    protected void executeReport(final Locale locale) throws MavenReportException {
        try {
            executeReport(locale, false);
            if (project.equals(getLastProject())) {
                executeReport(locale, true);
            }
        } catch (final IOException e) {
            throw new MavenReportException("Error while creating report: " + e.getMessage(), e);
        }
    }

    private MavenProject getLastProject() {
        final int size = reactorProjects.size();
        return reactorProjects.get(size - 1);
    }

    private void executeReport(final Locale locale, final boolean root)
            throws IOException, MavenReportException {

        final boolean exec = root ? aggregateRoot : !skipModule;
        if (!exec) {
            return;
        }

        final File outputDirectory = getOutputDirectory(root ? getRootProject() : project);
        final IReportVisitor mainVisitor = createVisitor(locale, outputDirectory);
        boolean visited = false;

        final ExecFileLoader loader = loadExecutionData(root);
        mainVisitor.visitInfo(
                loader.getSessionInfoStore().getInfos(),
                loader.getExecutionDataStore().getContents());


        final List<Pattern> patterns = new ArrayList<Pattern>();
        for (final String excludeModule : excludeModules) {
            patterns.add(Pattern.compile(excludeModule));
        }

        if (root) {
            final IReportGroupVisitor subProjectVisitor =
                    mainVisitor.visitGroup(getRootProject().getName());

            for (final MavenProject child : reactorProjects) {
                boolean skip = false;
                for (final Pattern pattern : patterns) {
                    if (pattern.matcher(child.getArtifactId()).matches()
                        || pattern.matcher(child.getGroupId() + ':' + child.getArtifactId()).matches()) {
                        skip = true;
                        break;
                    }
                }

                if (skip) {
                    continue;
                }

                visited |= visitProject(loader, subProjectVisitor, child);
            }
        } else {
            visited = visitProject(loader, mainVisitor, project);
        }

        if (visited) {
            mainVisitor.visitEnd();
        }
    }

    private IReportVisitor createVisitor(final Locale locale, final File outputDirectory)
            throws IOException {
        final List<IReportVisitor> visitors = new ArrayList<IReportVisitor>();

        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("Unable to create " + outputDirectory);
        }

        if (reportFormats.contains(xml)) {
            final XMLFormatter xmlFormatter = new XMLFormatter();
            xmlFormatter.setOutputEncoding(outputEncoding);
            visitors.add(xmlFormatter.createVisitor(new FileOutputStream(new File(
                    outputDirectory, "jacoco.xml"))));
        }

        if (reportFormats.contains(csv)) {
            final CSVFormatter csvFormatter = new CSVFormatter();
            csvFormatter.setOutputEncoding(outputEncoding);
            visitors.add(csvFormatter.createVisitor(new FileOutputStream(new File(
                    outputDirectory, "jacoco.csv"))));
        }

        if (reportFormats.contains(html)) {
            final HTMLFormatter htmlFormatter = new HTMLFormatter();
            htmlFormatter.setOutputEncoding(outputEncoding);
            htmlFormatter.setLocale(locale);
            visitors.add(htmlFormatter.createVisitor(new FileMultiReportOutput(
                    outputDirectory)));
        }

        return new MultiReportVisitor(visitors);
    }

    /**
     * @return {@code true} if project was actually visited
     *
     * @throws java.io.IOException
     */
    private boolean visitProject(final ExecFileLoader loader,
                                 final IReportGroupVisitor visitor,
                                 final MavenProject project) throws IOException {

        // skip processing modules with "pom" packaging
        final File classesDir = new File(project.getBuild().getOutputDirectory());
        if ("pom".equals(project.getPackaging()) || !classesDir.exists()) {
            return false;
        }

        final FileFilter fileFilter = new FileFilter(this.includes, this.excludes);
        final BundleCreator creator = new BundleCreator(project, fileFilter);
        final IBundleCoverage bundle = creator.createBundle(loader.getExecutionDataStore());

        final SourceFileCollection locator = new SourceFileCollection(
                getCompileSourceRoots(project),
                sourceEncoding);

        checkForMissingDebugInformation(bundle);
        visitor.visitBundle(bundle, locator);

        return true;
    }

    private List<File> getCompileSourceRoots(final MavenProject project) {
        final Set<File> result = new HashSet<File>();
        for (final String path : project.getCompileSourceRoots()) {
            result.add(resolvePath(project, path));
        }

        for (final String path : sources) {
            result.add(resolvePath(project, path));
        }
        return new ArrayList<File>(result);
    }

    private void checkForMissingDebugInformation(final ICoverageNode node) {
        if (node.getClassCounter().getTotalCount() > 0
            && node.getLineCounter().getTotalCount() == 0) {
            getLog().warn("To enable source code annotation class "
                    + "files have to be compiled with debug information.");
        }
    }

    private ExecFileLoader loadExecutionData(final boolean root) throws MavenReportException {
        final ExecFileLoader loader = new ExecFileLoader();
        try {
            if (root) {
                for (final MavenProject project : reactorProjects) {
                    loadExecFile(loader, project);
                }
            } else {
                loadExecFile(loader, project);
            }
        } catch (final IOException e) {
            throw new MavenReportException("Unable to read execution data file: " + e.getMessage(), e);
        }

        return loader;
    }

    private void loadExecFile(final ExecFileLoader loader, final MavenProject project)
            throws IOException, MavenReportException {
        for (final String dataFile : dataFiles) {
            final File file = resolvePath(project, dataFile);
            if (file.exists()) {
                loader.load(file);
            } else {
                if (strict) {
                    throw new MavenReportException("File " + file + " not exists");
                }
            }
        }
    }

    private static File resolvePath(final MavenProject project, final String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(project.getBasedir(), path);
        }
        return file;
    }

    private MavenProject getRootProject() {
        for (final MavenProject reactorProject : reactorProjects) {
            if (reactorProject.isExecutionRoot()) {
                return reactorProject;
            }
        }
        throw new IllegalStateException("Unable to determine root project");
    }

    protected abstract File getOutputDirectory(final MavenProject project);

    protected static File join(final File file, final String... parts) {
        final List<String> elements = new ArrayList<String>(Arrays.asList(parts));
        File result = file;
        if (file == null && parts.length > 0) {
            result = new File(elements.remove(0));
        }

        for (final String e : elements) {
            result = new File(result, e);
        }

        return result;
    }

    private static class SourceFileCollection implements ISourceFileLocator {

        private final List<File> sourceRoots;
        private final String encoding;

        public SourceFileCollection(final List<File> sourceRoots,
                                    final String encoding) {
            this.sourceRoots = sourceRoots;
            this.encoding = encoding;
        }

        @Override
        public Reader getSourceFile(final String packageName,
                                    final String fileName) throws IOException {
            final String r;
            if (!packageName.isEmpty()) {
                r = packageName + '/' + fileName;
            } else {
                r = fileName;
            }
            for (final File sourceRoot : sourceRoots) {
                final File file = new File(sourceRoot, r);
                if (file.exists() && file.isFile()) {
                    return new InputStreamReader(new FileInputStream(file),
                            encoding);
                }
            }
            return null;
        }

        @Override
        public int getTabWidth() {
            return 4;
        }
    }
}
