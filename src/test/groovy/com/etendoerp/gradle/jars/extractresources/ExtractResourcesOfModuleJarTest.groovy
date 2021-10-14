package com.etendoerp.gradle.jars.extractresources

import com.etendoerp.gradle.jars.JarsUtils
import com.etendoerp.gradle.jars.modules.ModuleToJarUtils
import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify that the task 'extractResourcesOfJar' extracts the resources of a Etendo dependency.")
@Narrative(""" When a Etendo dependency is added it should be resolved correctly. When any task of the root project is ran,
the 'extractResourcesOfJar' should be trigger. This task should extract the resources found in the 'META-INF'
folder of the JAR file in the 'build/etendo/modules' directory""")
class ExtractResourcesOfModuleJarTest extends EtendoSpecification {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def "Adding a Etendo dependency to the build gradle file is resolved correctly and extracted in the 'build' directory"() {
        given: "A module to be convented to a gradle subproject"
        def module = moduleName

        and: "The module contains the 'AD_MODULE' file"
        def moduleLocation = "${testProjectDir.absolutePath}/modules/$module/"
        ModuleToJarUtils.createADModuleFile([baseLocation: moduleLocation, moduleProperties: moduleProperties])

        and: "The users runs the 'createModuleBuild' task"
        def moduleBuildResult = runTask(":createModuleBuild","-P${PKG}=${module}", "-P${REPO}=${repository}", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}") as BuildResult

        and: "The task will finish successfully"
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The 'build.gradle' file will be created in the module location"
        def buildFile = new File("${testProjectDir.absolutePath}/modules/${module}/build.gradle")
        assert buildFile.exists()

        and: "The users sets a Etendo dependency in the 'build.gradle' file of the module"
        buildFile << JarsUtils.generateDependenciesBlock(dependencies)

        // Running any task from the root project should trigger the 'extractResourcesOfJar' task.
        when: "The users runs the 'dependencies' task"
        def dependenciesTaskResult = runTask(":dependencies")

        then: "The task will finish successfully."
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        and: "The output will contain the dependencies added."
        containDependencies(dependencies, dependenciesTaskResult.output)

        and: "The 'build/etendo' directory should contain the resources extracted from the Etendo modules dependencies"
        containsModuleResources(dependencies)

        where:
        moduleProperties                                                                        | moduleName           | repository    | dependencies
        [javapackage: "com.test.etendodep", version: "1.0.0", description: "com.test.etendodep"]| "com.test.etendodep" | "etendo-test" |["com.test:dummytopublish:1.0.0"]
    }

    static void containDependencies(List<String> dependencies, String taskOutput) {
        dependencies.each {
            assert taskOutput.contains(it)
        }
    }

    void containsModuleResources(List<String> etendoDependencies) {
        etendoDependencies.each {
            def moduleName = getModuleName(it)
            def moduleResources = new File("${getProjectDir().absolutePath}/build/etendo/${BASE_MODULE}/${moduleName}")
            assert moduleResources.exists()
        }
    }

    String getModuleName(String dependency) {
        def parts = dependency.split(":")
        def group = parts[0]
        def name = parts[1]
        return "${group}.${name}"
    }

}
