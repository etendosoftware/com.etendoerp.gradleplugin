package com.etendoerp.gradle.tests.database

import com.etendoerp.gradle.tests.EtendoSpecification
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to check for open connections to postgres after executing gradle tasks.")
@Narrative("""
TODO: This test is currently failing because of some active queries after task execution, 
but we need to asses if this actually causes a problem or if the connection check should be improved.
""")
class OpenConnectionsTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "executing a task does not leave postgres open connections"() {
        given: "a certain number of connections accesing the database"
        List<GroovyRowResult> queryResult = null
        String countQueries = "select count(pid) as queryCount from pg_stat_activity where datname = '${System.getProperty('test.bbdd.sid')}'"
        Sql.withInstance(getDBConnection()) {
            Sql sql ->  queryResult = sql.rows(countQueries)
        }
        def queryCount = queryResult?.queryCount

        when: "executing a failing gradle task, that uses the database"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)
        def expandResult = getOutcome("expandCore")
        def setupResult = getOutcome("setup")

        BuildResult installOutcome
        if (expectedResult == TaskOutcome.FAILED) {
            // were expecting to fail, so make it fail
            def adColumnFile = new File(testProjectDir, "src-db/database/sourcedata/AD_COLUMN.xml")
            if (adColumnFile.exists()) {
                adColumnFile.delete()
            }
            installOutcome = runTaskAndFail("install")
        } else {
            installOutcome = runTask("install")
        }

        then: "the same number of connections exists"
        expandResult == TaskOutcome.SUCCESS
        if (expectedResult == TaskOutcome.UP_TO_DATE) {
            installOutcome.task(executedTask).outcome == expectedResult || TaskOutcome.SUCCESS
        } else {
            installOutcome.task(executedTask).outcome == expectedResult
        }

        setupResult == TaskOutcome.UP_TO_DATE

        Sql.withInstance(getDBConnection()) {
            Sql sql ->  queryResult = sql.rows(countQueries)
        }
        queryCount == queryResult?.queryCount

        where:
        executedTask                || expectedResult
        ":install"                  || TaskOutcome.UP_TO_DATE
        ":create.database"          || TaskOutcome.FAILED
    }
}
