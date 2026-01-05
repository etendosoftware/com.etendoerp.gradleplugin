package com.etendoerp.legacy.sqlc

import com.etendoerp.legacy.ant.AntLoader
import org.gradle.api.Project

class SqlcLoader {

    static void load(Project project) {
        createSqlcTask(project)
        createSqlcADTask(project)
        configureSourceSets(project)
    }

    private static void createSqlcTask(Project project) {
        project.tasks.register('gradleSqlc') {
            description = 'Generates Java code from .xsql files (Native Gradle implementation)'
            group = 'etendo-sqlc'
            
            dependsOn 'prepareConfig'

            // Detect mode
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"
            def outputDir = coreInSources ? project.file('build/javasqlc/sqlc/src') : project.file('build/etendo/build/javasqlc/sqlc/src')

            // --- INPUTS ---
            if (project.file("${corePath}/src").exists()) {
                inputs.files(project.fileTree("${corePath}/src") { include '**/*.xsql' }).withPropertyName('rootXsql')
            }
            if (project.file('modules').exists()) {
                inputs.files(project.fileTree('modules') { include '**/src/**/*.xsql' }).withPropertyName('modulesXsql')
            }
            if (project.file('modules_core').exists()) {
                inputs.files(project.fileTree('modules_core') { include '**/src/**/*.xsql' }).withPropertyName('modulesCoreXsql')
            }
            
            def prepareConfig = project.tasks.named('prepareConfig')
            inputs.files(prepareConfig).withPropertyName('config')
            inputs.files("${corePath}/src-core/lib/openbravo-core.jar").optional().withPropertyName('coreJar')

            // --- OUTPUTS ---
            outputs.dir(outputDir).withPropertyName('generatedJava')
            
            // Enable caching
            outputs.cacheIf { true }

            doLast {
                outputDir.mkdirs()
                
                def openbravoProperties = prepareConfig.get().outputs.files.find { it.name == 'Openbravo.properties' }
                
                // Use compileClasspath instead of Ant references to avoid conversion issues/cycles
                def sqlcClasspath = project.files()
                
                def coreLib = project.file("${corePath}/src-core/lib/openbravo-core.jar")
                if (coreLib.exists()) sqlcClasspath += project.files(coreLib)
                
                sqlcClasspath += project.configurations.findByName('compileClasspath')
                sqlcClasspath += project.files('config')

                if (!coreInSources && project.file('build/etendo/lib').exists()) {
                    sqlcClasspath += project.fileTree(dir: 'build/etendo/lib', include: '**/*.jar')
                }

                def baseConfig = project.file('config').absolutePath
                
                // Copiar archivo de configuración de log4j dummy
                def log4jConfig = project.file('build/etendo/log4j2-no-db.xml')
                if (!log4jConfig.exists()) {
                    log4jConfig.parentFile.mkdirs()
                    log4jConfig.text = '''<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>'''
                }

                def runSqlc = { String rootDir, String packageFilter, File targetDir, String dirIni = "." ->
                   if (!project.file(rootDir).exists()) return
                   if (!targetDir.exists()) targetDir.mkdirs()

                   project.javaexec {
                       executable = "${System.env.JAVA_HOME}/bin/java"
                       workingDir = project.file(rootDir)
                       mainClass = 'org.openbravo.data.Sqlc'
                       classpath = sqlcClasspath
                       jvmArgs = [
                           "-Djava.security.egd=file:///dev/urandom",
                           "-Dlog4j.configurationFile=${log4jConfig.absolutePath}",
                           "-Dorg.openbravo.utils.OBRebuildAppender.disabled=true",
                           "-Dsqlc.queryExecutionStrategy=traditional"
                       ]
                       maxHeapSize = '1024m'
                       
                       args = [
                           openbravoProperties.absolutePath,
                           ".xsql",
                           dirIni,
                           targetDir.absolutePath,
                           packageFilter,
                           "false"
                       ]
                   }
                }

                // 1. Root src - Match Ant: run from src, dirIni is ., output is build/javasqlc/src
                runSqlc("${corePath}/src", 'null', outputDir)

                // 2. modules_core - Run from modules_core, filter handles modules
                runSqlc('modules_core', '*/src', outputDir)

                // 3. modules
                runSqlc('modules', '*/src', outputDir)
            }
        }
    }

    private static void createSqlcADTask(Project project) {
        project.tasks.register('gradleSqlcAD') {
            description = 'Generates Java code from .xsql files in srcAD (Native Gradle implementation)'
            group = 'etendo-sqlc'

            dependsOn 'prepareConfig'
            dependsOn 'gradleWad'

            // Detect mode
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"
            def outputDir = coreInSources ? project.file('build/javasqlc/sqlc/srcAD') : project.file('build/etendo/build/javasqlc/sqlc/srcAD')

            // --- INPUTS ---
            // Solo rastreamos archivos .xsql para evitar que cambios en archivos .java generados por WAD
            // disparen innecesariamente esta tarea si no hubo cambios en la lógica SQL.
            if (project.file('srcAD').exists()) {
                inputs.files(project.fileTree('srcAD') { include '**/*.xsql' }).withPropertyName('srcADXsql')
            }
            def prepareConfig = project.tasks.named('prepareConfig')
            inputs.files(prepareConfig).withPropertyName('config')
            inputs.files("${corePath}/src-core/lib/openbravo-core.jar").optional().withPropertyName('coreJar')
            
            // --- OUTPUTS ---
            outputs.dir(outputDir).withPropertyName('generatedADJava')
            
            outputs.cacheIf { true }

            doLast {
                def openbravoProperties = prepareConfig.get().outputs.files.find { it.name == 'Openbravo.properties' }

                def sqlcClasspath = project.files("${corePath}/src-core/lib/openbravo-core.jar") + 
                                    project.configurations.findByName('compileClasspath') +
                                    project.files('config')

                if (!coreInSources) {
                    sqlcClasspath += project.fileTree(dir: 'build/etendo/lib', include: '**/*.jar')
                }

                def baseConfig = project.file('config').absolutePath
                def log4jConfig = project.file('build/etendo/log4j2-no-db.xml')

                def srcAD = project.file('srcAD')
                if (!srcAD.exists()) {
                    srcAD.mkdirs()
                }

                if (srcAD.exists()) {
                    project.javaexec {
                        executable = "${System.env.JAVA_HOME}/bin/java"
                        workingDir = srcAD
                        mainClass = 'org.openbravo.data.Sqlc'
                        classpath = sqlcClasspath
                        jvmArgs = [
                            "-Djava.security.egd=file:///dev/urandom",
                            "-Dlog4j.configurationFile=${log4jConfig.absolutePath}",
                            "-Dorg.openbravo.utils.OBRebuildAppender.disabled=true",
                            "-Dsqlc.queryExecutionStrategy=traditional"
                        ]
                        maxHeapSize = '1024m'
                        
                        args = [
                            openbravoProperties.absolutePath,
                            ".xsql",
                            '.',
                            outputDir.absolutePath,
                            "null",
                            "false"
                        ]
                    }
                }
            }
        }
    }

    private static void configureSourceSets(Project project) {
        project.afterEvaluate {
            def mainSourceSet = project.sourceSets.main
            def coreInSources = AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            // Add all generated source directories
            if (coreInSources) {
                mainSourceSet.java.srcDir 'build/javasqlc/sqlc/src'
                mainSourceSet.java.srcDir 'build/javasqlc/sqlc/srcAD'
                mainSourceSet.java.srcDir 'build/javasqlc/wad/src'
                mainSourceSet.java.srcDir 'build/javasqlc/trl/src'
            } else {
                mainSourceSet.java.srcDir 'build/etendo/build/javasqlc/sqlc/src'
                mainSourceSet.java.srcDir 'build/etendo/build/javasqlc/sqlc/srcAD'
                mainSourceSet.java.srcDir 'build/etendo/build/javasqlc/wad/src'
                mainSourceSet.java.srcDir 'build/etendo/build/javasqlc/trl/src'
            }
            mainSourceSet.java.srcDir 'srcAD'
            
            // Ensure compileJava depends on all generation tasks
            def compileJavaTask = project.tasks.findByName('compileJava')
            if (compileJavaTask != null) {
                compileJavaTask.dependsOn('gradleSqlc')
                compileJavaTask.dependsOn('gradleSqlcAD')
                compileJavaTask.dependsOn('gradleWad')
                compileJavaTask.dependsOn('gradleTrlSqlc')
            }
        }
    }
}
