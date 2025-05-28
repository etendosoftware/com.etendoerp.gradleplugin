package com.etendoerp.gradle.tests.installation

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.api.internal.tasks.TaskDependencyResolveException
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

@Title("Expand Task Tests")
@Narrative("""
Collection of tests for the Expand task.
The expand task should download core and module dependencies
""")
@Stepwise
class ExpandTest extends EtendoSpecification {
    @TempDir @Shared File testProjectDir
    boolean expanded = false

    String nexusUser = null
    String nexusPassword = null

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def isExpanded(TaskOutcome expandOutcome) {
        assert expandOutcome == TaskOutcome.SUCCESS

        expanded = true
    }

    def "Expand task does not fail"() {
        when: "running ./gradlew expandCore"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def result = runTask("expandCore")

        then: "the expand task does not result in an error"
        isExpanded(result.task(":expandCore").outcome)
    }

    def "Expand task has downloaded core files"() {
        when: "listing the project dir files (after expandCore)"
        expanded
        def projectFileNames = getProjectDir().list()

        then: "all main folders from the core dependency should exists"
        def antBuildFile = new File(testProjectDir, "build.xml")
        verifyAll {
            !antBuildFile.text.contains("dummy")
            projectFileNames.find {it == 'src'} != null
            projectFileNames.find {it == 'src-db'} != null
            projectFileNames.find {it == 'src-core'} != null
            projectFileNames.find {it == 'src-wad'} != null
            projectFileNames.find {it == 'src-test'} != null
            projectFileNames.find {it == 'src-util'} != null
            projectFileNames.find {it == 'config'} != null
            projectFileNames.find {it == 'lib'} != null
            projectFileNames.find {it == 'referencedata'} != null
            projectFileNames.find {it == 'modules_core'} != null
            projectFileNames.find {it == 'web'} != null
        }
    }

    def "Expand task has downloaded default module files"() {
        when: "listing the project module files (after expandCore)"
        expanded
        def modulesPath = getProjectDir().toPath().resolve("modules_core")
        def modules = modulesPath.toFile().list()

        then: "all main folders from the modules dependencies should exist"
        verifyAll {
            modules.find {it == 'com.smf.securewebservices'} != null
            modules.find {it == 'com.smf.smartclient.boostedui'} != null
            modules.find {it == 'com.smf.smartclient.debugtools'} != null
        }
    }

    def "ExpandModules task has downloaded external module files"() {
        given: "the user adds a dependency to the build.gradle"
        buildFile << """
        dependencies {
          moduleDeps('com.openbravo:gps.idl.stock:[1.1.0,)') { transitive = true }
        }
        
        repositories {
          maven {
            url 'https://repo.futit.cloud/repository/maven-unsupported-releases'
          }
        }
        """

        when: "listing the project module files (after expandCore)"
        buildFile.text.contains("com.openbravo:gps.idl.stock")

        changeExtensionPluginVariables([ignoreDisplayMenu : true])

        def result = runTask("expandModules")
        def modulesPath = getProjectDir().toPath().resolve("modules")
        def modules = modulesPath.toFile().list()

        then: "all main module dependencies should exist, even transitive ones"
        verifyAll {
            result.task(":expandModules").outcome == TaskOutcome.SUCCESS
            // Declared dependency
            modules.find {it == 'com.openbravo.gps.idl.stock'} != null
            // Transitive dependencies
            modules.find {it == 'org.openbravo.module.idljava'} != null
            modules.find {it == 'com.openbravo.gps.manageattributes'} != null
            modules.find {it == 'org.openbravo.idl'} != null
            modules.find {it == 'org.openbravo.utility.opencsv'} != null
        }
    }

    def "Mobile utils git submodule has files"() {
        when: "listing the contents of the module: com.smf.mobile.utils"
        expanded
        def mobileUtilsPath = getProjectDir().toPath().resolve("modules_core").resolve("com.smf.mobile.utils")
        def contents = mobileUtilsPath.toFile().list()

        then: "all main folders from the modules dependencies should exist"
        verifyAll {
            mobileUtilsPath.toFile().exists()
            contents.find {it == 'src'} != null
            contents.find {it == 'src-db'} != null
            contents.find {it == 'config'} != null
        }
    }
    @Ignore("Temporarily disabled")
    def "Expand task fails when unauthenticated or with incorrect credentials"() {
        given: "an empty gradle cache"
        def gradleTestKitDir = new DefaultGradleRunner().getTestKitDirProvider().getDir()
        if (gradleTestKitDir.exists()) {
            gradleTestKitDir.deleteDir()
        }

        when: "running ./gradlew expandCore (without authentication)"
        // Save properties to restore them later in the cleanup spec
        nexusUser = System.getProperty("nexusUser")
        nexusPassword = System.getProperty("nexusPassword")

        def nexusUser = System.getProperty("nexusUser")

        if (nexusUser) {
            buildFile.text = buildFile.text.replace(nexusUser, "")
        }

        if (credentials != null) {
            System.setProperty("nexusUser", credentials)
            System.setProperty("nexusPassword", credentials)
        } else {
            System.clearProperty("nexusUser")
            System.clearProperty("nexusPassword")
        }
        def success = true
        def result = null
        // use try/catch mechanic because runTaskAndFail does not throw
        // but also does not contain task outcomes due to the failure being capture dependency
        try {
            result = runTask("expandCore")
        } catch (UnexpectedBuildFailure ignored) {
            success = false
        }

        then: "the expand task results in an error"
        (result?.task(":expandCore")?.outcome == TaskOutcome.FAILED) || !success

        where:
        credentials     | _
        null            | _
        "incorrect"     | _
    }


    def cleanup() {
        // Restore cleared properties so that other tests have them
        if (nexusUser != null) {
            System.setProperty("nexusUser", nexusUser)
            nexusUser = null
        }
        if (nexusPassword != null) {
            System.setProperty("nexusPassword", nexusPassword)
            nexusPassword = null
        }
    }
}
