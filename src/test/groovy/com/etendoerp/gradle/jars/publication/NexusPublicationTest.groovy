package com.etendoerp.gradle.jars.publication

import com.etendoerp.gradle.jars.EtendoMockupSpecificationTest
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.*

@Title("Jars publication test")
@Narrative("""
    These test cases check the publication of the Core and a module in Nexus as a Jar
""")

class NexusPublicationTest extends EtendoMockupSpecificationTest {

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
    def "jarCorePublication"() {
        given: "Etendo_Core project"

        when: "Execute the publishCoreJar task"
        def result = runTask(":publishCoreJar") as BuildResult

        then: "The user should obtain a BUILD SUCCESS message"
        result.task(":publishCoreJar").outcome == TaskOutcome.SUCCESS
    }

    @Issue("ERP-585")
    def "jarModulePublication"() {
        given: "A Etendo module created ‘com.test.nontransactional’"
        def module = "com.test.nontransactional"

        when: "A users wants to publish a JAR version of the module in the Nexus repository ‘etendo-test’"
        and: "The users runs the command ./gradlew publishVersion -Ppkg=com.test.mymodule -Prepo=etendo-test"
        def result = runTask(":publishVersion", "-Ppkg=$module", "-Prepo=etendo-test")

        then: "The user should obtain a BUILD SUCCESS message"
        and: "The module should be publicated in the Nexus repository ‘etendo-test’"
        result.task(":publishVersion").outcome == TaskOutcome.SUCCESS
    }

}
