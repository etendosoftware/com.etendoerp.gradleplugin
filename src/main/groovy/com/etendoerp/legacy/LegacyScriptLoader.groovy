package com.etendoerp.legacy


import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.legacy.utils.GithubUtils
import com.etendoerp.legacy.utils.NexusUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.legacy.interactive.InteractiveSetupManager
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

        project.sourceSets {
            main {
                java {
                    output.classesDirs = project.files("${project.buildDir}/classes")
                    srcDirs = [
                        'src',
                        'src-gen',
                        'srcAD',
                        'src-core/src',
                        'src-wad/src',
                        'build/javasqlc/src',
                        'build/javasqlc/srcAD',
                        'build/etendo/src',
                        'build/etendo/src-gen',
                        'build/etendo/srcAD',
                        'build/etendo/build/javasqlc/src',
                        'build/etendo/build/javasqlc/srcAD'
                    ]
                }
            }
        }
        
        // set the modules sources directories.
        if (project.file('modules').exists() && project.file('modules').isDirectory()) {
            project.file('modules').eachDir { dir ->
                def moduleSrc = project.file("${dir}/src")
                if (moduleSrc.exists()) {
                    project.sourceSets.main.java.srcDir moduleSrc
                }
                
                def moduleResources = project.file("${dir}/etendo-resources")
                if (moduleResources.exists()) {
                    if (!project.sourceSets.main.resources.srcDirs.any { it.toString().endsWith('/etendo-resources') }) {
                        project.sourceSets.main.resources.srcDir moduleResources
                    }
                }
            }
        }

        // set the modules_core sources directories.
        if (project.file('modules_core').exists() && project.file('modules_core').isDirectory()) {
            project.file('modules_core').eachDir { dir ->
                def moduleSrc = project.file("${dir}/src")
                if (moduleSrc.exists()) {
                    project.sourceSets.main.java.srcDir moduleSrc
                }
            }
        }

        project.tasks.named('compileJava') {
            destinationDir = project.file("${project.buildDir}/classes")
            
            // --- OPTIMIZACIONES DE COMPILACIÓN ---
            options.incremental = true
            options.fork = true
            options.encoding = 'UTF-8'
            
            // Aumentar memoria para el compilador
            options.forkOptions.memoryMaximumSize = '2G'
            
            // Evitar procesamientos pesados innecesarios en desarrollo
            options.compilerArgs << '-Xlint:none'
            options.compilerArgs << '-nowarn'
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
                url = 'https://repo.futit.cloud/repository/maven-public-releases'
            }
            maven {
                url = 'https://repo.futit.cloud/repository/maven-releases'
                credentials {
                    username = project.ext.get("nexusUser")
                    password = project.ext.get("nexusPassword")
                }
            }
            maven {
                url = 'https://repo.futit.cloud/repository/maven-public-jars'
            }
            maven {
                url = 'https://repo.futit.cloud/repository/etendo-public-jars'
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

        project.afterEvaluate {
            project.war {
                dependsOn 'gradleCopyWebInf', 'gradleSyncLib', 'gradlePostsrc', 'gradleCopyReferenceData', 'gradleBuildLocalContext', 'gradleWad', 'gradleCopyDesign', 'gradleCopySkins', 'gradleCopyModuleWeb'
                
                // Include everything from WebContent
                from('WebContent') {
                    include '**/*'
                }
                
                // Explicitly include src-loc just in case it's being ignored
                from('WebContent/src-loc') {
                    into 'src-loc'
                }
                
                destinationDirectory = project.file('lib')
                archiveFileName = 'etendo.war'

                def coreInSources = com.etendoerp.legacy.ant.AntLoader.isCoreInSources(project)
                def webXmlPath = coreInSources ? 'build/javasqlc/src/web.xml' : 'build/etendo/build/javasqlc/src/web.xml'
                
                // Instead of setting webXml property which is strictly validated at configuration/start of execution,
                // we include it in the war content. This avoids "file does not exist" errors.
                from(project.file(webXmlPath).parentFile) {
                    include 'web.xml'
                    into 'WEB-INF'
                }
                
                duplicatesStrategy = 'exclude'
            }
        }

        /** Alias for compatibility */
        project.task("antWar") {
            description = 'Alias for the Gradle war task (compatibility)'
            group = 'etendo-build'
            dependsOn project.tasks.findByName('war')
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
        project.task("createBackupProperties") {
            dependsOn "createOBProperties"
            def template = project.file("config/backup.properties.template")
            def target = project.file("config/backup.properties")
            
            inputs.file(template)
            outputs.file(target)

            doFirst {
                boolean force = project.findProperty(FORCE_BACKUP_PROPS) ? true : false
                if (!template.exists() || (target.exists() && !force)) {
                    project.logger.info("* Omitting the creation of the 'backup.properties' from the template. To recreate run with '-P${FORCE_BACKUP_PROPS}=true'")
                    throw new StopExecutionException()
                }
            }
            
            doLast {
                project.copy {
                    from template
                    into project.file("config")
                    rename { "backup.properties" }
                }
            }
        }

        /** Copy Openbravo.properties template */
        project.task("createOBProperties") {
            dependsOn "createQuartzProperties"
            def template = project.file("config/Openbravo.properties.template")
            def target = project.file("config/Openbravo.properties")
            
            inputs.file(template)
            outputs.file(target)

            doFirst {
                boolean force = project.findProperty(FORCE_DEFAULT_PROPS) ? true : false
                if (!template.exists() || (target.exists() && !force)) {
                    project.logger.info("* Omitting the creation of the 'Openbravo.properties' from the template. To recreate run with '-P${FORCE_DEFAULT_PROPS}=true'")
                    throw new StopExecutionException()
                }
            }
            
            doLast {
                project.copy {
                    from template
                    into project.file("config")
                    rename { "Openbravo.properties" }
                }
            }
        }

        /** Copy quartz.properties template */
        project.task("createQuartzProperties") {
            dependsOn "createOtherConfigProperties"
            def template = project.file("config/quartz.properties.template")
            def target = project.file("config/quartz.properties")
            
            inputs.file(template)
            outputs.file(target)

            doFirst {
                boolean force = project.findProperty(FORCE_QUARTZ_PROPS) ? true : false
                if (target.exists() || (!template.exists() && !force)) {
                    project.logger.info("* Omitting the creation of the 'quartz.properties' from the template. To recreate run with '-P${FORCE_QUARTZ_PROPS}=true'")
                    throw new StopExecutionException()
                }
            }
            
            doLast {
                project.copy {
                    from template
                    into project.file("config")
                    rename { "quartz.properties" }
                }
            }
        }

        /** Copy other config templates */
        project.task("createOtherConfigProperties") {
            description = "Copies .template files to their non-template version in the same directory"
            group = "etendo-config"
            
            def configDir = project.file("config")
            def templates = project.fileTree(configDir) {
                include "*.template"
                exclude "backup.properties.template"
                exclude "Openbravo.properties.template"
                exclude "quartz.properties.template"
            }
            
            inputs.files(templates)
            
            // Declare specific output files instead of the whole directory
            templates.each { templateFile ->
                def targetName = templateFile.name.replace(".template", "")
                outputs.file(new File(configDir, targetName))
            }

            doLast {
                templates.each { templateFile ->
                    def targetName = templateFile.name.replace(".template", "")
                    def targetFile = new File(configDir, targetName)
                    if (!targetFile.exists()) {
                        project.copy {
                            from templateFile
                            into configDir
                            rename { targetName }
                        }
                        project.logger.lifecycle("Created ${targetName} from template")
                    }
                }
            }
        }

        /** Copy Openbravo.properties template and set values */
        project.task("prepareConfig") {
            dependsOn project.tasks.findByName("createOBProperties")
            dependsOn project.tasks.findByName("createBackupProperties")
            dependsOn project.tasks.findByName("createQuartzProperties")
            dependsOn project.tasks.findByName("createOtherConfigProperties")

            inputs.file(project.file("gradle.properties"))
            outputs.file(project.file("config/Openbravo.properties"))

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

        // =============================================================
        // Interactive Setup Integration
        // =============================================================
        
        /**
         * Interactive setup task that guides users through property configuration.
         * This task is only created when the 'interactive' property is present.
         * 
         * Usage:
         *   ./gradlew interactiveSetup -Pinteractive=true --console=plain
         * 
         * Note: The --console=plain flag is recommended to suppress progress bars
         * that appear during buildSrc compilation before the interactive session starts.
         */
        if (project.hasProperty('interactive')) {
            project.task("interactiveSetup") {
                description = "Interactive wizard for configuring Etendo project properties"
                group = "etendo setup"
                
                // Disable progress reporting to avoid progress bar during user interaction
                outputs.upToDateWhen { false }
                // Mark as not cacheable since it requires user input
                outputs.cacheIf { false }
                // Disable build cache to avoid any progress-related interference
                outputs.doNotCacheIf("Interactive task with user input") { true }
                
                doFirst {
                    // Disable Gradle's progress output during user interaction
                    project.gradle.startParameter.consoleOutput = org.gradle.api.logging.configuration.ConsoleOutput.Plain
                    // Set logging level to suppress progress information
                    project.gradle.startParameter.logLevel = org.gradle.api.logging.LogLevel.LIFECYCLE
                    // Additional progress suppression
                    System.setProperty("org.gradle.internal.progress.disable", "true")
                    
                    // Clear the console and prepare for clean interactive output
                    print("\n\n")
                    project.logger.quiet("="*60)
                    project.logger.quiet("🔧 ETENDO INTERACTIVE SETUP")
                    project.logger.quiet("="*60)
                    
                    // Try to force quiet execution by modifying the Gradle execution environment
                    try {
                        // Access the current build's execution controller if available
                        def buildExecuter = project.gradle.services.find { service ->
                            service.class.simpleName.contains('BuildExecuter') ||
                            service.class.simpleName.contains('Executer')
                        }
                        
                        if (buildExecuter?.hasProperty('progressReporting')) {
                            buildExecuter.progressReporting = false
                        }
                    } catch (Exception e) {
                        // Internal API access - ignore failures
                    }
                }
                
                doLast {
                    try {
                        def setupManager = new InteractiveSetupManager(project)
                        
                        // Validate environment before starting
                        setupManager.validateEnvironment()
                        
                        // Execute interactive setup process
                        setupManager.execute()
                        
                        // Show completion message
                        project.logger.quiet("\n" + "="*60)
                        project.logger.quiet("✅ Interactive setup completed successfully!")
                        project.logger.quiet("="*60)
                        
                    } catch (StopExecutionException e) {
                        // User cancelled - this is expected, just re-throw to stop execution
                        project.logger.quiet("\n" + "="*60)
                        project.logger.quiet("❌ Setup cancelled by user")
                        project.logger.quiet("="*60)
                        throw e
                    } catch (Exception e) {
                        project.logger.error("Interactive setup failed: ${e.message}")
                        throw new RuntimeException("Interactive setup process failed", e)
                    }
                }
            }
            
            // Make prepareConfig depend on interactiveSetup when in interactive mode
            project.tasks.findByName("prepareConfig").dependsOn "interactiveSetup"
            
            project.logger.info("Interactive setup mode enabled. Use 'interactiveSetup' task for guided configuration.")
        }

        // =============================================================
        // End Interactive Setup Integration  
        // =============================================================

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
                    version = "0.1"
                }
            }
            repositories {
                maven {
                    url = ""
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
