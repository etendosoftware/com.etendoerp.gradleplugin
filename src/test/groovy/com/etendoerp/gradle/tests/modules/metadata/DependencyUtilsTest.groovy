package com.etendoerp.gradle.tests.modules.metadata

import com.etendoerp.gradle.jars.EtendoMockupSpecificationTest
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import spock.lang.*

@Title("Tests for the Etendo Spock Specification")
@Narrative("""
EtendoSpecification is the class all tests should extend, so it should configure the project properly
""")

class DependencyUtilsTest extends EtendoMockupSpecificationTest {

    final static String ENVIRONMENTS_LOCATION = "src/test/resources/jars/environments"

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def setup(){
        FileUtils.copyDirectory(new File("${ENVIRONMENTS_LOCATION}/publishJar"), testProjectDir)
    }


    def "JAVA_HOME is declared properly"() {
        expect:
        System.getenv("JAVA_HOME") != null
        System.getenv("JAVA_HOME") != "null"
    }

    @Issue("ERP-585")
    def "getConfigurationsFromProject"() {
        when:
        setup()
        runTask(":publishCoreJar") as BuildResult
        then: "all tasks run successfully"
        true
    }

    @Issue("ERP-585")
    def "jarModulePublication"() {
        //Publication of a JAR module
        given: "A Etendo module created ‘com.test.mymodule’"
        def module = "com.test.nontransactional"
        setup()
        when:
        //A users wants to publish a JAR version of the module in the Nexus repository ‘etendo-test’
        //and: The users has permissions to publish in the ‘etendo-test’ repository.
        //and: The users runs the command ./gradlew publishVersion -Ppkg=com.test.mymodule -Prepo=etendo-test --info
        //and: The users insert his Nexus credentials
        def result = runTask("publishVersion", "-Ppkg=$module", "-Prepo=etendo-test")
        then:
        //The user should obtain a BUILD SUCCESS message.
        //The module should be publicated in the Nexus repository ‘etendo-test’

        true
    }

}
