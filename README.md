# Simpler `project.[zip|tar.gz]` (Source Archive) Production

This plugin simply wraps an invocation of the Maven Assembly Plugin's built-in `project` assembly, which creates archives containing a buildable project directory. It is basically an archive of your project's root directory without including any of the files created when the build runs.

This plugin was created to make the creation of project-sources archives as simple as declaring a plugin execution with a single goal and no configuration whatsoever. One handy side effect is that it also allows tools to inject project-sources creation into project POMs without affecting pre-existing assembly plugin executions.

## `m2eclipse` Ignores this Plugin

As of the 1.0 release, this plugin includes a `META-INF/m2e/lifecycle-mapping-metadata.xml` file that tells m2eclipse to ignore it when Eclipse rebuilds your project.
