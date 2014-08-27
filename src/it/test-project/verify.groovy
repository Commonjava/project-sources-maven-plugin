import java.io.File;
import java.util.zip.ZipFile;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

File dir = new File( localRepositoryPath, "org/test/test-project/1.0-SNAPSHOT" )
File destDir = new File( basedir, "target")

File tgz = new File( dir, "test-project-1.0-SNAPSHOT-project-sources.tar.gz")

if ( !tgz.exists() )
{
    System.out.println("Cannot find tar archive: ${tgz}" )
    return false
}

def project = new XmlSlurper().parseText( new File(basedir, "pom.xml").getText() )
def root = "${project.artifactId}-${project.version}"

final TarGZipUnArchiver ua = new TarGZipUnArchiver( tgz );
ua.enableLogging( new ConsoleLogger(Logger.LEVEL_DEBUG, "verify") );
destDir.mkdirs();
ua.setDestDirectory(destDir);
ua.extract();

File rootDir = new File( destDir, root );

def filesPresent = [
    "src/main/java/org/test/App.java",
    "src/test/java/org/test/AppTest.java",
    "pom.xml",
    "verify.groovy"
    ]

filesPresent.each{
    if ( !new File( rootDir, it ).exists() )
    {
        System.out.println("${it} not present in archive!")
        return false
    }
}

def filesMissing = [
    "target/classes/org/test/App.class",
    "target/test-classes/org/test/AppTest.class",
    "build.log",
    "src/test/java/.svn/entries"
    ]

filesMissing.each{
    if ( new File( rootDir, it ).exists() )
    {
        System.out.println("${it} is present in archive, but should not be!")
        return false
    }
}

return true
