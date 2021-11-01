package com.etendoerp.gradle.jars.publication

import com.etendoerp.gradle.jars.EtendoMockupSpecificationTest
import com.etendoerp.gradle.utils.DBCleanupMode
import groovy.json.JsonParser
import groovy.json.JsonSlurper
import org.gradle.internal.impldep.com.google.api.client.json.Json
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.*


import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.time.Duration

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

    def cleanupSpec() {
        HttpURLConnection uc
        try {
            URL url = new URL( "https://repo.futit.cloud/service/rest/v1/components?repository=etendo-test-publish")
            uc = url.openConnection()
            uc.setRequestMethod("GET")
            String userPass = System.getProperty("nexusUser") + ":" + System.getProperty("nexusPassword")
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()));
            uc.setRequestProperty("Authorization", basicAuth);
            def responseText = uc.getInputStream().getText()
            def jsonSlurper = new JsonSlurper()
            def obj = jsonSlurper.parseText(responseText)
            for (def module :obj.items ){
                URL url2 = new URL("https://repo.futit.cloud/service/rest/v1/components/"+ module.id)
                uc = url2.openConnection()
                uc.setRequestMethod("DELETE")
                uc.setRequestProperty("Authorization", basicAuth);
                uc.getInputStream()
            }
        }
        catch (Exception e ){
            print(e)
        }
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
        def result = runTask(":publishVersion", "-Ppkg=$module", "-Prepo=etendo-test-publish")

        then: "The user should obtain a BUILD SUCCESS message"
        and: "The module should be publicated in the Nexus repository ‘etendo-test-publish’"
        result.task(":publishVersion").outcome == TaskOutcome.SUCCESS
    }
}
