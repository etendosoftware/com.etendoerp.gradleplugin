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

@Title("Running the export database with a new module created and other having changes.")
@Narrative(""" Having a new module created and another with some changes,
running the 'export.database' task creates the new module dir in the 'root/modules' and export
the new changes for the others modules""")
class JarCoreExportDatabaseTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    public final static String PRE_EXPAND_MODULE_GROUP = "com.test"
    public final static String PRE_EXPAND_MODULE_NAME  = "premoduletoexpand"

    @Issue("EPL-13")
    def "Running export database with a new module created and another with some changes"() {
        given: "A Etendo environment with the Core dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", ignoreDisplayMenu : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users adds a sources module dependency before running the install"
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

        and: "The users runs the expandCustomModule task passing by command line the  pre module to expand"
        def preExpandTask = runTask(":expandCustomModule","-Ppkg=${preExpandModGroup}.${preExpandModName}","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        preExpandTask.task(":expandCustomModule").outcome == TaskOutcome.SUCCESS

        and: "The module will be expanded in the 'modules' dir "
        def preExpandModLocation = new File("${testProjectDir.getAbsolutePath()}/modules/${preExpandModGroup}.${preExpandModName}")
        assert preExpandModLocation.exists()

        when: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The users creates a new Module"
        String javapackage = "com.test.modulecustomtoexport"
        assert createModule(javapackage, getDBConnection())

        and: "The users modifies the installed expanded module"
        def author = "custom author"
        assert CoreUtils.updateModule("${preExpandModGroup}.${preExpandModName}", ["author": author, isindevelopment:"Y"], getDBConnection())

        when: "The users runs the export database task"
        def updateResult = runTask(":export.database")

        then: "The environment will be exported correctly"
        updateResult.task(":export.database").outcome == TaskOutcome.SUCCESS

        and: "The new created module will be exported"
        File moduleLocation = new File("${testProjectDir}/modules/${javapackage}/src-db")
        assert moduleLocation.exists()

        and: "The changes in the pre expanded module will be exported"
        File preADModuleFile = new File(preExpandModLocation, "src-db/database/sourcedata/AD_MODULE.xml")
        assert preADModuleFile.text.contains("<AUTHOR><![CDATA[${author}]]></AUTHOR>")

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

    static Boolean createModule(String javapackage, Object dbConnection) {
        String qry = """Insert into AD_MODULE (
          AD_MODULE_ID,AD_CLIENT_ID,AD_ORG_ID,ISACTIVE,CREATED,CREATEDBY,UPDATED,UPDATEDBY,
          NAME,VERSION,DESCRIPTION,
          HELP,
          URL,TYPE,LICENSE,
          ISINDEVELOPMENT,ISDEFAULT,SEQNO,JAVAPACKAGE,
          LICENSETYPE,AUTHOR,STATUS,UPDATE_AVAILABLE,ISTRANSLATIONREQUIRED,AD_LANGUAGE,HASCHARTOFACCOUNTS,
          ISTRANSLATIONMODULE,HASREFERENCEDATA,ISREGISTERED,UPDATEINFO,UPDATE_VER_ID)
        values (
          to_char(get_uuid()),'0','0','Y',now(),'0',now(),'0',
          'custom name','1.0.0', 'Description','Help',
          null,'M','License',
          'Y','N',null,'${javapackage}',
          'OtherOS',null,null,null,'N','en_US','N',
          'N','N',null,null,null);"""

        def result = CoreUtils.executeQueryInserts(qry, dbConnection)
        // 18 is the javapackage index
        if (result) {
            return result.get(0).get(18).toString() == javapackage
        }
        return  false
    }

}
