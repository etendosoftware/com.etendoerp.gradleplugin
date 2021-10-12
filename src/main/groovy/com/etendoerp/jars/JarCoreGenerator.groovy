package com.etendoerp.jars


import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar

class JarCoreGenerator {

    public static final String RESOURCES_DIR = 'build/resources'
    public static final String RESOURCES_JAR_DESTINATION = 'META-INF/'
    public static final String BUILD_CLASES = 'build/classes'
    public static final String ETENDO_CORE = 'etendo-core'

    static load(Project project) {

        project.dependencies {
            project.dependencies {
                implementation project.fileTree(dir: "${project.rootDir}/lib", include: ['**/*.jar'])
                implementation project.fileTree(dir: "${project.rootDir}/modules", include: ['**/*.jar'])
                implementation project.fileTree(dir: "${project.rootDir}/modules_core", include: ['**/*.jar'])
                implementation project.fileTree(dir: "${project.rootDir}/src-core/lib", include: ['**/*.jar'])
                implementation project.fileTree(dir: "${project.rootDir}/src-wad/lib", include: ['**/*.jar'])
                implementation project.fileTree(dir: "${project.rootDir}/src-trl/lib", include: ['**/*.jar'])
            }
        }

        project.tasks.register("jarConfig") {
            doLast {
                project.logger.info("Starting JAR configuration.")
                def jarTask = (project.jar as Jar)
                def generated = Utils.loadGeneratedEntitiesFile(project)

                jarTask.archiveBaseName.set(ETENDO_CORE)
                //Excluding src-gen
                jarTask.exclude(PathUtils.fromPackageToPathClass(generated))

                // Workaround for issue: https://issues.apache.org/jira/browse/LOG4J2-673
                jarTask.exclude "**/Log4j2Plugins.dat"

                jarTask.from(RESOURCES_DIR) {
                    into(RESOURCES_JAR_DESTINATION)
                }

                jarTask.manifest {
                    // make lo4j2 work in the generated jar, since the log4j jar is a multirelease one
                    attributes 'Multi-Release': 'true'
                }

            }
        }

        project.tasks.register("sourcesJarConfig") {
            doLast {
                project.logger.info("Starting Sources JAR configuration.")
                def sourcesJarTask = (project.sourcesJar as Jar)
                def generated = Utils.loadGeneratedEntitiesFile(project)

                // Exclude .class files (but include those in modulescript and buildvalidation folders, as they are "precompiled")
                sourcesJarTask.exclude { FileTreeElement el ->
                    return el.file.getName().endsWith(".class") && !el.file.getAbsolutePath().contains("modulescript") && !el.file.getAbsolutePath().contains("buildvalidation")
                }

                sourcesJarTask.from('build/resources') {
                    into('/META-INF')
                }

                sourcesJarTask.into 'META-INF/etendo/src'
            }
        }

        project.tasks.register("cleanResources") {
            def resourcesFolder = new File("${project.buildDir}/resources")
            if (resourcesFolder.exists() && resourcesFolder.isDirectory()) {
                resourcesFolder.deleteDir()
            }
        }

        project.tasks.register("copyBeans", Copy) {
            from "${project.projectDir}/modules_core/org.openbravo.base.weld/config/beans.xml"
            into "${project.buildDir}/resources"
        }

        project.tasks.register("copyLibsSources", Copy) {
            from "${project.projectDir}/lib"
            into "${project.buildDir}/resources/etendo/lib"
        }

        project.tasks.register("copySrcDB", Copy) {
            from "${project.projectDir}/src-db"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/etendo/src-db"
        }

        project.tasks.register("copySrcDBSources", Copy) {
            from "${project.projectDir}/src-db"
            include "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/etendo/src-db"
        }

        project.tasks.register("copySrc", Copy) {
            from "${project.projectDir}/src"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/etendo/src"
        }

        project.tasks.register("copyModules", Copy) {
            from "${project.projectDir}/modules"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/etendo/modules"
        }

        project.tasks.register("copyModulesCore", Copy) {
            from "${project.projectDir}/modules_core"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/etendo/modules"
        }

        project.tasks.register("copyModulesSources", Copy) {
            from "${project.projectDir}/modules"
            include "**/*${FileExtensions.JAVA}"
            includeEmptyDirs = false
            into "${project.buildDir}/resources/etendo/modules"
        }

        project.tasks.register("copyModulesCoreSources", Copy) {
            from "${project.projectDir}/modules_core"
            include "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/etendo/modules"
        }

        project.tasks.register("copySrcJmh", Copy) {
            from "${project.projectDir}/src-jmh"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/src-jmh"
        }

        project.tasks.register("copySrcJmhSources", Copy) {
            from "${project.projectDir}/src-jmh"
            include "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/etendo/src-jmh"
        }

        project.tasks.register("copySrcUtil", Copy) {
            from ([
                    "${project.projectDir}/src-util/buildvalidation/build/classes",
                    "${project.projectDir}/src-util/modulescript/build/classes"
            ])
            include "**/*${FileExtensions.CLASS}"
            into "${project.buildDir}/resources/src-util"
        }

        project.tasks.register("copySrcUtilSources", Copy) {
            from ("${project.projectDir}/src-util")
            include "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/etendo/src-util"
        }

        project.tasks.register("copySrcTrlSources", Copy) {
            from "${project.projectDir}/src-trl"
            include "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/etendo/src-trl"
        }

        project.tasks.register("copySrcCoreSources", Copy) {
            from "${project.projectDir}/src-core"
            include "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/etendo/src-core"
        }

        project.tasks.register("copySrcWadSources", Copy) {
            from "${project.projectDir}/src-wad"
            include "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/etendo/src-wad"
        }

        project.tasks.register("copyWebResources", Copy) {
            from ("${project.projectDir}/web",)
            into "${project.buildDir}/resources/etendo/web"
        }
        project.tasks.register("copyBuild", Copy) {
            from ("${project.projectDir}")
            include "build.xml"
            into "${project.buildDir}/resources"
        }
        project.tasks.register("copyReferenceData", Copy) {
            from ("${project.projectDir}/referencedata")
            into "${project.buildDir}/resources/etendo/referencedata"
        }

        project.tasks.register("copyConfig", Copy) {
            from ("${project.projectDir}/config")
            exclude 'Format.xml'
            exclude 'Openbravo.properties'
            exclude 'checksums'
            exclude 'log4j2-web.xml'
            exclude 'log4j2.xml'
            exclude 'redisson-config.yaml'
            into "${project.buildDir}/resources/etendo/config"
        }

        def resourcesDirs = [
                "copyReferenceData",
                "copyConfig",
                "copyBuild",
                "copyBeans",
                "copySrcDB",
                "copySrc",
                "copyModules",
                "copyModulesCore",
                "copySrcJmh",
                "copySrcUtil",
                "copyWebResources"
        ]

        def sourcesJarDependencies = [
                "cleanResources",
                "copyLibsSources",
                "copySrcDBSources",
                "copyModulesSources",
                "copyModulesCoreSources",
                "copySrcCoreSources",
                "copySrcJmhSources",
                "copySrcTrlSources",
                "copySrcUtilSources",
                "copySrcWadSources"
        ]

        project.jar.dependsOn("jarConfig")
        project.jarConfig.dependsOn("cleanResources")
        project.jarConfig.dependsOn(resourcesDirs)
        project.jarConfig.mustRunAfter("cleanResources")
        project.sourcesJar.dependsOn("sourcesJarConfig")
        project.sourcesJarConfig.dependsOn(sourcesJarDependencies)
        project.sourcesJarConfig.mustRunAfter("cleanResources")

        project.jar.from{
            project.configurations.compileClasspath.collect { it.isDirectory() ? it : project.zipTree(it) }
        }
    }
}

