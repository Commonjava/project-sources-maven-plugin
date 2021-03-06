import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.logging.console.ConsoleLogger

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

File tgz = new File( dir, "${project.artifactId}-${version}-project-sources.tar.gz")

if ( !tgz.exists() )
{
    System.out.println("Cannot find tar archive: ${tgz}" )
    return false
}

final TarGZipUnArchiver ua = new TarGZipUnArchiver( tgz );
ua.enableLogging( new ConsoleLogger(Logger.LEVEL_DEBUG, "verify") );
destDir.mkdirs();
ua.setDestDirectory(destDir);
ua.extract();

def root = "${project.artifactId}-${version}"
File rootDir = new File( destDir, root );

def filesPresent = [
    "src/main/java/org/test/App.java",
    "src/test/java/org/test/AppTest.java",
    "pom.xml",
    "verify.groovy"
    ]

boolean missing = false;
filesPresent.each {
    if ( !new File( rootDir, it ).exists() )
    {
        System.out.println("${it} not present in archive!")
        missing = true;
    }
}
if (missing) {
    return false;
}

def filesMissing = [
    "target/classes/org/test/App.class",
    "target/test-classes/org/test/AppTest.class",
    "build.log",
    "src/test/java/.svn/entries"
    ]

boolean present = false;
filesMissing.each{
    if ( new File( rootDir, it ).exists() )
    {
        System.out.println("${it} is present in archive, but should not be!")
        present = true;
    }
}
if (present) {
    return false;
}

return true
