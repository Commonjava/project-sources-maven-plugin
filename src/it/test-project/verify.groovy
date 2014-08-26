import java.io.File;

File dir = new File( localRepositoryPath, "org/test/test-project/1.0-SNAPSHOT" );
return new File( dir, "test-project-1.0-SNAPSHOT-project-sources.zip").exists();
