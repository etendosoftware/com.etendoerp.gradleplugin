package com.etendoerp.gradle.jars.modules

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify that the build/classes dir of a module is included in the Jar")
@Narrative(""" Some of the modules contains build validation classes, used by the BuildValidationHandler.
By default the handler looks in each module for the 'build/classes' dir.
""")
class CreationOfJarWithValidationClassTest extends ModuleToJarSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Issue("ERP-588")
    def "Creation of a JAR from a module with Validation classes"() {
        given: "A Etendo project with the module '#moduleToJar' to be converted to JAR "
        def module = moduleToJar

        and: "The module contains java classes '#classesModuleJar' in the 'src' folder to be compiled"
        def srcLocation = "${testProjectDir.absolutePath}/modules/$module/src/${moduleToPath(module)}"
        createJavaFiles([module: module, location: srcLocation, javaClasses: classesModuleJar])

        and: "The module contains the 'AD_MODULE' file"
        def moduleLocation = "${testProjectDir.absolutePath}/modules/$module/"
        ModuleToJarUtils.createADModuleFile([baseLocation: moduleLocation, moduleProperties: moduleToJarProperties])

        and: "The users runs the 'createModuleBuild' task previous to the publication of the module"
        def moduleBuildResult = runTask(":createModuleBuild","-Ppkg=${module}", "-Prepo=${repository}") as BuildResult
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "There is validation classes in the build/class dir of the module"
        def validationFile = new File("${testProjectDir.absolutePath}/modules/$module/build/classes/ValidationClass.class")
        if (!validationFile.getParentFile().exists()) {
            validationFile.getParentFile().mkdirs()
        }
        validationFile.createNewFile()
        assert validationFile.exists()

        and: "The users runs the 'generate.entities' task"
        def entitiesResult = runTask(":generate.entities") as BuildResult
        entitiesResult.task(":generate.entities").outcome == TaskOutcome.SUCCESS

        and: "The corresponding '.class' files from the module '#moduleToJar' are in the 'build/classes' directory"
        validateClassFiles(module, classesModuleJar, [])

        when: "The jar task is ran in the module '#moduleToJar'"
        def jarResult = runTask(":modules:$module:jar", "-Ppkg=$module") as BuildResult

        then: "The task will complete successfully."
        jarResult.task(":modules:$module:jar").outcome == TaskOutcome.SUCCESS

        and: "The jar file will be created in the /build/libs of the module folder."
        def jarFile = new File("${testProjectDir.absolutePath}/modules/$module/build/libs/$module-${moduleToJarProperties.version}.jar")
        assert jarFile.exists()

        and: "The jar file will only contain the 'classes' from the module '#moduleToJar'"
        def srcClassFiles = getListOfClasses(module, classesModuleJar, [])
        containsClassFiles(jarFile, srcClassFiles)

        and: "The jar will contain the 'build/classes' dir with the validation class"
        containsValidationClasses(jarFile, ["ValidationClass.class"], module)

        where:
        moduleToJarProperties                                                               | repository    | moduleToJar        | classesModuleJar
        [javapackage: "com.test.module1", version: "1.0.0", description: "com.test.module1"]| "etendo-test" | "com.test.module1" | ["Module1Class"]
    }

    void containsValidationClasses(File jarFile, List<String> classes, String moduleName) {
        def location = "META-INF/etendo/modules/${moduleName}/build/classes/"
        def validationFiles = getFilesFromJar([jarFile: jarFile, pathToSearch: location])

        Set validationFilesSet = validationFiles.collect({it.replace(location,"")}).flatten() as Set
        Set moduleClassesSet = classes.flatten() as Set

        assert validationFilesSet == moduleClassesSet
    }

}
