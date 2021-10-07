package com.etendoerp.gradle.tests.distribution

import com.etendoerp.gradle.jars.modules.ModuleToJarSpecificationTest
import com.etendoerp.gradle.jars.modules.ModuleToJarUtils
import com.etendoerp.gradle.tests.EtendoSpecification
import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify the correct creation of the 'build.gradle' file.")
@Narrative("""The test creates the build.gradle file and verifies 
that the module is considered a gradle subproject and contains all the publication tasks""")
class CreateModuleBuildTest extends EtendoSpecification {
    static String BASE_MODULE = PublicationUtils.BASE_MODULE_DIR
    static String REPO = PublicationUtils.REPOSITORY_NAME_PROP
    static String PKG  = PublicationUtils.MODULE_NAME_PROP

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def setup() {
        def ant = new AntBuilder()

        //Override the default build file
        def buildXmlFile = new File("${ModuleToJarSpecificationTest.BASE_JAR_LOCATION}/build-publication.xml")
        def destFile = new File("${getProjectDir().absolutePath}/build.xml")
        ant.copy(file: buildXmlFile, tofile: destFile, overwrite: true)
    }

    def "Creation of the 'build gradle' file"() {
        given: "A module to be convented to a gradle subproject"
        def module = moduleToSubproject

        and: "The module contains the 'AD_MODULE' file"
        def moduleLocation = "${testProjectDir.absolutePath}/modules/$module/"
        ModuleToJarUtils.createADModuleFile([baseLocation: moduleLocation, moduleProperties: moduleProperties])

        when: "The users runs the 'createModuleBuild' task"
        def moduleBuildResult = runTask(":createModuleBuild","-P${PKG}=${module}", "-P${REPO}=${repository}") as BuildResult

        then: "The task will finish successfully"
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The 'build.gradle' file will be created in the module location"
        def buildFile = new File("${testProjectDir.absolutePath}/modules/${module}/build.gradle")
        assert buildFile.exists()

        and: "The module will be considerate a gradle subproject"
        def propertiesTaskResult = runTask(":${BASE_MODULE}:${module}:properties")
        propertiesTaskResult.task(":${BASE_MODULE}:${module}:properties").outcome == TaskOutcome.SUCCESS

        and: "The subproject will contain all the defined properties"
        containsProperties(moduleProperties, module, repository, propertiesTaskResult.output)

        and: "The subproject will contain the default tasks to publish"
        def subprojectTaskResult = runTask(":${BASE_MODULE}:${module}:tasks", "-P${PKG}=${module}")
        subprojectTaskResult.task(":${BASE_MODULE}:${module}:tasks").outcome == TaskOutcome.SUCCESS
        containsDefaultTasks(module, subprojectTaskResult.output)

        where:
        moduleProperties                                                                          | repository    | moduleToSubproject
        [javapackage: "com.test.module1sub", version: "1.0.0", description: "com.test.module1sub"]| "etendo-test" | "com.test.module1sub"
        [javapackage: "com.test.module2sub", version: "1.0.2", description: "com.test.module2sub"]| "etendo-test" | "com.test.module2sub"
    }


    def "Running 'createModuleBuildTask' with undefined module"() {
        given: "A an undefined module"
        def module = "com.test.undefined"
        def repository = "repo-test"

        when: "The users runs the 'createModuleBuild' task"
        def moduleBuildResult = runTaskAndFail(":createModuleBuild","-P${PKG}=${module}", "-P${REPO}=${repository}") as BuildResult

        then: "The task will fail"
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.FAILED

        and: "The output will show a descriptive message"
        moduleBuildResult.output.contains("'${getProjectDir().absolutePath}/modules/${module}/' does not exists")
    }

    void containsProperties(Map moduleProperties, String module, String repo, String taskOutput) {
        String javaPackage = moduleProperties.javapackage
        String version     = moduleProperties.version

        def group    = ModulesUtils.splitGroup(javaPackage)
        def artifact = ModulesUtils.splitArtifact(javaPackage)
        def repository = "${PublicationUtils.BASE_REPOSITORY_URL}$repo"

        assert taskOutput.contains("project: project ':${BASE_MODULE}:${module}'")
        assert taskOutput.contains("version: ${version}")
        assert taskOutput.contains("group: ${group}")
        assert taskOutput.contains("artifact: ${artifact}")
        assert taskOutput.contains("repository: ${repository}")
    }

    void containsDefaultTasks(String module, String taskOutput) {
        def capitalized = PublicationUtils.capitalizeModule(module)

        def mavenTaskName = "publish${capitalized}PublicationToMavenRepository"
        def jarTask = "jar -"
        def sourcesJarTask = "sourcesJar -"

        assert taskOutput.contains(mavenTaskName)
        assert taskOutput.contains(jarTask)
        assert taskOutput.contains(sourcesJarTask)
    }
}
