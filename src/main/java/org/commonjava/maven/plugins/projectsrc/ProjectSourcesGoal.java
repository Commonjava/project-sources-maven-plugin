package org.commonjava.maven.plugins.projectsrc;

/*
 * Licensed to the Red Hat under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Red Hat licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.List;

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
import org.codehaus.plexus.configuration.PlexusConfiguration;

/**
 * Goal that wraps an invocation of the <code>project</code> built-in assembly descriptor (in the assembly plugin). This allows drastically simpler
 * configuration, along with isolation from pre-existing assembly-plugin configurations (allowing this plugin to be injected via tooling with minimal
 * risk of collision).
 */
/* @formatter:off */
@Mojo( name = "zip", 
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

    private static final String[] FORMATS = { "zip" };

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

    @Parameter( property = "project.src.skip" )
    protected boolean skipAssembly;

    @Parameter( defaultValue = "${basedir}", required = true, readonly = true )
    protected File basedir;

    @Parameter( property = "project.src.dryRun" )
    protected boolean dryRun;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    protected List<MavenProject> reactorProjects;

    @Parameter( defaultValue = "${project}", required = true, readonly = true )
    protected MavenProject project;

    /**
     * Temporary directory that contain the files to be assembled.
     */
    @Parameter( defaultValue = "${project.build.directory}/archive-tmp", required = true, readonly = true )
    protected File tempRoot;

    /**
     * Directory to unpack JARs into if needed
     */
    @Parameter( defaultValue = "${project.build.directory}/assembly/work", required = true )
    protected File workDirectory;

    /**
     * The output directory of the assembled distribution file.
     */
    @Parameter( defaultValue = "${project.build.directory}", required = true )
    protected File outputDirectory;

    /**
     * The filename of the assembled distribution file.
     */
    @Parameter( defaultValue = "${project.build.finalName}", required = true )
    protected String finalName;

    /**
     * Allows additional configuration options that are specific to a particular type of archive format. This is
     * intended to capture an XML configuration that will be used to reflectively setup the options on the archiver
     * instance. <br/>
     * For instance, to direct an assembly with the "ear" format to use a particular deployment descriptor, you should
     * specify the following for the archiverConfig value in your plugin configuration: <br/>
     * <p/>
     * <pre>
     * &lt;appxml&gt;${project.basedir}/somepath/app.xml&lt;/appxml&gt;
     * </pre>
     */
    @Parameter
    protected PlexusConfiguration archiverConfig;

    /**
     * The character encoding scheme to be applied when filtering resources.
     */
    @Parameter( property = "encoding", defaultValue = "${project.build.sourceEncoding}" )
    protected String encoding;

    /**
     * This is a set of instructions to the archive builder, especially for building .jar files. It enables you to
     * specify a Manifest file for the jar, in addition to other options.
     * See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven Archiver Reference</a>.
     */
    @Parameter
    protected MavenArchiveConfiguration archive;

    protected ProjectSourcesGoal()
    {
    }

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skipAssembly )
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

        for ( final Assembly assembly : assemblies )
        {
            try
            {
                final String fullName = AssemblyFormatUtils.getDistributionName( assembly, this );

                for ( final String format : FORMATS )
                {
                    final File destFile = archiver.createArchive( assembly, fullName, format, this, true );

                    final MavenProject project = getProject();
                    projectHelper.attachArtifact( project, format, CLASSIFIER, destFile );
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
        return archiverConfig == null ? null : archiverConfig.toString();
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
        return encoding;
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
        return archive;
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
        return dryRun;
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

}
