package com.etendoerp.gradle.jars.resolution.webcontentexclusion

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Title

@Title("Removing old version of a module in the WebContent directory")
@Issue("EPL-115")
class ModuleRemoveOldVersionInsideWebContentTest extends EtendoCoreResolutionSpecificationTest{

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreRepo() {
        return SNAPSHOT_REPOSITORY_URL
    }



    def "Removing old version of a Module when updates version" () {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFileFirst(getCoreRepo())

        String moduleToUpdate = "moduleToUpdate"
        String oldModuleVersion = "1.0.0"
        String newModuleVersion = "1.0.1"

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The users adds a module dependency in JARs"
        addRepositoryToBuildFile("https://repo.futit.cloud/repository/etendo-test")
        buildFile << """
        dependencies {
            implementation('com.test:${moduleToUpdate}:${oldModuleVersion}')  
        }
        """

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users install the environment"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "After running the 'smartbuild' task, the MODULE JAR file will be deployed in the WebContent dir"
        def smartbuildResult = runTask("smartbuild")
        smartbuildResult.task(":smartbuild").outcome  == TaskOutcome.SUCCESS

        def webContentLibs = new File(testProjectDir, "WebContent/WEB-INF/lib")
        def moduleBeforeUpdate = new File(webContentLibs, "${moduleToUpdate}-${oldModuleVersion}.jar")
        assert  moduleBeforeUpdate.exists()


        when: "The users updates the MODULE with a new version "
        buildFile << """
        dependencies {
            implementation('com.test:${moduleToUpdate}:${newModuleVersion}')  
        }
        """

        and: "The users runs the smartbuild task"
        def reSmartbuildResult = runTask("smartbuild")
        reSmartbuildResult.task(":smartbuild").outcome  == TaskOutcome.SUCCESS

        then: "The MODULE dependency will be updated in the WebContent, the old one should be deleted"
        def moduleAfterUpdate = new File(webContentLibs, "${moduleToUpdate}-${newModuleVersion}.jar")

        assert !moduleBeforeUpdate.exists()
        assert moduleAfterUpdate.exists()


        where:
        coreType  | _
        "sources" | _
        "jar"     | _

    }

}
