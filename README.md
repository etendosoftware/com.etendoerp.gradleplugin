### README

To work with the plugin locally, create a directory named 'buildSrc' in the root of a Etendo project. Clone the repository inside the 'buildSrc' directory.

Create a 'build.gradle' file in the 'buildSrc' directory with the following content.
```
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    runtime subprojects.findAll { it.getTasksByName("jar", false) }
}

// Hack to prevent running all tests automatically
// To run all test use the command line parameter -Drun.all.tests=true
project.subprojects.each {
    it.test.onlyIf {
        Boolean.getBoolean('run.all.tests')
    }
}
```

Create a 'settings.gradle' file in the 'buildSrc' directory with the following content.

```
include "etendo_gradle_plugin"
```
