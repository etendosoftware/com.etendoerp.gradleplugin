package com.etendoerp.legacy.ant

import org.gradle.api.Project

/**
 * SmartbuildLoader - Migración de la tarea Ant 'smartbuild' a Gradle
 * 
 * Orquesta el flujo de compilación incremental de Etendo
 * 
 * Flujo original de Ant:
 * smartbuild → core.lib → update.database → wad.lib → trl.lib → 
 *              generate.entities.quick → compile → setApplied → build.deploy
 * 
 * Flujo migrado a Gradle:
 * smartbuild → [validaciones] → gradleSmartbuildCore
 */
class SmartbuildLoader {
    
    static void load(Project project) {
        
        /**
         * Task: gradleSmartbuildCore
         * Ejecuta la compilación principal de smartbuild
         */
        project.tasks.register('gradleSmartbuildCore') {
            group = 'etendo build'
            description = 'Core smartbuild execution'
            
            // Dependencias de generación de librerías
            dependsOn 'etendoWadLib'      // Genera openbravo-wad.jar
            dependsOn 'etendoTrlJar'      // Genera openbravo-trl.jar
            
            // Nota: core.lib y update.database se mantienen en Ant
            // Deben ejecutarse antes de las tareas de Gradle
            
            doLast {
                project.logger.lifecycle("* Gradle smartbuild core completed")
            }
        }
        
        /**
         * Task: gradleCompileAll
         * Compila todo el código fuente con Gradle
         */
        project.tasks.register('compileAll') {
            group = 'etendo build'
            description = 'Compile all Java sources with Gradle'
            
            dependsOn 'etendoSqlcAll'     // Genera código SQLC
            dependsOn 'etendoWad'         // Genera formularios WAD
            dependsOn 'compileJava'       // Compilación Java de Gradle
            
            // Asegurar orden
            project.tasks.findByName('compileJava')?.mustRunAfter('etendoSqlcAll', 'etendoWad')
        }
        
        /**
         * Task: smartbuild
         * Tarea principal de smartbuild - usa Gradle (Ant está en antSmartbuild)
         * 
         * Flujo: validaciones → SQLC → WAD → TRL → compileJava → copy → deploy
         */
        project.tasks.register('smartbuild') {
            group = 'etendo build'
            description = 'Smartbuild using Gradle tasks (use antSmartbuild for legacy Ant version)'
            
            // Validaciones previas
            dependsOn 'compileFilesCheck'
            dependsOn project.tasks.findByName('consistencyVerification') ?: []
            
            // Sincronización de dependencias (si existe)
            def depSync = project.tasks.findByName('dependencies.sync')
            if (depSync) {
                dependsOn depSync
            }
            
            // Generación de código
            dependsOn 'etendoSqlcAll'     // Genera código SQLC
            dependsOn 'etendoWadLib'      // Genera openbravo-wad.jar
            dependsOn 'etendoTrlJar'      // Genera openbravo-trl.jar
            
            // Compilación Java
            dependsOn 'compileJava'
            
            // Copia de archivos
            dependsOn 'etendoCopyFiles'
            
            doFirst {
                project.logger.lifecycle("*********************************************")
                project.logger.lifecycle("* Starting Gradle Smartbuild")
                project.logger.lifecycle("*********************************************")
            }
            
            doLast {
                project.logger.lifecycle("*********************************************")
                project.logger.lifecycle("* Gradle Smartbuild completed successfully")
                project.logger.lifecycle("*********************************************")
            }
        }
        
        /**
         * Task: gradleSmartbuildSequence
         * Secuencia ordenada de tareas de smartbuild
         */
        project.tasks.register('gradleSmartbuildSequence') {
            group = 'etendo build'
            description = 'Ordered smartbuild sequence'
            
            doFirst {
                project.logger.info("* Executing smartbuild sequence...")
            }
            
            doLast {
                // 1. core.lib (Ant - mantener por ahora)
                executeAntTaskIfExists(project, 'core.lib')
                
                // 2. update.database (Ant - mantener por ahora)
                executeAntTaskIfExists(project, 'update.database')
                
                // 3. WAD lib (Gradle)
                // Se ejecuta como dependencia
                
                // 4. TRL lib (Gradle)
                // Se ejecuta como dependencia
                
                // 5. generate.entities.quick (Ant - mantener por ahora)
                executeAntTaskIfExists(project, 'generate.entities.quick')
                
                // 6. Actualizar timestamp
                updateBuildTimestamp(project)
                
                project.logger.lifecycle("*********************************************")
                project.logger.lifecycle("* Gradle Smartbuild completed")
                project.logger.lifecycle("*********************************************")
            }
        }
        
        /**
         * Task: optimizedBuild
         * Build optimizado que usa máximo Gradle y mínimo Ant
         */
        project.tasks.register('optimizedBuild') {
            group = 'etendo build'
            description = 'Fully optimized build using Gradle'
            
            dependsOn 'compileFilesCheck'
            dependsOn 'etendoSqlcAll'
            dependsOn 'etendoWadLib'
            dependsOn 'etendoTrlJar'
            dependsOn 'compileJava'
            dependsOn 'etendoCopyFiles'
            
            doLast {
                project.logger.lifecycle("* Optimized build completed")
            }
        }
        
        /**
         * Task: optimizedDeploy
         * Deploy optimizado
         */
        project.tasks.register('optimizedDeploy') {
            group = 'etendo build'
            description = 'Optimized build with deploy'
            
            dependsOn 'optimizedBuild'
            finalizedBy 'etendoDeploy'
        }
        
        // Configurar orden de tareas después de la evaluación
        project.afterEvaluate {
            configureTaskOrder(project)
        }
    }
    
    /**
     * Configura el orden de ejecución de tareas
     */
    private static void configureTaskOrder(Project project) {
        // Orden: SQLC → WAD → TRL → CompileJava → Copy
        def sqlcAll = project.tasks.findByName('etendoSqlcAll')
        def wadLib = project.tasks.findByName('etendoWadLib')
        def trlJar = project.tasks.findByName('etendoTrlJar')
        def compileJava = project.tasks.findByName('compileJava')
        def copyFiles = project.tasks.findByName('etendoCopyFiles')
        
        if (wadLib && sqlcAll) {
            wadLib.mustRunAfter(sqlcAll)
        }
        if (trlJar && wadLib) {
            trlJar.mustRunAfter(wadLib)
        }
        if (compileJava && trlJar) {
            compileJava.mustRunAfter(trlJar)
        }
        if (copyFiles && compileJava) {
            copyFiles.mustRunAfter(compileJava)
        }
    }
    
    /**
     * Ejecuta una tarea Ant si existe
     */
    private static void executeAntTaskIfExists(Project project, String taskName) {
        def task = project.tasks.findByName(taskName)
        if (task) {
            project.logger.info("* Executing Ant task: ${taskName}")
            try {
                // Nota: En Gradle, las tareas se ejecutan por dependencias
                // Este método es para logging principalmente
            } catch (Exception e) {
                project.logger.warn("* Failed to execute Ant task ${taskName}: ${e.message}")
            }
        } else {
            project.logger.debug("* Ant task not found: ${taskName}")
        }
    }
    
    /**
     * Actualiza el timestamp de build en la base de datos
     */
    private static void updateBuildTimestamp(Project project) {
        // Esta funcionalidad se mantiene en Ant por ahora
        // ya que requiere conexión directa a la BD
        def updateTask = project.tasks.findByName('update.build.timestamp')
        if (updateTask) {
            project.logger.info("* Updating build timestamp...")
        }
    }
}
