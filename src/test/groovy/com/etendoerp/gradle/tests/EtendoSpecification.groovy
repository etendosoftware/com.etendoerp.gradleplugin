package com.etendoerp.gradle.tests

import com.etendoerp.gradle.utils.DBCleanupMode
import com.etendoerp.publication.PublicationUtils
import groovy.sql.Sql
import org.gradle.internal.impldep.com.google.common.io.Files
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
/**
 * Base specification for an Etendo Gradle project.
 * Configures everything necessary to run gradle tasks properly in tests.
 * This specification also provides utility methods to run gradle tasks
 */
abstract class EtendoSpecification extends Specification implements EtendoSpecificationTrait {
    private static final String TEST_RESOURCES_DIR = 'src/test/resources'

    File buildFile
    Map<String, String> args

    public static String BASE_MODULE = PublicationUtils.BASE_MODULE_DIR
    public static String REPO = PublicationUtils.REPOSITORY_NAME_PROP
    public static String PKG  = PublicationUtils.MODULE_NAME_PROP

    /**
     * Override this method to return the directory where the gradle project will be created, to run the tests
     * It is recommended to use the @TempDir annotation. See the Spock documentation.
     * @return a file pointing to the project directory
     */
    abstract File getProjectDir()

    static def getDBConnection() {
        return [
                url: System.getProperty('test.bbdd.url') + "/",
                user: System.getProperty('test.bbdd.systemUser'),
                password: System.getProperty('test.bbdd.systemPassword'),
                driver: 'org.postgresql.Driver']
    }
    /**
     * Setups the project required files and arguments to build the project and run tasks
     * This method runs for every test inside an specification
     * @return
     */
    def setup() {
        def projectDir = getProjectDir()
        def baseDir = new File(TEST_RESOURCES_DIR)
        def baseAntBuildFile = new File(baseDir, 'build.xml')
        def baseFile = new File(baseDir, 'build-base-for-tests.gradle')
        def baseSettingsFile = new File(baseDir, 'settings-base-for-tests.gradle')

        def antBuildFile = new File(projectDir, 'build.xml')
        buildFile = new File(projectDir, 'build.gradle')
        def settingsFile = new File(projectDir, 'settings.gradle')

        // Do not replace file when not needed
        // This is used by projects that require these files to not be reset between tests
        if (!buildFile.exists()) {
            Files.copy(baseFile, buildFile)
        }
        if (!antBuildFile.exists()) {
            Files.copy(baseAntBuildFile, antBuildFile)
        }
        if (!settingsFile.exists()) {
            Files.copy(baseSettingsFile, settingsFile)
        }

        def gradleProperties = new File(projectDir, "gradle.properties")
        gradleProperties << """
        bbdd.url=${System.getProperty('test.bbdd.url')}
        bbdd.sid=${System.getProperty('test.bbdd.sid')}
        bbdd.systemUser=${System.getProperty('test.bbdd.systemUser')}
        bbdd.systemPassword=${System.getProperty('test.bbdd.systemPassword')}
        bbdd.user=${System.getProperty('test.bbdd.user')}
        bbdd.password=${System.getProperty('test.bbdd.password')}
        """

        def pluginPath = "com.etendoerp.gradleplugin"
        args = new HashMap<>()
        args.put("pluginPath", pluginPath)
        args.put("nexusUser", System.getProperty("nexusUser"))
        args.put("nexusPassword", System.getProperty("nexusPassword"))
    }

    /**
     * Default cleanup after each test is done.
     * When the cleanDatabase() method returns ALWAYS, this will delete the created database.
     */
    def cleanup() {
        if (cleanDatabase() == DBCleanupMode.ALWAYS) {
            dropDatabase()
        }
    }

    /**
     * Default cleanup after all tests are done.
     * When the cleanDatabase() method returns ONCE, this will delete the created database.
     */
    def cleanupSpec() {
        if (cleanDatabase() == DBCleanupMode.ONCE) {
            dropDatabase()
        }
    }

    /**
     * Kill all sessions and drop the database defined in the test.bbdd.sid system property
     */
    static def dropDatabase() {
        Map dbConnParams = getDBConnection()
        String killSessionsQuery = "select pg_terminate_backend(pid) from pg_stat_activity where datname = '${System.getProperty('test.bbdd.sid')}'"
        String dropQuery = "DROP DATABASE IF EXISTS ${System.getProperty('test.bbdd.sid')}"

        Sql.withInstance(dbConnParams) {
            Sql sql -> sql.execute killSessionsQuery
        }

        Sql.withInstance(dbConnParams) {
            Sql sql -> sql.execute dropQuery
        }
    }

    /**
     * Executes a Gradle task and returns its outcome.
     * Useful to expect a success or failure without checking anything else
     * @param taskName - the name of the gradle task to run
     * @return the outcome of the task after execution. See: {@link org.gradle.testkit.runner.TaskOutcome}
     */
    def getOutcome(String taskName) {
        def task = runTask(taskName)
        return task.task(":${taskName}").outcome
    }

    /**
     * Executes a Gradle task, expecting it to fail
     * Default parameters passed are "--stacktrace" and "--info", and the etendo.gradle path
     * @param task - the name of the gradle task to run
     * @return the task build result: {@link org.gradle.testkit.runner.BuildResult}
     */
    def runTaskAndFail(String task) {
        return runTaskAndFail(task, "-PpluginPath=${args.get('pluginPath')}")
    }

    /**
     * Executes a Gradle task, expecting it to fail
     * Default parameters passed are "--stacktrace" and "--info", and the etendo.gradle path
     * @param task - the name of the gradle task to run
     * @param args - extra arguments to be passed to gradle.
     * @return the task build result: {@link org.gradle.testkit.runner.BuildResult}
     */
    def runTaskAndFail(String task, String... args) {
        def allArgs = [task]
        allArgs.addAll(args.toList())
        allArgs.add("--stacktrace")
        allArgs.add("--info")
        // Results in execution like:
        // gradle task1 arg1 arg2 -PpluginPath=/path/to/etendo.gradle --stacktrace --info

        return GradleRunner
                .create()
                .forwardOutput()
                .withProjectDir(getProjectDir())
                .withArguments(allArgs)
                .withPluginClasspath()
                .buildAndFail()
    }

    /**
     * Executes a Gradle task, expecting it to complete without failure.
     * Default parameters passed are "--stacktrace" and "--info", and the etendo.gradle path
     * @param task - the name of the gradle task to run
     * @return the task build result: {@link org.gradle.testkit.runner.BuildResult}
     */
    def runTask(String task) {
        return runTask(task, "-PpluginPath=${args.get('pluginPath')}")
    }

    /**
     * Executes a Gradle task, expecting it to complete without failure.
     * Default parameters passed are "--stacktrace" and "--info", and the etendo.gradle path
     * @param task - the name of the gradle task to run
     * @param args - extra arguments to be passed to gradle.
     * @return the task build result: {@link org.gradle.testkit.runner.BuildResult}
     */
    def runTask(String task, String... args) {
        def allArgs = [task]
        allArgs.addAll(args.toList())
        allArgs.add("--stacktrace")
        allArgs.add("--info")
        // Results in execution like:
        // gradle task1 arg1 arg2 -PpluginPath=/path/to/etendo.gradle --stacktrace --info

        return GradleRunner
                .create()
                .forwardOutput()
                .withProjectDir(getProjectDir())
                .withArguments(allArgs)
                .withPluginClasspath()
                .build()
    }


}
