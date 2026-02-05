package com.etendoerp.legacy.ant

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

/**
 * TrlLoader - Migración de la tarea Ant 'trl.lib' a Gradle
 * 
 * Genera el JAR openbravo-trl.jar que contiene las clases de traducción
 * 
 * Flujo:
 * 1. gradleTrlSqlc: Genera código Java desde src-trl/*.xsql
 * 2. trlCompile: Compila el código generado
 * 3. gradleTrlJar: Empaqueta en openbravo-trl.jar
 */
class TrlLoader {
    
    static final String TRL_OUTPUT_DIR = 'build/javasqlc/trl/src'
    static final String TRL_CLASSES_DIR = 'build/classes/trl'
    static final String TRL_JAR_NAME = 'openbravo-trl'
    
    static final String SQLC_CLASS = 'org.openbravo.data.Sqlc'
    
    static void load(Project project) {
        
        /**
         * Task: etendoTrlSqlc
         * Genera código Java desde archivos .xsql en src-trl
         */
        project.tasks.register('etendoTrlSqlc', JavaExec) {
            group = 'etendo build'
            description = 'Generate SQLC code for TRL library'
            
            mainClass.set(SQLC_CLASS)
            classpath = project.configurations.runtimeClasspath
            maxHeapSize = '1024m'
            
            doFirst {
                def coreInSources = AntLoader.isCoreInSources(project)
                def trlSrcDir = coreInSources 
                    ? "${project.projectDir}/src-trl" 
                    : "${project.buildDir}/etendo/src-trl"
                
                // Verificar que existe el directorio fuente
                def srcDir = project.file(trlSrcDir)
                if (!srcDir.exists()) {
                    project.logger.warn("* TRL source directory not found: ${trlSrcDir}")
                    throw new org.gradle.api.GradleException("TRL source directory not found: ${trlSrcDir}")
                }
                
                // Crear directorio de salida
                project.file(TRL_OUTPUT_DIR).mkdirs()
                
                args = [
                    trlSrcDir,
                    project.file(TRL_OUTPUT_DIR).absolutePath,
                    project.file(TRL_OUTPUT_DIR).absolutePath,  // mismo destino para AD
                    '**/*.xsql',
                    'false'
                ]
                
                project.logger.info("* Generating TRL SQLC from ${trlSrcDir}")
                project.logger.info("  Output: ${TRL_OUTPUT_DIR}")
            }
            
            // Copiar archivos Java estáticos que no son .xsql
            doLast {
                def coreInSources = AntLoader.isCoreInSources(project)
                def trlSrcDir = coreInSources 
                    ? project.file("${project.projectDir}/src-trl")
                    : project.file("${project.buildDir}/etendo/src-trl")
                
                if (trlSrcDir.exists()) {
                    project.copy {
                        from trlSrcDir
                        into project.file(TRL_OUTPUT_DIR)
                        include '**/*.java'
                    }
                }
            }
        }
        
        /**
         * Task: trlCompile
         * Compila el código TRL generado
         */
        project.tasks.register('trlCompile', JavaCompile) {
            group = 'etendo build'
            description = 'Compile TRL generated sources'
            dependsOn 'etendoTrlSqlc'
            
            source = project.fileTree(TRL_OUTPUT_DIR) {
                include '**/*.java'
            }
            
            destinationDirectory.set(project.file(TRL_CLASSES_DIR))
            classpath = project.configurations.runtimeClasspath
            
            sourceCompatibility = project.findProperty('sourceCompatibility') ?: '17'
            targetCompatibility = project.findProperty('targetCompatibility') ?: '17'
            
            options.compilerArgs = [
                '-Xlint:-deprecation',
                '-Xlint:-unchecked',
                '-Xlint:-serial'
            ]
            options.encoding = 'UTF-8'
            options.fork = true
            
            doFirst {
                project.file(TRL_CLASSES_DIR).mkdirs()
                project.logger.info("* Compiling TRL sources from ${TRL_OUTPUT_DIR}")
            }
        }
        
        /**
         * Task: etendoTrlJar
         * Genera el JAR openbravo-trl.jar
         * Equivalente a trl.lib de Ant
         */
        project.tasks.register('etendoTrlJar', Jar) {
            group = 'etendo build'
            description = 'Create openbravo-trl.jar (equivalent to ant trl.lib)'
            dependsOn 'trlCompile'
            
            archiveBaseName.set(TRL_JAR_NAME)
            archiveVersion.set('')  // Sin versión en el nombre
            
            from project.file(TRL_CLASSES_DIR)
            destinationDirectory.set(project.file("${project.buildDir}/lib"))
            
            doFirst {
                project.file("${project.buildDir}/lib").mkdirs()
                project.logger.info("* Creating ${TRL_JAR_NAME}.jar")
            }
            
            doLast {
                def jarFile = project.file("${project.buildDir}/lib/${TRL_JAR_NAME}.jar")
                if (jarFile.exists()) {
                    project.logger.lifecycle("* TRL library generated: ${jarFile.absolutePath}")
                } else {
                    project.logger.error("* TRL library generation failed!")
                }
            }
        }
    }
}
