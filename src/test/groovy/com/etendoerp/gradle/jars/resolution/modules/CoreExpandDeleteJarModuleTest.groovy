package com.etendoerp.gradle.jars.resolution.modules

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Expanding a module should delete the Jar version")
@Narrative("""
Expanding a source modules should delete the JAR version in the 'build/etendo/modules' dir
""")
@Issue("EPL-104")
class CoreExpandDeleteJarModuleTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    def "Deleting the extracted JAR module when working with sources"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(SNAPSHOT_REPOSITORY_URL)
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", ignoreDisplayMenu : true, forceResolution : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users adds a module dependency in JARs A:1.0.0"
        buildFile << """
        dependencies {
            implementation('com.test:transitiveA:1.0.0')
        }
        """

        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        and: "The module is extracted in the 'build/etendo/modules' dir"
        def moduleALocation = new File(testProjectDir, "build/etendo/modules/com.test.transitiveA")
        assert moduleALocation.exists()

        when: "The user wants to work with the module in Sources (specifies in the build.gradle ‘moduleDeps(A:1.0.0)’)"

        buildFile << """
            dependencies {
              moduleDeps('com.test:transitiveA:1.0.0@zip') {
                transitive = true
              }
            }
        """

        // TODO: Add flag menu Rdy

        and: "The user runs the ‘expandModules’ tasks"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS

        then: "Then the module A:1.0.0 should be extracted in the root ‘modules’ dir"
        def moduleASourcesLocation = new File(testProjectDir, "modules/com.test.transitiveA")
        assert moduleASourcesLocation.exists()

        and: "The JAR module A:1.0.0 in the ‘build/etendo/modules’ should be deleted."
        def moduleAJarsLocation = new File(testProjectDir, "build/etendo/modules/com.test.transitiveA")
        assert !moduleAJarsLocation.exists()

        where:
        coreType  | supportJars | _
        "sources" | false       | _
        "sources" | true        | _
        "jar"     | true        | _
    }

}
