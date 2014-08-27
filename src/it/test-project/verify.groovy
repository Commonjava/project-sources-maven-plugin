import java.io.File;
import java.util.zip.ZipFile;

File dir = new File( localRepositoryPath, "org/test/test-project/1.0-SNAPSHOT" )
File zip = new File( dir, "test-project-1.0-SNAPSHOT-project-sources.zip")

def project = new XmlSlurper().parseText( new File(basedir, "pom.xml").getText() )
def root = "${project.artifactId}-${project.version}"

ZipFile zf = new ZipFile( zip )

def filesPresent = [
    "src/main/java/org/test/App.java",
    "src/test/java/org/test/AppTest.java",
    "pom.xml",
    "verify.groovy"
    ]

filesPresent.each{
    if ( null == zf.getEntry( "${root}/${it}" ) )
    {
        System.out.println("${it} not present in zip!")
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
    if ( null != zf.getEntry( "${root}/${it}" ) )
    {
        System.out.println("${it} is present in zip, but should not be!")
        return false
    }
}

return true
