package com.etendoerp.legacy.sqlc

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
            
            dependsOn 'gradleSqlcAD'

            // --- INPUTS ---
            inputs.files(project.fileTree(dir: 'src', include: '**/*.xsql')).withPropertyName('rootXsql')
            inputs.files(project.fileTree(dir: 'modules', include: '**/src/**/*.xsql')).withPropertyName('modulesXsql')
            if (project.file('modules_core').exists()) {
                inputs.files(project.fileTree(dir: 'modules_core', include: '**/src/**/*.xsql')).withPropertyName('modulesCoreXsql')
            }
            inputs.file('config/Openbravo.properties').withPropertyName('config')

            // --- OUTPUTS ---
            def outputDir = project.file('build/javasqlc/src')
            outputs.dir(outputDir).withPropertyName('generatedJava')
            
            // Enable caching
            outputs.cacheIf { true }

            doLast {
                outputDir.mkdirs()
                
                // Use compileClasspath instead of Ant references to avoid conversion issues/cycles
                def sqlcClasspath = project.files('src-core/lib/openbravo-core.jar') + 
                                    project.configurations.findByName('compileClasspath') +
                                    project.files('config')

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
                           "${baseConfig}/Openbravo.properties",
                           ".xsql",
                           dirIni,
                           targetDir.absolutePath,
                           packageFilter,
                           "false"
                       ]
                   }
                }

                // 1. Root src - Match Ant: run from src, dirIni is ., output is build/javasqlc/src
                runSqlc('src', 'null', project.file('build/javasqlc/src'))

                // 2. modules_core - Run from modules_core, filter handles modules
                runSqlc('modules_core', '*/src', project.file('build/javasqlc/src'))

                // 3. modules
                runSqlc('modules', '*/src', project.file('build/javasqlc/src'))
            }
        }
    }

    private static void createSqlcADTask(Project project) {
        project.tasks.register('gradleSqlcAD') {
            description = 'Generates Java code from .xsql files in srcAD (Native Gradle implementation)'
            group = 'etendo-sqlc'

            // --- INPUTS ---
            inputs.dir('srcAD').withPropertyName('srcADDir')
            inputs.file('config/Openbravo.properties').withPropertyName('config')
            
            // --- OUTPUTS ---
            def outputDir = project.file('build/javasqlc/srcAD')
            outputs.dir(outputDir).withPropertyName('generatedADJava')
            
            outputs.cacheIf { true }

            doLast {
                def sqlcClasspath = project.files('src-core/lib/openbravo-core.jar') + 
                                    project.configurations.findByName('compileClasspath') +
                                    project.files('config')

                def baseConfig = project.file('config').absolutePath
                def log4jConfig = project.file('build/etendo/log4j2-no-db.xml')

                if (project.file('srcAD').exists()) {
                    project.javaexec {
                        workingDir = project.file('srcAD')
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
                            "${baseConfig}/Openbravo.properties",
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
            mainSourceSet.java.srcDir 'build/javasqlc/src'
            mainSourceSet.java.srcDir 'build/javasqlc/srcAD'
            mainSourceSet.java.srcDir 'srcAD'
            
            // Ensure compileJava depends on gradleSqlc
            def compileJavaTask = project.tasks.findByName('compileJava')
            if (compileJavaTask != null) {
                compileJavaTask.dependsOn('gradleSqlc')
            }

            // Fix implicit dependencies on configuration generation tasks
            ['gradleSqlc', 'gradleSqlcAD'].each { sqlcTaskName ->
                def sqlcTask = project.tasks.findByName(sqlcTaskName)
                if (sqlcTask != null) {
                    ['createQuartzProperties', 'createOBProperties', 'createBackupProperties'].each { genTaskName ->
                        def genTask = project.tasks.findByName(genTaskName)
                        if (genTask != null) {
                            sqlcTask.dependsOn(genTask)
                        }
                    }
                }
            }
        }
    }
}
