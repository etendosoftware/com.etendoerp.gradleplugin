package com.etendoerp.gradle.tests.configuration

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir

class CreateOBPropertiesTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    final static String OB_FILE = "Openbravo.properties"

    def "Creation of the 'Openbravo properties' file."() {
        given: "A expanded project"
        def expandResult = runTask(":expand")

        and: "The expand task finalizes successfully"
        expandResult.task(":expand").outcome == TaskOutcome.SUCCESS

        when: "The users run the 'createOBProperties' task"
        def obResult = runTask(":createOBProperties")

        then: "The task will finalize successfully"
        obResult.task(":createOBProperties").outcome == TaskOutcome.SUCCESS

        and: "The 'Openbravo.properties' file is created in the config directory"
        def obFile = new File("${getProjectDir().absolutePath}/config/${OB_FILE}")
        assert obFile.exists()

    }
}
