package com.etendoerp.legacy

import com.etendoerp.jars.modules.metadata.ModuleMetadata
import com.etendoerp.legacy.utils.DependenciesUtils
import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.legacy.utils.NexusUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.publish.maven.MavenPublication

/**
 *  This class will load all the task from the legacy script
 *  following the same order.
 */
class LegacyScriptLoader {

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
            nexusUser = null
            nexusPassword = null
            askNexusCredentials = this.&askNexusCredentials
        }

        project.sourceSets{
            main{
                java{
                    outputDir = project.file("${project.buildDir}/classes/")
                    srcDirs = ['build/javasqlc/src'] //clean the default sources directories.
                    srcDirs 'build/javasqlc/srcAD'
                    srcDirs 'src'
                    srcDirs 'src-gen'
                    srcDirs 'srcAD'
                }
            }
        }
        //set the modules sources directories.
        if(project.file('modules').exists() && project.file('modules').isDirectory()){
            project.file('modules').eachDir {
                project.sourceSets.main.java.srcDirs += it.toString()+"/src"
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
        }

        // TODO: Check if this works
        def dependenciesFile = project.file("./dependencies.gradle")
        if (dependenciesFile.exists()) {
             project.apply from: dependenciesFile
        }

        /**
         * DEPENDENCIES
         */
        project.dependencies {
            project.dependencies {
                moduleDeps ('com.smf:smartclient.debugtools:[1.0.1,)@zip') {
                    transitive = true
                }
                moduleDeps ('com.smf:smartclient.boostedui:[1.0.0,)@zip') {
                    transitive = true
                }
                moduleDeps ('com.smf:securewebservices:[1.1.1,)@zip') {
                    transitive = true
                }
            }

            // hack to load jar dependencies.
            implementation project.fileTree(project.projectDir) {
                include "**/lib/**/*.jar"
                exclude "\${env.CATALINA_HOME}"
                exclude "WebContent"
            }
        }

        /**
         * REPOSITORIES CONFIGURATIONS
         */

        project.repositories {
            jcenter()
            maven {
                url "https://repo.futit.cloud/repository/maven-releases"
            }
            maven {
                url "https://repo.futit.cloud/repository/maven-public-jars"
            }
        }

        /**
         * This method gets all compile dependencies and sets them in ant as a file list with id: "gradle.libs"
         * Ant task build.local.context uses this to copy them to WebContent
         */
        project.task("dependenciesToAntForCopy") {
            project.afterEvaluate {
                def dependencies = []
                project.configurations.compile.collect {
                    dependencies.add ant.path(location: it)
                    project.logger.log(LogLevel.INFO , "Gradle library " + it.getName() + " added to gradle.libs ant filelist")
                }

                ant.filelist(id: 'gradle.libs', files: dependencies.join(','))
            }
        }


/**
 * This method gets all resolved dependencies by gradle and pass all resolved jars to ANT tasks
 */
        project.task("deps") {
            project.afterEvaluate {
                def antClassLoader = org.apache.tools.ant.Project.class.classLoader
                def newPath = []
                //
                project.configurations.compile.collect {
                    antClassLoader.addURL it.toURL()
                }
                project.configurations.compile.collect {
                    newPath.add ant.path(location: it)
                }
                //
                ant.references.keySet().forEach {
                    if(it.contains("path")) {
                        newPath.forEach { pth ->
                            logger.log(LogLevel.INFO, "ant reference " + it + " add to classpath " + pth)
                            ant.references[it].add(pth)
                        }
                    }
                }
            }
        }

        /**
         * BUILD TASKS
         */

        /***
         * Task to check  that all configuration files exists
         * */
        project.task("compileFilesCheck"){
            doLast {
                def error = false
                if (!project.file("${project.projectDir}/gradle.properties").exists()) {
                    logger.error('No such  file ${project.projectDir}/gradle.properties')
                    error = true
                }
                if (!project.file("${project.projectDir}/config/Openbravo.properties").exists()) {
                    logger.error('No such  file ${project.projectDir}/config/Openbravo.properties')
                    error = true
                }
                if (!project.file("${project.projectDir}/config/Format.xml").exists()) {
                    logger.error('No such  file ${project.projectDir}/config/Format.xml')
                    error = true
                }
                if (!project.file("${project.projectDir}/config/log4j2.xml").exists()) {
                    logger.error('No such  file ${project.projectDir}/config/log4j2.xml')
                    error = true
                }
                if (!project.file("${project.projectDir}/config/log4j2-web.xml").exists()) {
                    logger.error('No such  file ${project.projectDir}/config/log4j2-web.xml')
                    error = true
                }

                if (error) {
                    throw new GradleException("Configuration files are missing to run this task, to fix it try run ./gradlew setup, before that you can modify gradle.properties file to set new configuration values")
                }
            }
        }

        /** map from ant tasks to gradle*/
        project.ant.importBuild('build.xml') { String oldTargetName ->
            switch (oldTargetName) {
                case 'clean':
                    return 'antClean'
                case 'setup':
                    return 'antSetup'
                case 'init':
                    return 'antInit'
                case 'install.source':
                    return 'antInstall'
                case 'war':
                    return 'antWar'
                default:
                    if(oldTargetName.contains("test")) {
                        return "ant." + oldTargetName
                    }
                    return oldTargetName
            }
        }

        ['smartbuild', 'compile.complete', 'compile.complete.deploy', 'update.database', 'export.database'].each {
            def task = project.tasks.findByName(it)
            if(task != null) {
                task.dependsOn(project.tasks.findByName("compileFilesCheck"))
            }
        }

        /** war coinfiguration */
        project.war {
            from 'WebContent'
        }

        /** Unpacks the Core dependency to a temporary folder. Used by expandCore **/
        project.task("unpackCoreToTemp", type: Copy) {
            def extractDir =  getTemporaryDir()
            project.afterEvaluate {
                def etendo = project.getExtensions().getByName("etendo")
                project.dependencies.add("coreDep", 'com.smf.classic.core:ob:' + etendo.coreVersion + '@zip')
                if (extractDir.exists()) {
                    project.delete(extractDir.getPath())
                }
            }
            from {
                NexusUtils.askNexusCredentials(project)
                project.configurations.coreDep.collect {
                    project.zipTree(it).getAsFileTree()
                }
            }
            into extractDir
        }

        /** Unpacks module dependencies to a temporary folder. Used by expandModules **/
        project.task("unpackModulesToTemp", type: Copy) {
            def extractDir = getTemporaryDir()
            dependsOn project.configurations.moduleDeps
            from {
                NexusUtils.askNexusCredentials(project)
                project.configurations.moduleDeps.findResults {
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
        project.task("expandCore", type: Sync) {
            dependsOn project.tasks.findByName("unpackCoreToTemp")
            from project.tasks.findByName("unpackCoreToTemp").getTemporaryDir()
            into "${project.projectDir}"
            // Preserve files that are allowed to be modified by the user, and those not included in the Core zip
            preserve {
                include 'attachments'
                include 'gradle.properties'
                include 'modules/'
                include 'settings.gradle'
                include 'gradlew.bat'
                include 'gradlew'
                include 'build.gradle'
                include 'gradle/'
                include 'config/Openbravo.properties'
                include 'config/log4j2-web.xml'
                include 'config/log4j2.xml'
                include 'config/Format.xml'
                include 'config/redisson-config.yaml'
            }
        }


        /** Expand modules from the dependencies */
        project.task("expandModules", type: Sync) {
            dependsOn project.tasks.findByName("unpackModulesToTemp")
            File modulesDir = new File("${project.projectDir}/modules")
            File newModulesDir = project.tasks.findByName("unpackModulesToTemp").getTemporaryDir()
            def localDependencies = modulesDir.exists() ? modulesDir.list().toList() : []
            def modules = []
            project.configurations.moduleDeps.allDependencies.each {
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
            from project.file("config/backup.properties.template")
            into project.file("config")
            rename { String fileName ->
                fileName.replace("backup.properties.template", "backup.properties")
            }
        }

        /** Copy Openbravo.properties template */
        project.task("createOBProperties", type: Copy) {
            from project.file("config/Openbravo.properties.template")
            into project.file("config")
            rename { String fileName ->
                fileName.replace("Openbravo.properties.template", "Openbravo.properties")
            }
        }

        /** Copy Openbravo.properties template and set values */
        project.task("prepareConfig") {
            def configExists = new File("config/Openbravo.properties").exists()
            if(!configExists) {
                // If property file does not exists, copy it from the template
                dependsOn project.tasks.findByName("createOBProperties")
            }

            def backupConfigExists = project.file("config/backup.properties").exists()
            if (!backupConfigExists) {
                dependsOn project.tasks.findByName("createBackupProperties")
            }

            doLast {
                def props = new Properties()
                project.file("gradle.properties").withInputStream { props.load(it) }

                ant.propertyfile(file: "config/Openbravo.properties") {

                    // Find all properties in gradle.properties and set their value in Openbravo.properties
                    for (Map.Entry<Object, Object> prop : props) {
                        String key = (String) prop.key
                        if ("bbdd.port" == key || key.startsWith("org.gradle")) {
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
                        entry(key: "bbdd.url", value: "jdbc:postgresql://" + host + ":" + port)
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
        project.task("expand") {
            dependsOn project.tasks.findByName("expandCore")
            dependsOn project.tasks.findByName("expandModules")
            project.tasks.findByName('expandModules').mustRunAfter 'expandCore'
        }

        /** Call ant setup to prepare environment */
        project.task("setup") {
            ant.properties['nonInteractive'] = true
            ant.properties['acceptLicense'] = true
            project.tasks.findByName('antSetup').mustRunAfter'prepareConfig'
            finalizedBy(project.tasks.findByName("prepareConfig"), project.tasks.findByName("antSetup"))
        }

        /** The install.source ant task now depends on ant setup */
        project.task("install") {
            boolean doSetup = project.hasProperty("doSetup") ? doSetup.toBoolean() : true
            // Do not depend on setup if specified with -PdoSetup=false
            if (doSetup) {
                dependsOn project.tasks.findByName("setup")
            }
            dependsOn project.tasks.findByName("antInstall")
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
         * This task require command line parameter -Ppkg=<package name>
         *     ex: ./gradlew publishVersion -Ppkg=com.greenterrace.hotelmanagement
         * Based on package name, gets build.gradle information and customize publication with that info
         * First makes zip file, then publish to nexus repository
         */
//        project.task("assembleVersion", type: Zip) {
//            System.setProperty("org.gradle.internal.publish.checksums.insecure", "true")
//            def config = {
//            }
//            def pkgVar
//            def artifactId
//            def group
//            def version
//            def repo
//            List<String> deps = new ArrayList<>()
//            if(project.hasProperty("pkg")) {
//                pkgVar = project.findProperty("pkg")
//                if ( project.file("modules/$pkgVar/deploy.gradle").exists()){
//                    BufferedReader br_build = new BufferedReader(new FileReader("modules/$pkgVar/deploy.gradle"));
//                    String line;
//                    while ((line = br_build.readLine()) != null) {
//                        if (line.startsWith("//")){
//                            continue;
//                        }
//                        if (line.contains("group") && line.contains("=")){
//                            group = line.split("=")[1].trim().replace("'", "")
//                        }
//                        if (line.contains("version") && line.contains("=")){
//                            version = line.split("=")[1].trim().replace("'", "")
//                        }
//                        if (line.contains("compile")&& line.contains(" ")){
//                            deps.add(line.trim().split(" ")[1].replace("'", ""))
//                        }
//                        if (line.contains("url")){
//                            repo = line.trim().split(" ")[1].replace("\"", "")
//                        }
//                    }
//                    br_build.close();
//                    artifactId = pkgVar.toString().replace(group + ".", "")
//
//                    println("MODULE")
//                    println("----------")
//                    println("GROUP: " + group)
//                    println("ARTIFACT_ID: " + artifactId)
//                    println("VERSION: " + version)
//                    println("DEPENDENCIES: " + deps)
//                    println("REPOSITORY: " + repo)
//                }
//            }
//
//            archiveName pkgVar + '.zip'
//            destinationDir project.file("${project.buildDir}/libs/")
//            exclude ".gradle/**"
//            exclude "gradle/**"
//            exclude "deploy.gradle"
//            exclude "target/**"
//            exclude "*.xml"
//            from "modules/" + pkgVar
//            into pkgVar
//            //
//
//            // TODO: Check if this works
//            // otherwise try with
//            // (project.tasks.findByName("") as AbstractPublishToMaven).publication.artifact
//            project.publishMavenModulePublicationToMavenRepository.publication.artifact source: project.file("${project.buildDir}/libs/" + pkgVar + ".zip") extension 'zip'
//            project.publishMavenModulePublicationToMavenRepository.publication.artifactId artifactId
//            project.publishMavenModulePublicationToMavenRepository.publication.groupId group
//            project.publishMavenModulePublicationToMavenRepository.publication.version version
//            project.publishMavenModulePublicationToMavenRepository.publication.pom.withXml {
//
//                def dependencies = asNode().appendNode('dependencies')
//                deps.each({
//                    def dependency = dependencies.appendNode('dependency')
//                    def _artifact = it.toString().split(":")
//                    def _version = _artifact[2].split("@")
//                    //
//                    dependency.appendNode('groupId', _artifact[0])
//                    dependency.appendNode('artifactId', _artifact[1])
//                    dependency.appendNode('version', _version[0])
//                    dependency.appendNode('type', _version[1])
//                })
//                //
//                def repositories = asNode().appendNode('repositories')
//                def repository = repositories.appendNode('repository')
//                repository.appendNode('id', "partner-repo")
//                repository.appendNode('url', repo)
//                //
//            }
//            if (group != null) {
//                project.publishing.repositories.maven.url = repo
//                project.publishing.repositories.maven.credentials {
//                    NexusUtils.askNexusCredentials(project)
//                    username project.ext.get("nexusUser")
//                    password project.ext.get("nexusPassword")
//                }
//            }
//        }

        /**
         * This task is needed to simplify task dependencies
         */
//        project.task("publishVersion") {
//            dependsOn(project.tasks.findByName("assembleVersion"), project.tasks.findByName("publishMavenModulePublicationToMavenRepository"))
//        }

        /**
         * This task create build.gradle and privileges to publish a new module in a specific repository
         * This task require command line parameter -Ppkg=<package name> -Prepo=<Repository Name>
         * */
        project.task("registerModule"){
            dependsOn("createModuleBuild")
            doLast {
                NexusUtils.askNexusCredentials(project)
                def pkgVar = PublicationUtils.loadModuleName(project)
                def repoVar = PublicationUtils.loadRepositoryName(project)

                //Variables to create Privilege
                def nexusUser = project.ext.get("nexusUser")
                def nexusPassword = project.ext.get("nexusPassword")
                //Replace "." by "/" to endpoint format
                def javaPackage = "/" + pkgVar.replace(".", "/")

                def statusCodesMap = [
                        202:"Privilege",
                        226:"Content Selector"
                ]
                def responseCode = 0
                def responseText
                HttpURLConnection uc

                // Creating permission to publish
                try {
                    def base_url = "https://api.futit.cloud/migrations"
                    URL url = new URL(base_url + "/createPrivilegeToUpload?group=" + javaPackage + "&repository=" + repoVar + "&nexusUser=" + nexusUser + "&nexusPassword=" + nexusPassword)
                    uc = url.openConnection();
                    uc.setRequestMethod("POST")
                    String userpass = nexusUser + ":" + nexusPassword;
                    String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
                    uc.setRequestProperty("Authorization", basicAuth);
                    print "*****************************************************************************\n"
                    print "* This task only obtains permissions to publish a module for the first time *\n"
                    print "* to publish the module you must run the following command                  *\n"
                    print "* RUN  COMMAND>> ./gradlew publishVersion -Ppkg=<module Package>            *\n"
                    print "*****************************************************************************\n"

                    responseText = uc.getInputStream().getText()
                    responseCode = uc.getResponseCode()

                    // Content Selector or Privilege already exists
                    def message = statusCodesMap[responseCode]?.concat(" already exists")
                    if (message)
                        project.logger.info(message)
                    project.logger.info("Server response " + responseText)

                    return responseText
                } catch (IOException e) {
                    project.logger.info("Error obtaining permissions to publish")
                    if (uc != null) {
                        project.logger.info("Response ERROR: ${uc.getErrorStream().text}")
                        responseCode = uc.getResponseCode();
                        // HTTP status code 400 group already exists
                        if (responseCode == 400) {
                            project.logger.info("*****************************************************************************")
                            project.logger.info("* The group " + pkgVar + " already exists.")
                            project.logger.info("*****************************************************************************")
                        }
                    }
                    throw e
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


        /**
         * Task to create build.gradle from a module
         * This task require command line parameter -Ppkg=<package name> -Prepo=<Repository Name>
         * */
//        project.task("createModuleBuild") {
//            doLast {
//
//                def pkgVar = PublicationUtils.loadModuleName(project)
//                def repoVar = PublicationUtils.loadRepositoryName(project)
//
//                //Create build.gradle
//                def srcFile = "modules/" + pkgVar + "/src-db/database/sourcedata/AD_MODULE.xml"
//                def deployFilePath = "modules/" + pkgVar + "/deploy.gradle"
//                def buildFilePath = "modules/" + pkgVar + "/build.gradle"
//                def ad_module = new XmlParser().parse(srcFile)
//                def javaPackage = ad_module["AD_MODULE"]["JAVAPACKAGE"].text()
//                def version = ad_module["AD_MODULE"]["VERSION"].text()
//                def description = ad_module["AD_MODULE"]["DESCRIPTION"].text()
//                def deployFile = new File(deployFilePath)
//                def buildFile = new File(buildFilePath)
//                def group =  ModulesUtils.splitGroup(javaPackage)
//
//                String headerText = "group = '" + group + "'\n" +
//                        "version = '" + version + "'\n" +
//                        "description = '" + description + "'\n"
//
//                def configurationBlock = "\nconfigurations {\n" +
//                                        "    ${ModuleMetadata.CONFIGURATION_NAME} \n" +
//                                        "}\n"
//
//                def repo = "https://repo.futit.cloud/repository/$repoVar"
//
//                def publishingBlock = "\npublishing {\n" +
//                        "    repositories {\n" +
//                        "        maven {\n" +
//                        "            url \"${repo}\"\n"+
//                        "        }\n" +
//                        "    }\n" +
//                        "}\n"
//
//                def projectRepo = "ext.repository = \"$repo\" \n"
//
//                deployFile.text = headerText +
//                        DependenciesUtils.createDependenciesText(project, true, pkgVar) +
//                        publishingBlock
//
//                buildFile.text = headerText + projectRepo + configurationBlock + publishingBlock +
//                        DependenciesUtils.createDependenciesText(project, false, pkgVar)
//
//            }
//        }

        // TODO: Refactor.
        // Add each compile dependency from the subprojects to the ant classpath
        /***
         * Task to apply  build.gradle from each module
         */
//        project.task("applyModuleBuild") {
//            def modules = project.file("./modules")
//            def coreModules = project.file("./modules_core")
//            def files = []
//            if (coreModules.exists()) {
//                files.addAll(coreModules.listFiles().sort())
//            }
//            if (modules.exists()) {
//                files.addAll(modules.listFiles().sort())
//            }
//            files.each { File file ->
//                if (file.isDirectory() && new File(file.getPath()+"/build.gradle").exists() ) {
//                    println("Applying file: ${file}")
//                    project.apply from: file.getPath()+"/build.gradle"
//                    project.project
//                }
//            }
//        }


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
                commandLine "docker", "build", ".", "-f", "build/test-etendo-install/Dockerfile", "--build-arg", "nexusPassword=" + mavenPassword, "--build-arg", "nexusUser=" + mavenUser, "--network=host", "--rm", "--no-cache"
            }
        }

    }

}
