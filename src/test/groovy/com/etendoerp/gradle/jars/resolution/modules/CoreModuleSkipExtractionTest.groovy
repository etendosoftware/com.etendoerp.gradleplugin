package com.etendoerp.gradle.jars.resolution.modules

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Skip extraction of a JAR module already in SOURCES")
@Narrative("""
    Given a module already in sources, if the users specifies a JAR dependency of the module,
    or by transitivity the module to extract is the same of the Sources one, the extraction
    should be skipped.
   
    modules/
        |----B:1.0.0
    
    The users adds A:1.0.0 to the build.gradle
    
    Where A:1.0.0 depends on B:[1.0.0, 1.0.1]    
    
    A:1.0.0
       |--- B:[1.0.0, 1.0.1]
    
""")
@Issue("EPL-104")
class CoreModuleSkipExtractionTest extends EtendoCoreResolutionSpecificationTest{
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return "1.0.0"
    }

    def "Skipping extraction of a JAR module already in sources"() {
        given: "A Etendo core '#coreType'"

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", supportJars: supportJars, ignoreExpandMenu : true, forceResolution : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The user specifies a source module to be expanded (B:1.0.0)"
        buildFile << """
            dependencies {
              moduleDeps('com.test:transitiveB:1.0.0@zip') {
                transitive = true
              }
            }
        """

        // TODO: Add flag menu RDY

        and: "The user runs the 'expandModules' task to obtain the module B"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS
        def modulesLocation = new File(testProjectDir, "modules")
        def modulesBLocation = new File(modulesLocation, "com.test.transitiveB")
        assert modulesBLocation.exists()

        when: "The users adds a JAR module (A:1.0.0) that has a transitive dependency to B"
        buildFile << """
        dependencies {
          implementation('com.test:transitiveA:1.0.0')
        }
        """

        then: "The JAR module B SHOULD NOT be extracted in the 'build/etendo/modules' directory"
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        def etendoModulesLocation = new File(testProjectDir, "build/etendo/modules/com.test.transitiveB")
        assert !etendoModulesLocation.exists()

        where:
        coreType  | supportJars | _
        "sources" | false       | _
        "sources" | true        | _
        "jar"     | true        | _
    }

}
