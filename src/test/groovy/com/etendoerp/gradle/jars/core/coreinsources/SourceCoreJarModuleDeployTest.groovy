package com.etendoerp.gradle.jars.core.coreinsources

import com.etendoerp.gradle.jars.EtendoCoreSourcesSpecificationTest
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify the correct deploy and extraction of resources from a module jar")
@Narrative("""This test adds a module in jar dependency, runs an install and smartbuild to
see if the resources of the jar ('.html, .xml, etc') are deployed in the WebContent folder.""")
@Stepwise
class SourceCoreJarModuleDeployTest extends EtendoCoreSourcesSpecificationTest {
    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    DBCleanupMode cleanDatabase() {
        return DBCleanupMode.ONCE
    }

    public final static String JAR_MODULE_GROUP = "com.test"
    public final static String JAR_MODULE_NAME  = "dummymodule"

    def "Installing Etendo sources core with a module in Jar"() {
        given: "A Etendo sources core environment"
        expandMock()
        def expandResult = runTask(":expandCoreMock")
        assert expandResult.task(":expandCoreMock").outcome == TaskOutcome.SUCCESS

        and: "The users adds a jar module dependency"
        def moduleGroup = JAR_MODULE_GROUP
        def moduleName = JAR_MODULE_NAME
        buildFile << """
        dependencies {
          implementation('${moduleGroup}:${moduleName}:[1.0.0,)') { transitive = true }
        }
        
        repositories {
          maven {
            url 'https://repo.futit.cloud/repository/etendo-test'
          }
        }
        """

        and: "The users runs the 'dependencies' task"
        def dependenciesResult = runTask("dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        assert dependenciesResult.task(":dependencies").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        and: "The dependencies contains the Jar module"
        assert dependenciesResult.output.contains("${moduleGroup}:${moduleName}")

        when: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The environment should contain the jar module"
        assert CoreUtils.containsModule("${moduleGroup}.${moduleName}", getDBConnection())
    }

    def "The users runs a 'smartbuild' after install and the module should be deployed to the WebContent"() {
        given: "The users run the smartbuild task"
        def result = runTask("smartbuild")
        result.task(":smartbuild").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        expect: "The module files should be deployed in the web content folder"
        def resourceDummyFile  = "dummytesthtml.html"
        def moduleLocationPath = JAR_MODULE_GROUP.replace(".",File.separator) + File.separator + JAR_MODULE_NAME

        def fileLocation = new File("${testProjectDir.absolutePath}/WebContent/src-loc/design/${moduleLocationPath}/${resourceDummyFile}")
        assert fileLocation.exists()
    }
}
