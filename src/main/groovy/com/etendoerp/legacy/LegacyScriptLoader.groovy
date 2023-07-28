package com.etendoerp.legacy


import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.legacy.utils.GithubUtils
import com.etendoerp.legacy.utils.NexusUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.Sync
import org.gradle.api.publish.maven.MavenPublication

/**
 *  This class will load all the task from the legacy script
 *  following the same order.
 */
class LegacyScriptLoader {

    static final String FORCE_DEFAULT_PROPS = "forceDefaultProps"
    static final String FORCE_BACKUP_PROPS = "forceBackupProps"
    static final String FORCE_QUARTZ_PROPS = "forceQuartzProps"


    static List whiteSyncCoreList = [
            'legal/**',
            'lib/**',
            'modules_core/**',
            'referencedata/**',
            'src/**',
            'src-db/**',
            'src-test/**',
            'src-core/**',
            'src-jmh/**',
            'src-trl/**',
            'src-util/**',
            'src-wad/**',
            'web/**',
            '*.template',
            'config/*.template',
            'gradlew',
            'gradle.bat',
            'build.xml'
    ]

    static load(Project project) {

        def defaultModules = [
                'com.smf.smartclient.debugtools',
                'com.smf.smartclient.boostedui',
                'com.smf.securewebservices',
                'org.openbravo.base.weld',
                'org.openbravo.service.integration.google',
                'org.openbravo.client.kernel',
                'org.openbravo.client.htmlwidget',
                'org.openbravo.client.querylist',
                'org.openbravo.client.widgets',
                'org.openbravo.service.datasource',
                'org.openbravo.service.integration.openid',
                'org.openbravo.client.myob',
                'org.openbravo.client.application',
                'org.openbravo.userinterface.selector',
                'org.openbravo.v3.framework',
                'org.openbravo.v3.datasets',
                'org.openbravo.apachejdbcconnectionpool',
                'org.openbravo.utility.cleanup.log',
                'org.openbravo.userinterface.skin.250to300Comp',
                'org.openbravo.userinterface.smartclient',
                'org.openbravo.service.json',
                'org.openbravo',
                'org.openbravo.financial.paymentreport',
                'org.openbravo.reports.ordersawaitingdelivery',
                'org.openbravo.advpaymentmngt',
                'org.openbravo.v3'
        ]

        project.ext {
            askNexusCredentials = this.&askNexusCredentials
        }

        project.sourceSets{
            main {
                java {
                    outputDir = project.file("${project.buildDir}/classes/")
                    srcDirs = ['build/javasqlc/src'] //clean the default sources directories.
                    srcDirs 'build/javasqlc/srcAD'
                    srcDirs 'src'
                    srcDirs 'src-gen'
                    srcDirs 'srcAD'

                    // The core is in JARs
                    srcDirs 'build/etendo/build/javasqlc/src'
                    srcDirs 'build/etendo/build/javasqlc/srcAD'
                    srcDirs 'build/etendo/src-gen'
                    srcDirs 'build/etendo/srcAD'
                }
            }
        }
        //set the modules sources directories.
        if (project.file('modules').exists() && project.file('modules').isDirectory()) {
            project.file('modules').eachDir {
                project.sourceSets.main.java.srcDirs += it.toString() + "/src"
                if (project.file(it.toString() + '/etendo-resources').exists()) {
                    def resourcesDir = project.sourceSets.main.resources.srcDirs.find { it.toString().endsWith('/etendo-resources') }
                    if (resourcesDir == null) {
                        project.sourceSets.main.resources.srcDirs += it.toString() + "/etendo-resources"
                    }
                }
            }
        }

        //set the modules_core sources directories.
        if(project.file('modules_core').exists() && project.file('modules_core').isDirectory()){
            project.file('modules_core').eachDir {
                project.sourceSets.main.java.srcDirs += it.toString()+"/src"
            }
        }

        project.configurations {
            coreDep
            moduleDeps
            dockerDeps
            moduleDepsLegacy
        }

        // TODO: Check if this works
        def dependenciesFile = project.file("./dependencies.gradle")
        if (dependenciesFile.exists()) {
             project.apply from: dependenciesFile
        }

        /**
         * REPOSITORIES CONFIGURATIONS
         */
        project.repositories {
            mavenCentral()
            maven {
                url = URI.create("https://maven.pkg.github.com/etendosoftware/etendo_core")
                credentials {
                    username = project.ext.get("githubUser")
                    password = project.ext.get("githubToken")
                }
            }
            maven {
                url 'https://repo.futit.cloud/repository/maven-public-releases'
            }
            maven {
                url 'https://repo.futit.cloud/repository/maven-releases'
                credentials {
                    username = project.ext.get("nexusUser")
                    password = project.ext.get("nexusPassword")
                }
            }
            maven {
                url 'https://repo.futit.cloud/repository/maven-public-jars'
            }
            maven {
                url 'https://repo.futit.cloud/repository/etendo-public-jars'
            }
        }

        /**
         * DEPENDENCIES
         */
        project.dependencies {
            project.dependencies {
                moduleDepsLegacy ('com.smf:smartclient.debugtools:[1.0.1,)@zip') {
                    transitive = true
                }
                moduleDepsLegacy ('com.smf:smartclient.boostedui:[1.0.0,)@zip') {
                    transitive = true
                }
                moduleDepsLegacy ('com.smf:securewebservices:[1.1.1,)@zip') {
                    transitive = true
                }
            }
        }

        /**
         * BUILD TASKS
         */

        /** war coinfiguration */
        project.war {
            from 'WebContent'
        }

        /** Unpacks the Core dependency to a temporary folder. Used by expandCoreLegacy **/
        project.task("unpackCoreToTemp", type: Copy) {
            def extractDir =  getTemporaryDir()
            project.afterEvaluate {
                def etendo = project.getExtensions().getByName("etendo")
                project.dependencies.add("coreDep", 'com.etendoerp.platform:etendo-core:' + etendo.coreVersion + '@zip')
                if (extractDir.exists()) {
                    project.delete(extractDir.getPath())
                }
            }
            from {
                GithubUtils.askCredentials(project)
                project.configurations.coreDep.collect {
                    project.zipTree(it).getAsFileTree()
                }
            }
            into extractDir
        }

        /** Unpacks module dependencies to a temporary folder. Used by expandModules **/
        project.task("unpackModulesToTemp", type: Copy) {
            def extractDir = getTemporaryDir()
            dependsOn project.configurations.moduleDepsLegacy
            from {
                NexusUtils.askNexusCredentials(project)
                project.configurations.moduleDepsLegacy.findResults {
                    // Avoid extracting the core dependency inside the modules folder
                    it.getCanonicalPath().contains('com.smf.classic.core') ? null : project.zipTree(it)
                }
            }
            into extractDir
        }

        project.task("cleanTempModules", type: Delete) {
            delete project.tasks.findByName("unpackModulesToTemp").getTemporaryDir()
        }


        /** Expand the Core dependency */
        project.task("expandCoreLegacy", type: Sync) {
            dependsOn project.tasks.findByName("unpackCoreToTemp")
            from project.tasks.findByName("unpackCoreToTemp").getTemporaryDir()
            into "${project.projectDir}"
            // Preserve files that are allowed to be modified by the user, and those not included in the Core zip
            preserve {
                include '**'
                exclude(whiteSyncCoreList)
            }
        }


        /** Expand modules from the dependencies */
        project.task("expandModulesLegacy", type: Sync) {
            dependsOn project.tasks.findByName("unpackModulesToTemp")
            File modulesDir = new File("${project.projectDir}/modules")
            File newModulesDir = project.tasks.findByName("unpackModulesToTemp").getTemporaryDir()
            def localDependencies = modulesDir.exists() ? modulesDir.list().toList() : []
            def modules = []
            project.configurations.moduleDepsLegacy.allDependencies.each {
                modules.add "${it.getGroup()}.${it.getName()}"
            }

            from newModulesDir
            into "${project.projectDir}/modules"

            // Preserve modules that are local dependencies (those installed manually, not declared in build.gradle)
            preserve {
                localDependencies.each {
                    include "${it}/**"
                }
                exclude modules
            }

            finalizedBy project.tasks.findByName("cleanTempModules")
        }

        /** Copy backup.properties template */
        project.task("createBackupProperties", type: Copy) {
            outputs.upToDateWhen { return false }
            from project.file("config/backup.properties.template")
            into project.file("config")
            rename { String fileName ->
                fileName.replace("backup.properties.template", "backup.properties")
            }
            doFirst {
                boolean force = project.findProperty(FORCE_BACKUP_PROPS) ? true : false
                if (!project.file("config/backup.properties.template").exists() || (project.file("config/backup.properties").exists() && !force)) {
                    project.logger.info("* Omitting the creation of the 'backup.properties' from the template. To recreate run with '-P${FORCE_BACKUP_PROPS}=true'")
                    throw new StopExecutionException()
                }
            }
        }

        /** Copy Openbravo.properties template */
        project.task("createOBProperties", type: Copy) {
            outputs.upToDateWhen { return false }
            from project.file("config/Openbravo.properties.template")
            into project.file("config")
            rename { String fileName ->
                fileName.replace("Openbravo.properties.template", "Openbravo.properties")
            }
            doFirst {
                boolean force = project.findProperty(FORCE_DEFAULT_PROPS) ? true : false
                if (!project.file("config/Openbravo.properties.template").exists() || (project.file("config/Openbravo.properties").exists() && !force)) {
                    project.logger.info("* Omitting the creation of the 'Openbravo.properties' from the template. To recreate run with '-P${FORCE_DEFAULT_PROPS}=true'")
                    throw new StopExecutionException()
                }
            }
        }

        /** Copy quartz.properties template */
        project.task("createQuartzProperties", type: Copy) {
            outputs.upToDateWhen { return false }
            from project.file("config/quartz.properties.template")
            into project.file("config")
            rename { String fileName ->
                fileName.replace("quartz.properties.template", "quartz.properties")
            }
            doFirst {
                boolean force = project.findProperty(FORCE_QUARTZ_PROPS) ? true : false
                if (project.file("config/quartz.properties").exists() || (!project.file("config/quartz.properties.template").exists() && !force)) {
                    project.logger.info("* Omitting the creation of the 'quartz.properties' from the template. To recreate run with '-P${FORCE_QUARTZ_PROPS}=true'")
                    throw new StopExecutionException()
                }
            }
        }

        /** Copy Openbravo.properties template and set values */
        project.task("prepareConfig") {
            dependsOn project.tasks.findByName("createOBProperties")
            dependsOn project.tasks.findByName("createBackupProperties")
            dependsOn project.tasks.findByName("createQuartzProperties")

            doLast {
                def props = new Properties()
                project.file("gradle.properties").withInputStream { props.load(it) }
                ant.propertyfile(file: "config/Openbravo.properties") {

                    // Find all properties in gradle.properties and set their value in Openbravo.properties
                    for (Map.Entry<Object, Object> prop : props) {
                        String key = (String) prop.key
                        if ("bbdd.port" == key || key.startsWith("org.gradle") || key == "nexusUser" || key == "nexusPassword") {
                            // Skip helper and gradle props
                            continue
                        }
                        entry(key: prop.key, value: prop.value)
                    }

                    // If some properties do not exists, fill them with their default values
                    if (!props.getProperty("context.name")) {
                        entry(key: "context.name", value: "etendo")
                    }

                    if (!props.getProperty("bbdd.sid")) {
                        entry(key: "bbdd.sid", value: "etendo")
                    }

                    if (!props.getProperty("bbdd.url")) {
                        def host = props.getProperty("bbdd.host", "localhost")
                        def port = props.getProperty("bbdd.port", "5432")

                        def database = props.getProperty("bbdd.rdbms")
                        def urlValue
                        if (database && database == "ORACLE") {
                            def sid = props.getProperty("bbdd.sid", "etendo")
                            port = props.getProperty("bbdd.port", "1521")
                            urlValue = "jdbc:oracle:thin:@" + host + ":" + port + ":" + sid
                        } else {
                            urlValue = "jdbc:postgresql://" + host + ":" + port
                        }

                        entry(key: "bbdd.url", value: urlValue)
                    }

                    if (!props.getProperty("bbdd.systemUser")) {
                        entry(key: "bbdd.systemUser", value: "postgres")
                    }

                    if (!props.getProperty("bbdd.systemPassword")) {
                        entry(key: "bbdd.systemPassword", value: "syspass")
                    }

                    if (!props.getProperty("bbdd.user")) {
                        entry(key: "bbdd.user", value: "tad")
                    }

                    if (!props.getProperty("bbdd.password")) {
                        entry(key: "bbdd.password", value: "tad")
                    }

                    if (!props.getProperty("allow.root")) {
                        entry(key: "allow.root", value: "false")
                    }

                    if (!props.getProperty("source.path")) {
                        entry(key: "source.path", value: project.projectDir.getAbsolutePath())
                    }

                    if (!props.getProperty("attach.path")) {
                        entry(key: "attach.path", value: project.projectDir.getAbsolutePath() + "/attachments")
                    }
                }
            }

        }

        /** Expand core and modules from the dependencies */
        project.task("expandLegacy") {
            dependsOn project.tasks.findByName("expandCoreLegacy")
            dependsOn project.tasks.findByName("expandModulesLegacy")
            project.tasks.findByName('expandModulesLegacy').mustRunAfter 'expandCoreLegacy'
        }

        /** Expand core and modules from the dependencies */
        project.task("expand") {
            dependsOn project.tasks.findByName("expandCore")
            dependsOn project.tasks.findByName("expandModules")
            project.tasks.findByName('expandModules').mustRunAfter 'expandCore'
        }

        /**
         * Basic configuration of repositories used later on publish tasks.
         * Publication name "mavenModule", has direct correlation with object "publishMavenModulePublicationToMavenRepository",
         * object name is composed by: "publish" + [publication name] + "PublicationToMavenRepository", which is needed to
         * customize before publish new version
         */
        // TODO: Check if this works correctly
        // org.gradle.api.publish.internal.DefaultPublishingExtension publishing(groovy.lang.Closure configuration)
        // org.gradle.api.publish.maven.plugins.MavenPublishPlugin
        project.publishing {
            publications {
                mavenModule(MavenPublication) {
                    version "0.1"
                }
            }
            repositories {
                maven {
                    url ""
                }
            }
        }

        /**
         * This task changes all local dependencies by external dependencies deployed in Nexus.
         * Exclude modules declared in coreModules array.
         */
        project.task("normalize"){
            doLast {
                NexusUtils.askNexusCredentials(project)
                def files = project.file( "./modules").listFiles().sort()
                files.each { File file ->
                    if (file.isDirectory() && !defaultModules.contains(file.getName())) {
                        ModulesUtils.normalizeModule(project, file, project.ext.get("nexusUser"), project.ext.get("nexusPassword"))
                    }
                }
                print "*******************************************************************************\n"
                print "* IMPORTANT, this task only declares the references of modules dependencies   *\n"
                print "* if you want to download the dependencies you must run the following command *\n"
                print "* RUN COMMAND>> ./gradlew expandModules                                       *\n"
                print "*******************************************************************************\n"
            }
        }


        /* Task to normalize a specific module
         * This task require command line parameter -Ppkg=<package name>
         * */
        project.task("normalizeModule") {
            doLast {
                if (project.hasProperty("pkg")) {
                    String pkgVar = project.pkg
                    if (!defaultModules.contains(pkgVar)) {
                        NexusUtils.askNexusCredentials(project)
                        ModulesUtils.normalizeModule(project, new File("./modules/" + pkgVar), project.ext.get("nexusUser"), project.ext.get("nexusPassword"))
                    }
                }
                print "*******************************************************************************\n"
                print "* IMPORTANT, this task only declares the references of modules dependencies   *\n"
                print "* if you want to download the dependencies you must run the following command *\n"
                print "* RUN COMMAND>> ./gradlew expandModules                                       *\n"
                print "*******************************************************************************\n"
            }
        }


        //
        project.dependencies {
            project.dependencies {
                dockerDeps 'com.etendoerp:etendo-docker-install-resources:latest'
            }
        }

        project.task("unzipDockerInstallResources", type: Copy) {
            def extractDir = project.getBuildDir()
            dependsOn project.configurations.dockerDeps
            from {
                NexusUtils.askNexusCredentials(project)
                project.configurations.dockerDeps.findResults {
                    project.zipTree(it)
                }
            }
            into extractDir
        }

        project.task("testModuleInstall", type: Exec) {
            dependsOn project.tasks.findByName("unzipDockerInstallResources")
            if (new File("dependencies.gradle").exists()) {
                commandLine "docker-compose", "-f", "build/test-etendo-install/docker-compose.yml", "up", "-d", "etendo_db"
                commandLine "docker", "build", ".", "-f", "build/test-etendo-install/Dockerfile", "--build-arg", "nexusPassword=" + nexusPassword, "--build-arg", "nexusUser=" + nexusUser, "--network=host", "--rm", "--no-cache"
            }
        }

    }

}
