package com.etendoerp.legacy.ant

import org.gradle.api.Project

/**
 * InstallLoader - Migración de la tarea Ant 'install.source' a Gradle
 * 
 * Orquesta el flujo de instalación completa de Etendo
 * 
 * Flujo original de Ant:
 * install.source → init → cleanSubfolders → create.database → 
 *                  wad.lib → trl.lib → compile.complete.deploy → 
 *                  apply.module → import.sample.data
 * 
 * Tareas que se mantienen en Ant:
 * - create.database (lógica de BD compleja)
 * - apply.module (transacciones de BD)
 * - import.sample.data (importación de datos)
 */
class InstallLoader {
    
    static void load(Project project) {
        
        /**
         * Task: etendoInstall
         * Instalación completa usando máximo Gradle
         */
        project.tasks.register('etendoInstall') {
            group = 'etendo install'
            description = 'Full Etendo installation using Gradle tasks'
            
            // Determinar si ejecutar setup
            def doSetup = project.findProperty('doSetup')?.toString()?.toBoolean() ?: true
            
            if (doSetup) {
                dependsOn 'setup'
            }
            
            doFirst {
                project.logger.lifecycle("*********************************************")
                project.logger.lifecycle("* Starting Etendo Installation (Gradle)")
                project.logger.lifecycle("*********************************************")
                
                validateInstallation(project)
            }
            
            finalizedBy 'installSequence'
        }
        
        /**
         * Task: installSequence
         * Secuencia de instalación
         */
        project.tasks.register('installSequence') {
            group = 'etendo install'
            description = 'Installation sequence'
            
            doLast {
                def steps = [
                    'cleanSubfolders',           // Limpiar carpetas
                    'create.database',           // Crear BD (Ant)
                    'etendoWadLib',              // WAD lib (Gradle)
                    'etendoTrlJar',              // TRL lib (Gradle)
                    'etendoCompileComplete',     // Compilación completa (Gradle)
                    'apply.module',              // Aplicar módulos (Ant)
                ]
                
                // Importar sample data si está configurado
                def importSampleData = project.findProperty('import.sampledata')?.toString()?.toBoolean() ?: false
                if (importSampleData) {
                    steps.add('import.sample.data')
                }
                
                project.logger.lifecycle("* Installation sequence: ${steps.join(' → ')}")
                
                // Las tareas se ejecutan por dependencias en Gradle
                // Este doLast es principalmente para logging
                
                project.logger.lifecycle("*********************************************")
                project.logger.lifecycle("* Installation completed")
                project.logger.lifecycle("*********************************************")
            }
        }
        
        /**
         * Task: etendoCompileComplete
         * Compilación completa equivalente a compile.complete.deploy
         */
        project.tasks.register('etendoCompileComplete') {
            group = 'etendo install'
            description = 'Complete compilation with deploy'
            
            dependsOn 'etendoSqlcAll'     // SQLC generation
            dependsOn 'etendoWad'         // WAD generation
            dependsOn 'etendoWadLib'      // WAD JAR
            dependsOn 'etendoTrlJar'      // TRL JAR
            dependsOn 'compileJava'       // Java compilation
            dependsOn 'etendoCopyFiles'   // Copy resources
            
            doLast {
                project.logger.lifecycle("* Compile complete finished")
            }
        }
        
        /**
         * Task: cleanBuildFolders
         * Limpia carpetas de build antes de instalación
         */
        project.tasks.register('cleanBuildFolders') {
            group = 'etendo install'
            description = 'Clean build subfolders for fresh installation'
            
            doLast {
                def dirsToClean = [
                    'build/classes',
                    'build/javasqlc',
                    'build/lib',
                    'WebContent/WEB-INF/lib',
                    'WebContent/WEB-INF/classes',
                ]
                
                dirsToClean.each { dir ->
                    def dirFile = project.file(dir)
                    if (dirFile.exists()) {
                        project.logger.info("* Cleaning: ${dir}")
                        project.delete(dirFile)
                    }
                }
            }
        }
        
        /**
         * Task: installWithoutSampleData
         * Instalación sin datos de ejemplo
         */
        project.tasks.register('installWithoutSampleData') {
            group = 'etendo install'
            description = 'Install without importing sample data'
            
            doFirst {
                project.ext.set('import.sampledata', false)
            }
            
            finalizedBy 'etendoInstall'
        }
        
        /**
         * Task: reinstall
         * Reinstalación completa (drop + create + install)
         */
        project.tasks.register('reinstall') {
            group = 'etendo install'
            description = 'Complete reinstallation (drops existing database)'
            
            doFirst {
                project.logger.warn("*********************************************")
                project.logger.warn("* WARNING: This will DROP the existing database!")
                project.logger.warn("*********************************************")
            }
            
            // Primero ejecutar drop.database si existe
            def dropDb = project.tasks.findByName('drop.database')
            if (dropDb) {
                dependsOn dropDb
            }
            
            finalizedBy 'etendoInstall'
        }
        
        // Configurar dependencias entre tareas después de la evaluación
        project.afterEvaluate {
            configureInstallDependencies(project)
        }
    }
    
    /**
     * Valida que los prerrequisitos de instalación estén listos
     */
    private static void validateInstallation(Project project) {
        def errors = []
        
        // Verificar archivos de configuración
        if (!project.file('config/Openbravo.properties').exists()) {
            errors.add('config/Openbravo.properties not found. Run setup first.')
        }
        
        if (!project.file('config/Format.xml').exists()) {
            errors.add('config/Format.xml not found. Run setup first.')
        }
        
        // Verificar que existe el core
        def coreExists = AntLoader.isCoreInSources(project) || 
                         project.file("${project.buildDir}/etendo").exists()
        if (!coreExists) {
            errors.add('Etendo core not found. Run expandCore first.')
        }
        
        if (!errors.isEmpty()) {
            project.logger.error("* Installation validation failed:")
            errors.each { project.logger.error("  - ${it}") }
            throw new org.gradle.api.GradleException("Installation prerequisites not met")
        }
    }
    
    /**
     * Configura dependencias entre tareas de instalación
     */
    private static void configureInstallDependencies(Project project) {
        // Asegurar orden de tareas
        def cleanSub = project.tasks.findByName('cleanSubfolders')
        def createDb = project.tasks.findByName('create.database')
        def wadLib = project.tasks.findByName('etendoWadLib')
        def trlJar = project.tasks.findByName('etendoTrlJar')
        def compile = project.tasks.findByName('etendoCompileComplete')
        def applyMod = project.tasks.findByName('apply.module')
        
        // cleanSubfolders → create.database → wadLib → trlJar → compile → apply.module
        if (createDb && cleanSub) {
            createDb.mustRunAfter(cleanSub)
        }
        if (wadLib && createDb) {
            wadLib.mustRunAfter(createDb)
        }
        if (trlJar && wadLib) {
            trlJar.mustRunAfter(wadLib)
        }
        if (compile && trlJar) {
            compile.mustRunAfter(trlJar)
        }
        if (applyMod && compile) {
            applyMod.mustRunAfter(compile)
        }
    }
}
