package com.etendoerp.gradle.jars.core.coreinjars

import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir

/**
 * This test should use the latest CORE snapshot
 */

class JarCoreExportSampleDataTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    public final static String PRE_EXPAND_MODULE_GROUP = "com.test"
    public final static String PRE_EXPAND_MODULE_NAME  = "premoduletoexpand"

    @Issue("EPL-13")
    def "Running the export sample data exports all the data to a custom module" () {
        given: "A Etendo environment with the Core dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", ignoreDisplayMenu : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users adds a sources module dependency before running the install"
        def preExpandModGroup = PRE_EXPAND_MODULE_GROUP
        def preExpandModName = PRE_EXPAND_MODULE_NAME
        buildFile << """
        dependencies {
          moduleDeps('${preExpandModGroup}:${preExpandModName}:[1.0.0,)@zip') { transitive = true }
        }

        repositories {
          maven {
            url 'https://repo.futit.cloud/repository/etendo-test'
          }
        }
        """

        and: "The users runs the expandCustomModule task passing by command line the  pre module to expand"
        def preExpandTask = runTask(":expandCustomModule", "-Ppkg=${preExpandModGroup}.${preExpandModName}", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        preExpandTask.task(":expandCustomModule").outcome == TaskOutcome.SUCCESS

        and: "The module will be expanded in the 'modules' dir "
        def preExpandModLocation = new File("${testProjectDir.getAbsolutePath()}/modules/${preExpandModGroup}.${preExpandModName}")
        assert preExpandModLocation.exists()

        // Run The install task
        and: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The pre install expanded module will be installed correctly"
        assert CoreUtils.containsModule("${preExpandModGroup}.${preExpandModName}", getDBConnection())

        when: "The users runs the export sample data task using a custom client"
        String client = "F&B International Group"
        String clientExported = "F_B_International_Group"
        def exportSampleDataResult = runTask(":export.sample.data","-Dclient=${client}","-Dmodule=${preExpandModGroup}.${preExpandModName}")

        then: "The task will finish successfully"
        exportSampleDataResult.task(":export.sample.data").outcome == TaskOutcome.SUCCESS

        and: "The sample data will be exported to the module folder"
        File moduleLocation = new File("${testProjectDir.absolutePath}/modules/${preExpandModGroup}.${preExpandModName}")
        File sampledataLocation = new File(moduleLocation,"referencedata/sampledata/${clientExported}")
        assert sampledataLocation.exists()

        where:
        coreType  | _
        "sources" | _
        "jar"     | _

    }

}
