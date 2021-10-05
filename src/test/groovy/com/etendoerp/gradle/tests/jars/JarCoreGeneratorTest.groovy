package com.etendoerp.gradle.tests.jars

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

import java.util.stream.Stream
import java.util.zip.ZipFile

@Title("Test JarCore generation")
@Stepwise
class JarCoreGeneratorTest extends EtendoSpecification {
    @TempDir
    @Shared
    File testProjectDir

    boolean installed = false

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def setup() {
        FileUtils.copyDirectory(new File("src/test/resources/jars/environments/core"), testProjectDir)
    }

    def isInstalled(TaskOutcome expandOutcome, TaskOutcome setupOutcome, TaskOutcome installOutcome) {
        assert expandOutcome == TaskOutcome.SUCCESS
        assert setupOutcome == TaskOutcome.UP_TO_DATE
        assert (installOutcome == TaskOutcome.UP_TO_DATE) || (installOutcome == TaskOutcome.SUCCESS)

        installed = true
    }

    def "Creating Jar of core and check if the generated classes are excluded"() {
        when: "create a coreJar"
        def generateEntities = runTask(":generate.entities")
        def jar = runTask(":jar")

        // JAR classes
        Set<String> jarClasses = new ArrayList<String>();
        new ZipFile("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").entries().each {
            String filePath = it.toString()
            if (filePath.endsWith(".class")) {
                jarClasses.add(filePath)
            }
        }

        // build/classes - generated
        Set<String> buildClasses = new File("${testProjectDir.absolutePath}/build/classes").list()

        Set<String> generatedClasses = new File("${testProjectDir.absolutePath}/build/tmp/generated").text.split('\n')

        //Excluding all generated inner classes
        for (String generated : generatedClasses) {
            buildClasses.removeAll {
                if (it.contains(generated)) {
                    it
                }
            }
        }


        then: "The tasks run successfully, and the classes in Jar are the same that [build/clases]-generated"
        assert jar.task(":jar").outcome == TaskOutcome.SUCCESS
        assert generateEntities.task(":generate.entities").outcome == TaskOutcome.SUCCESS
        assert new File("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").exists()
        assert buildClasses == jarClasses

    }
}
