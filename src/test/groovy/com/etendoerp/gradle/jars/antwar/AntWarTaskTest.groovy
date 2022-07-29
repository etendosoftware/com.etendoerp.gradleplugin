package com.etendoerp.gradle.jars.antwar

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("antWar task creates the war file in the root lib dir")
@Narrative("""
When the core is in SOURCES o JAR, the antWar task should always create the war file
in the lib directory of the root project.
""")
@Issue("EPL-242")
class AntWarTaskTest extends EtendoCoreResolutionSpecificationTest {
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

    def "Running the antWar task creates de war file in the correct location"() {
        given: "A user installing the Etendo environment"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        when: "The users runs the antWar task"
        def smartbuildResult = runTask("antWar")

        then: "The task will finish successfully"
        smartbuildResult.task(":antWar").outcome  == TaskOutcome.SUCCESS

        and: "The war file will be created in the lib root dir"
        File libDir = new File(testProjectDir.absolutePath, "lib")
        assert libDir.exists()

        File warFile = new File(libDir, "etendo.war")
        assert warFile.exists()

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
