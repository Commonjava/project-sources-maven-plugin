/**
 * Copyright (C) 2012 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.maven.plugins.projectsrc;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugin.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugin.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugin.assembly.io.AssemblyReadException;
import org.apache.maven.plugin.assembly.io.AssemblyReader;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.plugin.assembly.utils.AssemblyFormatUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenFileFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal that wraps an invocation of the <code>project</code> built-in assembly descriptor (in the assembly plugin). This allows drastically simpler
 * configuration, along with isolation from pre-existing assembly-plugin configurations (allowing this plugin to be injected via tooling with minimal
 * risk of collision).
 */
/* @formatter:off */
@Mojo( name = "archive", 
        requiresDependencyResolution = ResolutionScope.NONE, 
        requiresDependencyCollection = ResolutionScope.NONE, 
        requiresOnline = false, 
        requiresProject = true, 
        defaultPhase = LifecyclePhase.INITIALIZE )
/* @formatter:on */
public class ProjectSourcesGoal
    extends AbstractMojo
    implements AssemblerConfigurationSource
{

    private static final String PROJECT_DESCRIPTOR = "project";

    private static final String CLASSIFIER = "project-sources";

    @Component
    protected AssemblyArchiver archiver;

    @Component
    protected AssemblyReader reader;

    /**
     * Maven ProjectHelper.
     */
    @Component
    protected MavenProjectHelper projectHelper;

    /**
     * Maven shared filtering utility.
     */
    @Component
    protected MavenFileFilter mavenFileFilter;

    /**
     * The Maven Session Object
     */
    @Component
    protected MavenSession mavenSession;

    @Parameter( defaultValue = "${basedir}", required = true, readonly = true )
    protected File basedir;

    @Parameter( defaultValue = "${project.build.finalName}", required = true, readonly = true )
    protected String assemblyRootFolder;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    protected List<MavenProject> reactorProjects;

    @Parameter( defaultValue = "${project}", required = true, readonly = true )
    protected MavenProject project;

    /**
     * Temporary directory that contain the files to be assembled.
     */
    @Parameter( defaultValue = "${project.build.directory}/projectsrc-archive-tmp", required = true, readonly = true )
    protected File tempRoot;

    /**
     * Directory to unpack JARs into if needed
     */
    @Parameter( defaultValue = "${project.build.directory}/projectsrc-work", required = true )
    protected File workDirectory;

    /**
     * The output directory of the assembled distribution file.
     */
    @Parameter( defaultValue = "${project.build.directory}", required = true, readonly = true )
    protected File outputDirectory;

    /**
     * The filename of the assembled distribution file.
     */
    @Parameter( defaultValue = "${project.build.finalName}", required = true, readonly = true )
    protected String finalName;

    /**
     * When set to 'true' the project-sources.zip will NOT be produced during the build.
     */
    @Parameter( property = "project.src.skip" )
    protected boolean skipProjectSources;

    /**
     * Allow to specify formats to be generated. Default value is "tar.gz". Please follow link below for list of possible values
     * @see <a href="https://maven.apache.org/plugins/maven-assembly-plugin/single-mojo.html#formats">https://maven.apache.org/plugins/maven-assembly-plugin/single-mojo.html#formats</a>
     *
     */
    @Parameter( property = "formats", defaultValue = "tar.gz")
    protected String formats;

    protected ProjectSourcesGoal()
    {
    }

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skipProjectSources )
        {
            getLog().info( "Assemblies have been skipped per configuration of the skipAssembly parameter." );
            return;
        }

        // run only at the execution root.
        if ( !isThisTheExecutionRoot() )
        {
            getLog().info( "Skipping the assembly in this project because it's not the Execution Root" );
            return;
        }

        final List<String> assemblyFormats = getAssemblyFormats( formats );

        final Assembly assembly = getAssembly( assemblyFormats );

        try
        {
            final String fullName = AssemblyFormatUtils.getDistributionName( assembly, this );

            AssemblerConfigurationSource configSourceForArchive = assemblyRootFolderNameDiffersFromFinalName() ? createConfigSourceForArchive(this) : this;
            for ( final String format : assembly.getFormats() )
            {
                final File destFile = archiver.createArchive( assembly, fullName, format, configSourceForArchive, true );

                final MavenProject project = getProject();
                projectHelper.attachArtifact( project, format, assembly.getId(), destFile );
            }
        }
        catch ( final ArchiveCreationException e )
        {
            throw new MojoExecutionException( "Failed to create assembly: " + e.getMessage(), e );
        }
        catch ( final AssemblyFormattingException e )
        {
            throw new MojoExecutionException( "Failed to create assembly: " + e.getMessage(), e );
        }
        catch ( final InvalidAssemblerConfigurationException e )
        {
            throw new MojoFailureException( assembly, "Assembly is incorrectly configured: " + assembly.getId(),
                                            "Assembly: " + assembly.getId() + " is not configured correctly: "
                                                + e.getMessage() );
        }
    }

    static List<String> getAssemblyFormats(String formats) {
        List<String> parsedList = asList(formats.split(","));
        List<String> list = new ArrayList<String>();
        int size = parsedList.size();
        for(int i = 0; i < size; i++) {
            list.add( parsedList.get(i).trim() );
        }
        return unmodifiableList(list);
    }

    private AssemblerConfigurationSource createConfigSourceForArchive(final AssemblerConfigurationSource other) {
        return new AssemblerConfigurationSource() {

            @Override
            public String getDescriptor() {
                return other.getDescriptor();
            }

            @Override
            public String getDescriptorId() {
                return other.getDescriptorId();
            }

            @Override
            public String[] getDescriptors() {
                return other.getDescriptors();
            }

            @Override
            public String[] getDescriptorReferences() {
                return other.getDescriptorReferences();
            }

            @Override
            public File getDescriptorSourceDirectory() {
                return other.getDescriptorSourceDirectory();
            }

            @Override
            public File getBasedir() {
                return other.getBasedir();
            }

            @Override
            public MavenProject getProject() {
                return other.getProject();
            }

            @Override
            public boolean isSiteIncluded() {
                return other.isSiteIncluded();
            }

            @Override
            public File getSiteDirectory() {
                return other.getSiteDirectory();
            }

            @Override
            public String getFinalName() {
                return assemblyRootFolder;
            }

            @Override
            public boolean isAssemblyIdAppended() {
                return other.isAssemblyIdAppended();
            }

            @Override
            public String getClassifier() {
                return other.getClassifier();
            }

            @Override
            public String getTarLongFileMode() {
                return other.getTarLongFileMode();
            }

            @Override
            public File getOutputDirectory() {
                return other.getOutputDirectory();
            }

            @Override
            public File getWorkingDirectory() {
                return other.getWorkingDirectory();
            }

            @Override
            public MavenArchiveConfiguration getJarArchiveConfiguration() {
                return other.getJarArchiveConfiguration();
            }

            @Override
            public ArtifactRepository getLocalRepository() {
                return other.getLocalRepository();
            }

            @Override
            public File getTemporaryRootDirectory() {
                return other.getTemporaryRootDirectory();
            }

            @Override
            public File getArchiveBaseDirectory() {
                return other.getArchiveBaseDirectory();
            }

            @Override
            public List<String> getFilters() {
                return other.getFilters();
            }

            @Override
            public List<MavenProject> getReactorProjects() {
                return other.getReactorProjects();
            }

            @Override
            public List<ArtifactRepository> getRemoteRepositories() {
                return other.getRemoteRepositories();
            }

            @Override
            public boolean isDryRun() {
                return other.isDryRun();
            }

            @Override
            public boolean isIgnoreDirFormatExtensions() {
                return other.isIgnoreDirFormatExtensions();
            }

            @Override
            public boolean isIgnoreMissingDescriptor() {
                return other.isIgnoreMissingDescriptor();
            }

            @Override
            public MavenSession getMavenSession() {
                return other.getMavenSession();
            }

            @Override
            public String getArchiverConfig() {
                return other.getArchiverConfig();
            }

            @Override
            public MavenFileFilter getMavenFileFilter() {
                return other.getMavenFileFilter();
            }

            @Override
            public boolean isUpdateOnly() {
                return other.isUpdateOnly();
            }

            @Override
            public boolean isUseJvmChmod() {
                return other.isUseJvmChmod();
            }

            @Override
            public boolean isIgnorePermissions() {
                return other.isIgnorePermissions();
            }

            @Override
            public String getEncoding() {
                return other.getEncoding();
            }

            @Override
            public String getEscapeString() {
                return other.getEscapeString();
            }
        };
    }

    private Assembly getAssembly(List<String> assemblyFormats)
        throws MojoExecutionException, MojoFailureException
    {
        List<Assembly> assemblies;
        try
        {
            assemblies = reader.readAssemblies( this );
        }
        catch ( final AssemblyReadException e )
        {
            throw new MojoExecutionException( "Error reading assemblies: " + e.getMessage(), e );
        }
        catch ( final InvalidAssemblerConfigurationException e )
        {
            throw new MojoFailureException( reader, e.getMessage(), "Mojo configuration is invalid: " + e.getMessage() );
        }

        if ( assemblies == null || assemblies.isEmpty() )
        {
            throw new MojoExecutionException( "Cannot read '" + PROJECT_DESCRIPTOR + "' assembly descriptor!" );
        }

        final Assembly assembly = assemblies.get( 0 );
        assembly.setId( CLASSIFIER );
        assembly.setFormats( assemblyFormats );

        return assembly;
    }

    /**
     * Returns true if the current project is located at the Execution Root Directory (where mvn was launched)
     * 
     * @return
     */
    private boolean isThisTheExecutionRoot()
    {
        final Log log = getLog();
        log.debug( "Root Folder:" + mavenSession.getExecutionRootDirectory() );
        log.debug( "Current Folder:" + basedir );
        final boolean result = mavenSession.getExecutionRootDirectory()
                                           .equalsIgnoreCase( basedir.toString() );
        if ( result )
        {
            log.debug( "This is the execution root." );
        }
        else
        {
            log.debug( "This is NOT the execution root." );
        }

        return result;
    }

    @Override
    public File getArchiveBaseDirectory()
    {
        return null;
    }

    @Override
    public String getArchiverConfig()
    {
        return null;
    }

    @Override
    public File getBasedir()
    {
        return basedir;
    }

    @Override
    public String getClassifier()
    {
        return null;
    }

    @Override
    public String getDescriptor()
    {
        return null;
    }

    @Override
    public String getDescriptorId()
    {
        return null;
    }

    @Override
    public String[] getDescriptorReferences()
    {
        return new String[] { PROJECT_DESCRIPTOR };
    }

    @Override
    public File getDescriptorSourceDirectory()
    {
        return null;
    }

    @Override
    public String[] getDescriptors()
    {
        return null;
    }

    @Override
    public String getEncoding()
    {
        return null;
    }

    @Override
    public String getEscapeString()
    {
        return null;
    }

    @Override
    public List<String> getFilters()
    {
        return null;
    }

    @Override
    public String getFinalName()
    {
        return finalName;
    }

    @Override
    public MavenArchiveConfiguration getJarArchiveConfiguration()
    {
        return null;
    }

    @Override
    public ArtifactRepository getLocalRepository()
    {
        return mavenSession.getLocalRepository();
    }

    @Override
    public MavenFileFilter getMavenFileFilter()
    {
        return mavenFileFilter;
    }

    @Override
    public MavenSession getMavenSession()
    {
        return mavenSession;
    }

    @Override
    public File getOutputDirectory()
    {
        return outputDirectory;
    }

    @Override
    public MavenProject getProject()
    {
        return project;
    }

    @Override
    public List<MavenProject> getReactorProjects()
    {
        return reactorProjects;
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public List<ArtifactRepository> getRemoteRepositories()
    {
        return project.getRemoteArtifactRepositories();
    }

    @Override
    public File getSiteDirectory()
    {
        return null;
    }

    @Override
    public String getTarLongFileMode()
    {
        return "gnu";
    }

    @Override
    public File getTemporaryRootDirectory()
    {
        return tempRoot;
    }

    @Override
    public File getWorkingDirectory()
    {
        return workDirectory;
    }

    @Override
    public boolean isAssemblyIdAppended()
    {
        return true;
    }

    @Override
    public boolean isDryRun()
    {
        return false;
    }

    @Override
    public boolean isIgnoreDirFormatExtensions()
    {
        return false;
    }

    @Override
    public boolean isIgnoreMissingDescriptor()
    {
        return false;
    }

    @Override
    public boolean isIgnorePermissions()
    {
        return false;
    }

    @Override
    public boolean isSiteIncluded()
    {
        return false;
    }

    @Override
    public boolean isUpdateOnly()
    {
        return false;
    }

    @Override
    public boolean isUseJvmChmod()
    {
        return true;
    }

    private boolean assemblyRootFolderNameDiffersFromFinalName() {
        return (assemblyRootFolder != null && !assemblyRootFolder.equals(finalName));
    }
}
