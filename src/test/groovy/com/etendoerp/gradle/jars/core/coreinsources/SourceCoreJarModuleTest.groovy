package com.etendoerp.gradle.jars.core.coreinsources

import com.etendoerp.gradle.jars.EtendoCoreSourcesSpecificationTest
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

@Title("Test with Sources core - sources modules - jar modules.")
@Narrative(""" This test uses the core in sources with a module in sources.
The module in sources has a dependency of a module in Jar. The test verify that
the module in Jar is installed correctly after the 'update.database' task.
""")
@Stepwise
class SourceCoreJarModuleTest extends EtendoCoreSourcesSpecificationTest {

    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    DBCleanupMode cleanDatabase() {
        return DBCleanupMode.ONCE
    }

    public final static String SOURCE_MODULE_GROUP = "com.openbravo"
    public final static String SOURCE_MODULE_NAME  = "gps.purchase.pgr"

    public final static String JAR_MODULE_GROUP = "com.test"
    public final static String JAR_MODULE_NAME  = "dummymodule"

    def "Installing Etendo sources core with source modules"() {
        given: "A Etendo sources core environment"
        expandMock()
        def expandResult = runTask(":expandCoreMock")
        assert expandResult.task(":expandCoreMock").outcome == TaskOutcome.SUCCESS

        and: "The users adds a sources module dependency"
        def moduleGroup = SOURCE_MODULE_GROUP
        def moduleName = SOURCE_MODULE_NAME
        buildFile << """
        dependencies {
          moduleDeps('${moduleGroup}:${moduleName}:[1.0.0,)@zip') { transitive = true }
        }

        repositories {
          maven {
            url 'https://repo.futit.cloud/repository/maven-unsupported-releases'
          }
        }
        """

        and: "The users runs the 'expandModules' task."
        def expandModulesResult = runTask(":expandModules")
        assert expandModulesResult.task(":expandModules").outcome == TaskOutcome.SUCCESS

        and: "The module is resolved correctly"
        def moduleLocation = new File("${getProjectDir().absolutePath}/modules/${moduleGroup}.${moduleName}")
        assert moduleLocation.exists()

        when: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The environment should contain the source module"
        CoreUtils.containsModule("${moduleGroup}.${moduleName}", getDBConnection())
    }

    def "Creating the build gradle file in the source module"() {
        given: "A source module to be convented to a gradle subproject"
        def module = "${SOURCE_MODULE_GROUP}.${SOURCE_MODULE_NAME}"

        when: "The users runs the 'createModuleBuild' task"
        def moduleBuildResult = runTask(":createModuleBuild","-P${PKG}=${module}", "-P${REPO}=etendo-test") as BuildResult

        then: "The task will finish successfully"
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The 'build.gradle' file will be created in the module location"
        def buildFile = new File("${testProjectDir.absolutePath}/modules/${module}/build.gradle")
        assert buildFile.exists()
    }

    def "Adding a Etendo jar module dependency to a Source module"() {
        given: "A Etendo jar module added to the Source module"

        def sourceModule = "${SOURCE_MODULE_GROUP}.${SOURCE_MODULE_NAME}"
        def sourceModuleLocation = new File("${testProjectDir.absolutePath}/modules/${sourceModule}")

        def sourceModuleBuildFile = new File(sourceModuleLocation, "build.gradle")

        sourceModuleBuildFile << """
        dependencies {
          implementation ('${JAR_MODULE_GROUP}:${JAR_MODULE_NAME}:1.0.0') { transitive = true }
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

        and: "The users creates a class in the Source module using the Jar module"
        def pathModule = sourceModule.replace(".", File.separator)
        def javaClass = new File("${sourceModuleLocation.absolutePath}/src/${pathModule}/CustomClass.java")
        if (!javaClass.getParentFile().exists()) {
            javaClass.getAbsoluteFile().mkdirs()
        }

        javaClass.createNewFile()

        javaClass << """
        package com.openbravo.gps.purchase.pgr;
        
        import ${JAR_MODULE_GROUP}.${JAR_MODULE_NAME}.*;

        public class CustomClass {
            public void test() {
                DummyModuleClass dummy = new DummyModuleClass();
            }
        }
        
        """

        when: "The users compiles the project"
        def compileResult = runTask("compileJava")
        def smartbuildResult = runTask("smartbuild")

        then: "The task will finish successfully"
        compileResult.task(":compileJava").outcome == TaskOutcome.SUCCESS
        smartbuildResult.task(":smartbuild").outcome == TaskOutcome.SUCCESS
    }

    def "The Jar module is installed correctly"() {
        given: "A Jar module to be installed"
        def module = "${JAR_MODULE_GROUP}.${JAR_MODULE_NAME}"

        when: "The users runs the 'update.database' task"
        def updateResult = runTask("update.database")

        then: "The task will finish successfully"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        and: "The jar module will be installed"
        CoreUtils.containsModule(module, getDBConnection())
    }

    def "successfully runs #task after install a Jar module"() {
        expect: "successfully runs #task"
        def result = runTask(task)
        result.task(":${task}").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        where:
        task                        | _
        "export.database"           | _
        "smartbuild"                | _
        "compile.complete"          | _
        "compile.complete.deploy"   | _
    }

}
