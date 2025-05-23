package com.etendoerp.gradle.jars.core.coreinjars

import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("Running the export config script with a new template created.")
@Narrative(""" Having a new template created and
running the 'export.config.script' task creates the new template dir in the 'root/modules'""")
class JarCoreExportConfigScriptTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    public final static String PRE_EXPAND_MODULE_GROUP = "com.test"
    public final static String PRE_EXPAND_MODULE_NAME  = "premoduletoexpand"

    public final static String PRE_EXPAND_TEMPLATE_GROUP = "com.test"
    public final static String PRE_EXPAND_TEMPLATE_NAME  = "pretemplatetoexpand"

    @Issue("EPL-13")
    def "Running export config script  with a new template created"() {
        given: "A Etendo environment with the Core dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", ignoreDisplayMenu : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users adds a sources MODULE dependency before running the install"
        def preExpandModGroup = PRE_EXPAND_MODULE_GROUP
        def preExpandModName = PRE_EXPAND_MODULE_NAME
        def repoEtendoTest = TEST_REPO
        buildFile << """
        dependencies {
          moduleDeps('${preExpandModGroup}:${preExpandModName}:[1.0.0,)@zip') { transitive = true }
        }

        repositories {
          maven {
            url '${repoEtendoTest}'
          }
        }
        """

        and: "The users runs the expandCustomModule task passing by command line the  pre MODULE to expand"
        def preExpandTask = runTask(":expandCustomModule","-Ppkg=${preExpandModGroup}.${preExpandModName}","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        preExpandTask.task(":expandCustomModule").outcome == TaskOutcome.SUCCESS

        and: "The users adds a sources TEMPLATE dependency before running the install"
        def preExpandTempGroup = PRE_EXPAND_TEMPLATE_GROUP
        def preExpandTempName = PRE_EXPAND_TEMPLATE_NAME
        buildFile << """
        dependencies {
          moduleDeps('${preExpandTempGroup}:${preExpandTempName}:[1.0.0,)@zip') { transitive = true }
        }
        """

        and: "The users runs the expandCustomModule task passing by command line the  pre module to expand"
        def preExpTemplateTask = runTask(":expandCustomModule","-Ppkg=${preExpandTempGroup}.${preExpandTempName}","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        preExpTemplateTask.task(":expandCustomModule").outcome == TaskOutcome.SUCCESS

        and: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The users sets in development the pre installed module and template"
        assert CoreUtils.updateModule("${preExpandModGroup}.${preExpandModName}", [isindevelopment:"Y"], getDBConnection())
        assert CoreUtils.updateModule("${preExpandTempGroup}.${preExpandTempName}", [isindevelopment:"Y"], getDBConnection())

        and: "The users makes some changes in the Application dictionary"
        def nameChange = "new sales order custom name"
        assert updateADColumn(["name": nameChange], getDBConnection())

        when: "The users runs the export config script task"
        def exportScriptResult = runTask(":export.config.script")

        then: "The task will finish successfully"
        exportScriptResult.task(":export.config.script").outcome == TaskOutcome.SUCCESS

        and: "The changes will be exported to the pre expanded template"
        File preExpandedTemplate = new File("${testProjectDir.getAbsolutePath()}/modules/${preExpandTempGroup}.${preExpandTempName}")
        File configScript = new File(preExpandedTemplate, "src-db/database/configScript.xml")
        assert configScript.exists()
        assert configScript.text.contains("<newValue><![CDATA[${nameChange}]]></newValue>")

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

    static Boolean updateADColumn(Map valuesMap, Object dbConnection) {
        def values = CoreUtils.generateUpdateQueryValues(valuesMap)

        // Update Sales order 'C_Order_ID' column name
        def qry = "update ad_column set ${values} where ad_column_id='2161'"

        def qryResult = CoreUtils.executeQueryUpdate(qry, dbConnection)

        if (qryResult == 0) {
            return false
        }
        return true
    }

}
