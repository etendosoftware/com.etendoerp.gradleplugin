package com.etendoerp.legacy.ant

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Copy

/**
 * SqlcLoader - Migración de la tarea Ant 'sqlc' a Gradle
 * 
 * Genera código Java desde archivos .xsql usando org.openbravo.data.Sqlc
 * 
 * Tareas creadas:
 * - gradleSqlc: Genera código SQLC desde src-db/database
 * - gradleSqlcAD: Genera código SQLC desde srcAD
 * - gradleSqlcModules: Genera código SQLC desde módulos
 */
class SqlcLoader {
    
    static final String SQLC_OUTPUT_DIR = 'build/javasqlc/sqlc/src'
    static final String SQLC_OUTPUT_DIR_AD = 'build/javasqlc/sqlc/srcAD'
    
    static final String SQLC_CLASS = 'org.openbravo.data.Sqlc'
    
    static void load(Project project) {
        
        /**
         * Task: etendoSqlc
         * Equivalente Ant: <java classname="org.openbravo.data.Sqlc">
         * 
         * Genera código Java desde archivos .xsql en src-db/database
         */
        project.tasks.register('etendoSqlc', JavaExec) {
            group = 'etendo build'
            description = 'Generate Java code from .xsql files (SQLC)'
            
            mainClass.set(SQLC_CLASS)
            classpath = project.configurations.runtimeClasspath
            
            maxHeapSize = '1024m'
            
            def coreInSources = AntLoader.isCoreInSources(project)
            def basePath = coreInSources 
                ? project.projectDir.absolutePath 
                : "${project.buildDir}/etendo"
            
            doFirst {
                // Crear directorios de salida
                project.file(SQLC_OUTPUT_DIR).mkdirs()
                project.file(SQLC_OUTPUT_DIR_AD).mkdirs()
                
                // Argumentos para Sqlc
                // Formato: srcDir destDir destDirAD filePattern checkMD5
                args = [
                    "${basePath}/src-db/database",  // sourceDir
                    project.file(SQLC_OUTPUT_DIR).absolutePath,     // destDir
                    project.file(SQLC_OUTPUT_DIR_AD).absolutePath,  // destDirAD
                    '**/sqlc/**/*.xsql',            // filePattern
                    'false'                         // checkMD5 (smart mode)
                ]
                
                project.logger.info("* Running SQLC generation")
                project.logger.info("  Source: ${basePath}/src-db/database")
                project.logger.info("  Output: ${SQLC_OUTPUT_DIR}")
                project.logger.info("  OutputAD: ${SQLC_OUTPUT_DIR_AD}")
            }
        }
        
        /**
         * Task: etendoSqlcAD
         * Genera código SQLC desde srcAD (Application Dictionary)
         */
        project.tasks.register('etendoSqlcAD', JavaExec) {
            group = 'etendo build'
            description = 'Generate SQLC code from srcAD'
            dependsOn 'etendoSqlc'
            
            mainClass.set(SQLC_CLASS)
            classpath = project.configurations.runtimeClasspath
            maxHeapSize = '1024m'
            
            doFirst {
                def coreInSources = AntLoader.isCoreInSources(project)
                def srcADPath = coreInSources 
                    ? "${project.projectDir}/srcAD" 
                    : "${project.buildDir}/etendo/srcAD"
                
                args = [
                    srcADPath,
                    project.file(SQLC_OUTPUT_DIR).absolutePath,
                    project.file(SQLC_OUTPUT_DIR_AD).absolutePath,
                    '**/*.xsql',
                    'false'
                ]
                
                project.logger.info("* Running SQLC AD generation from ${srcADPath}")
            }
        }
        
        /**
         * Task: etendoSqlcModules
         * Genera código SQLC desde módulos que contienen archivos .xsql
         */
        project.tasks.register('etendoSqlcModules') {
            group = 'etendo build'
            description = 'Generate SQLC code from modules'
            dependsOn 'etendoSqlcAD'
            
            doLast {
                def modulesDir = project.file('modules')
                def modulesCoreDir = project.file('modules_core')
                
                // Procesar módulos en 'modules/'
                if (modulesDir.exists()) {
                    processModulesDirectory(project, modulesDir)
                }
                
                // Procesar módulos en 'modules_core/' (solo si es modo sources)
                if (AntLoader.isCoreInSources(project) && modulesCoreDir.exists()) {
                    processModulesDirectory(project, modulesCoreDir)
                }
            }
        }
        
        /**
         * Task agregada: etendoSqlcAll
         * Ejecuta toda la generación SQLC
         */
        project.tasks.register('etendoSqlcAll') {
            group = 'etendo build'
            description = 'Run all SQLC generation tasks'
            dependsOn 'etendoSqlcModules'
        }
    }
    
    /**
     * Procesa un directorio de módulos buscando archivos .xsql
     */
    private static void processModulesDirectory(Project project, File modulesDir) {
        modulesDir.eachDir { moduleDir ->
            def srcDbDir = new File(moduleDir, 'src-db/database')
            if (srcDbDir.exists()) {
                def xsqlFiles = project.fileTree(srcDbDir) {
                    include '**/*.xsql'
                }
                
                if (!xsqlFiles.isEmpty()) {
                    project.logger.info("* Processing SQLC for module: ${moduleDir.name}")
                    
                    project.javaexec {
                        mainClass.set(SQLC_CLASS)
                        classpath = project.configurations.runtimeClasspath
                        maxHeapSize = '1024m'
                        
                        args = [
                            srcDbDir.absolutePath,
                            project.file(SQLC_OUTPUT_DIR).absolutePath,
                            project.file(SQLC_OUTPUT_DIR_AD).absolutePath,
                            '**/*.xsql',
                            'false'
                        ]
                    }
                }
            }
        }
    }
}
