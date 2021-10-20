package com.etendoerp.gradle.jars.generator

import com.etendoerp.gradle.jars.EtendoMockupSpecificationTest
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title
import java.nio.file.Paths
import java.util.zip.ZipFile


@Title("Test to verify that the task 'JarCoreGeneration' add all classes and resources of Etendo Core.")
@Narrative("""When a Jar task is executed in Core project, all classes should be added except which ones 
are generated, in addition, all resources should be added in META-INF/etendo directory. Also, the local JARs 
should be included as local dependencies. """)
@Stepwise
class JarCoreGeneratorTest extends EtendoMockupSpecificationTest {
    @TempDir
    File testProjectDir
    boolean installed = false

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    @Override
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

        given: "A dummy core project, with some .java files, and inner classes in src folder, and some .java in the src-gen directory."

        when: "Create a core Jar."
        def generateEntities = runTask(":generate.entities")
        def jar = runTask(":jar")

        and: "Obtains all classes from generated Jar."
        Set<String> jarClasses = new ArrayList<String>();
        new ZipFile("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").entries().each {
            String filePath = it.toString()
            if (filePath.endsWith(".class")) {
                jarClasses.add(filePath)
            }
        }

        and: "Obtains the project classes in build/classes."
        Set<String> buildClasses = new File("${testProjectDir.absolutePath}/build/classes").list()

        and: "Excluding all generated and inner generated classes."
        Set<String> generatedClasses = new File("${testProjectDir.absolutePath}/build/tmp/generated").text.split('\n')
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

    def "Creating Core Jar and check if the src resources ere correctly includes"() {
        given: "A dummy core project, with some .xml and .ftl files in src folder"
        when: "create a coreJar"
        runTask(":generate.entities")
        def jar = runTask(":jar")

        and: "the JAR Resources"
        Set<String> resourcesFromJar = new ArrayList<String>();
        new ZipFile("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").entries().each {
            if (it.toString().contains("META-INF/etendo/src") && !it.isDirectory()) {
                String fileName = Paths.get(it.toString()).getFileName().toString()
                resourcesFromJar.add(fileName)
            }
        }

        // Generating  resources files in src
        Set<String> resources = new File("${testProjectDir.absolutePath}/src").list()
        resources.removeAll {
            if (it.endsWith(".class") || it.endsWith(".java")) {
                it
            }
        }

        then: "The tasks run successfully, and the resources are added in META-INF/etendo/src"
        assert jar.task(":jar").outcome == TaskOutcome.SUCCESS
        assert new File("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").exists()
        assert resourcesFromJar == resources
    }

    def "Creating Core Jar and check if the beans.xml file is included"() {
        given: "A dummy core project, with beans.xml file in /modules_core/org.openbravo.base.weld/config/ folder"
        when: "create a coreJar"
        runTask(":generate.entities")
        def jar = runTask(":jar")

        and: "add in resourcesFromJar list the beans.xml file if exist"
        Set<String> resourcesFromJar = new ArrayList<String>();
        new ZipFile("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").entries().each {
            if (it.toString().contains("META-INF/beans.xml")) {
                String fileName = Paths.get(it.toString()).getFileName().toString()
                resourcesFromJar.add(fileName)
            }
        }

        and: "add in resources list the beans.xml file  from the expected directory"
        Set<String> resources = new File("${testProjectDir.absolutePath}/modules_core/org.openbravo.base.weld/config/").list()
        resources.removeAll {
            if (it.endsWith(".class") || it.endsWith(".java")) {
                it
            }
        }

        then: "The tasks run successfully, and the beans.xml file is in the JAR"
        assert jar.task(":jar").outcome == TaskOutcome.SUCCESS
        assert new File("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").exists()
        assert resourcesFromJar == resources
    }

    def "Creating Core Jar and check if the build.xml file is included"() {
        given: "A dummy core project, with build.xml"
        when: "create a coreJar"
        runTask(":generate.entities")
        def jar = runTask(":jar")

        and: "add in resourcesFromJar list the build.xml file if exist"
        Set<String> resourcesFromJar = new ArrayList<String>();
        new ZipFile("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").entries().each {
            if (it.toString().contains("META-INF/etendo/build.xml")) {
                String fileName = Paths.get(it.toString()).getFileName().toString()
                resourcesFromJar.add(fileName)
            }
        }

        and: "add in resources list the build.xml file  from the expected directory"
        Set<String> resources = new File("${testProjectDir.absolutePath}").list()
        resources.removeAll {
            if (it != "build.xml") {
                it
            }
        }

        then: "The tasks run successfully, and the build.xml file is in the JAR"
        assert jar.task(":jar").outcome == TaskOutcome.SUCCESS
        assert new File("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").exists()
        assert resourcesFromJar == resources
    }

    def "Creating Core Jar and check if the modules and modules_core are included"() {
        given: "A dummy core project, with a module and modules core"
        when: "create a coreJar"
        runTask(":generate.entities")
        def jar = runTask(":jar")

        and: "add in resourcesFromJar list the beans.xml file if exist"
        Set<String> resourcesFromJar = new ArrayList<String>();
        new ZipFile("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").entries().each {
            if (it.toString().contains("META-INF/etendo/modules")) {
                if(!it.isDirectory()){
                    String fileName = Paths.get(it.toString()).getFileName().toString()
                    resourcesFromJar.add(fileName)
                }
            }
        }

        and: "add in resources list the files  from the modules and modules_core directories"
        Set<String> resources = new File("${testProjectDir.absolutePath}/modules/testModule").list()
        resources.addAll( new File("${testProjectDir.absolutePath}/modules_core/org.openbravo.base.weld/config").list())
        resources.removeAll {
            if (!new File(it).isDirectory() && (it.endsWith(".class") || it.endsWith(".java"))) {
                it
            }
        }

        then: "The tasks run successfully, and the resources from modules and modules_core are in the Jar"
        assert jar.task(":jar").outcome == TaskOutcome.SUCCESS
        assert new File("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").exists()
        assert resourcesFromJar == resources
    }

    def "Creating Core Jar and check if the config directory are included"() {
        given: "A dummy core project"
        when: "create a coreJar"
        runTask(":generate.entities")
        def jar = runTask(":jar")

        and: "add in resourcesFromJar all the files in config directory"
        Set<String> resourcesFromJar = new ArrayList<String>();
        new ZipFile("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").entries().each {
            if (it.toString().contains("META-INF/etendo/config")) {
                if(!it.isDirectory()){
                    String fileName = Paths.get(it.toString()).getFileName().toString()
                    resourcesFromJar.add(fileName)
                }
            }
        }

        and: "add in resources list the files  from the config directory ends in .template"
        Set<String> resources = new File("${testProjectDir.absolutePath}/config").list()
        resources.removeAll {
            if (!it.endsWith(".template")) {
                it
            }
        }

        then: "The tasks run successfully, and the config files are in the Jar"
        assert jar.task(":jar").outcome == TaskOutcome.SUCCESS
        assert new File("${testProjectDir.absolutePath}/build/libs/etendo-core.jar").exists()
        assert resourcesFromJar == resources
    }
}
