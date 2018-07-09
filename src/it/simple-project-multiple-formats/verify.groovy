def project = new XmlSlurper().parseText( new File(basedir, "pom.xml").getText() )
def version = project.version
if ( version == null ){
    version = project.parent.version
}

def groupPath = project.groupId
if ( groupPath == null ){
    groupPath = project.parent.groupId
}

groupPath = groupPath.toString().replace('.', '/')

assert new File( basedir, "target/${project.artifactId}-${version}-project-sources.tar.gz" ).exists();

File dir = new File( localRepositoryPath, "${groupPath}/${project.artifactId}/${version}" )
File destDir = new File( basedir, "target")

File tgzFile = new File( dir, "${project.artifactId}-${version}-project-sources.tar.gz")

if ( !tgzFile.exists() )
{
    System.out.println( "Cannot find tar archive: ${tgzFile}" )
    return false
}

File zipFile = new File( dir, "${project.artifactId}-${version}-project-sources.zip")

if ( !zipFile.exists() )
{
    System.out.println( "Cannot find zip archive: ${zipFile}" )
    return false
}

return true
