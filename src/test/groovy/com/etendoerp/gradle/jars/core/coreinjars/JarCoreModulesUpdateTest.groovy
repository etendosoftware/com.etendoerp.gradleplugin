package com.etendoerp.gradle.jars.core.coreinjars

import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import groovy.sql.GroovyRowResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("Running the update.database task after adding source and jar modules")
@Narrative(""" After adding a source module and a jar module and running the 'update.database' task,
the modules should be imported to the database and updated correctly.""")
class JarCoreModulesUpdateTest extends EtendoCoreResolutionSpecificationTest {
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

    public final static String SOURCE_MODULE_GROUP = "com.test"
    public final static String SOURCE_MODULE_NAME  = "moduletoexpand"

    public final static String JAR_MODULE_GROUP = "com.test"
    public final static String JAR_MODULE_NAME  = "dummymodule"

    @Issue("EPL-13")
    def "Running update database with source and jar modules" () {
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

        // Run The install task
        and: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The pre install expanded module will be installed correctly"
        assert CoreUtils.containsModule("${preExpandModGroup}.${preExpandModName}", getDBConnection())

        ///

        and: "The users adds a sources module dependency"
        def moduleSourceGroup = SOURCE_MODULE_GROUP
        def moduleSourceName = SOURCE_MODULE_NAME
        buildFile << """
        dependencies {
          moduleDeps('${moduleSourceGroup}:${moduleSourceName}:[1.0.0,)@zip') { transitive = true }
        }
        """

        and: "The users runs the expandCustomModule task passing by command line the module to expand"
        def expandCustomModuleTaskResult = runTask(":expandCustomModule","-Ppkg=${moduleSourceGroup}.${moduleSourceName}","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandCustomModuleTaskResult.task(":expandCustomModule").outcome == TaskOutcome.SUCCESS

        and: "The module will be expanded in the 'modules' dir "
        def moduleLocation = new File("${testProjectDir.getAbsolutePath()}/modules/${moduleSourceGroup}.${moduleSourceName}")
        assert moduleLocation.exists()

        and: "The users adds a jar module dependency"
        def moduleJarGroup = JAR_MODULE_GROUP
        def moduleJarName = JAR_MODULE_NAME

        buildFile << """
        dependencies {
          implementation('${moduleJarGroup}:${moduleJarName}:[1.0.0,)') { transitive = true }
        }
        """

        def dependenciesJarResult = runTask(":dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesJarResult.task(":dependencies").outcome == TaskOutcome.SUCCESS
        assert dependenciesJarResult.output.contains("${moduleJarGroup}:${moduleJarName}")

        and: "The pre expanded module has some changes"
        def preExpandedModFile = new File("${testProjectDir.getAbsolutePath()}/modules/${preExpandModGroup}.${preExpandModName}")
        def preExpandADModule = new File(preExpandedModFile, "src-db/database/sourcedata/AD_MODULE.xml")
        def modContent = preExpandADModule.text

        // Change the AD_MODULE author
        String author = "author change"
        modContent = modContent.replace("<AUTHOR><![CDATA[-]]></AUTHOR>", "<AUTHOR><![CDATA[${author}]]></AUTHOR>")
        preExpandADModule.text = modContent

        when: "The users runs the update database task"
        def updateResult = runTask(":update.database")

        then: "The environment will be updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        and: "All the defined modules should be installed in the database"
        assert CoreUtils.containsModule("${preExpandModGroup}.${preExpandModName}", getDBConnection())
        assert CoreUtils.containsModule("${moduleSourceGroup}.${moduleSourceName}", getDBConnection())
        assert CoreUtils.containsModule("${moduleJarGroup}.${moduleJarName}", getDBConnection())

        and: "The pre expanded module changes should be updated in the database"
        assert containsAuthor("${preExpandModGroup}.${preExpandModName}", author, getDBConnection())

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

    static Boolean containsAuthor(String javapackage, String author, Object dbConnection) {
        String qry = "select author from ad_module where javapackage = '${javapackage}'"
        def rowResult = CoreUtils.executeQuery(qry, dbConnection)

        if (!rowResult) {
            return false
        }

        for (GroovyRowResult row : rowResult) {
            def rowAuthor = row.author
            if (rowAuthor && rowAuthor == author) {
                return true
            }
        }
        return false
    }

}