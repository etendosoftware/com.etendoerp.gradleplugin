package com.etendoerp.gradle.jars.resolution.webcontentexclusion

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Skip adding a JAR already in sources to the WebContent")
@Narrative("""
When a transitive dependency is already in SOURCES, the JAR should not be extracted and
added to the WebContent directory after running a smartbuild.
""")
@Issue("EPL-104")
class CoreInstallSkipJarToWebContentTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreRepo() {
        return SNAPSHOT_REPOSITORY_URL
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "running smartbuild with a transitive dependency already in sources"() {
        given: "A user installing the Etendo environment"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true, ignoreDisplayMenu : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        addRepositoryToBuildFile(ETENDO_CORE_REPO)

        and: "The user specifies a source module to be expanded (B:1.0.0)"
        buildFile << """
            dependencies {
              moduleDeps('com.test:transitiveB:1.0.0@zip') {
                transitive = true
              }
            }
        """

        and: "The user runs the 'expandModules' task to obtain the module B"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS
        def modulesLocation = new File(testProjectDir, "modules")
        def modulesBLocation = new File(modulesLocation, "com.test.transitiveB")
        assert modulesBLocation.exists()
        new File(modulesBLocation, "src-db").deleteDir()

        and: "The users adds a JAR module (A:1.0.0) that has a transitive dependency to B"
        buildFile << """
        dependencies {
          implementation('com.test:transitiveA:1.0.0')
        }
        """

        def dependenciesTaskResult = runTask(":dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        def moduleAlocation = new File(testProjectDir, "build/etendo/modules/com.test.transitiveA")
        assert moduleAlocation.exists()

        and: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        when: "The users runs the smartbuild task"
        def smartbuildResult = runTask("smartbuild")
        smartbuildResult.task(":smartbuild").outcome  == TaskOutcome.SUCCESS

        then: "The transitive dependency B should not be copied to the WebContent"
        def webContentLibs = new File(testProjectDir, "WebContent/WEB-INF/lib")

        def containsAJarFile = false
        def containsBJarFile = false

        webContentLibs.listFiles().each {
            if (it.name.contains("transitiveA")) {
                containsAJarFile = true
            }
            if (it.name.contains("transitiveB")) {
                containsBJarFile = true
            }
        }

        // The WebContent should contain the A JAR file
        assert containsAJarFile

        // The WebContent should NOT contain the B JAR file (already in sources)
        assert !containsBJarFile

        where:
        coreType  | _
        "sources" | _
        "jar"     | _

    }

}
