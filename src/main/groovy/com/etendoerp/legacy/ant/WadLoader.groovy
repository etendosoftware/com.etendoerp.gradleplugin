package com.etendoerp.legacy.ant

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

/**
 * WadLoader - Migración de la tarea Ant 'wad.lib' a Gradle
 * 
 * Genera el JAR openbravo-wad.jar que contiene las clases de WAD (Web Application Definition)
 * 
 * Flujo:
 * 1. wadGenerateSqlc: Genera código Java desde src-wad/src/*.xsql
 * 2. wadCompile: Compila el código generado
 * 3. wadJar: Empaqueta en openbravo-wad.jar
 * 4. gradleWadLib: Tarea agregada
 */
class WadLoader {
    
    static final String WAD_OUTPUT_DIR = 'build/javasqlc/wad/src'
    static final String WAD_CLASSES_DIR = 'build/classes/wad'
    static final String WAD_JAR_NAME = 'openbravo-wad'
    
    static final String SQLC_CLASS = 'org.openbravo.data.Sqlc'
    
    static void load(Project project) {
        
        /**
         * Task: wadGenerateSqlc
         * Genera código Java desde archivos .xsql en src-wad/src
         */
        project.tasks.register('wadGenerateSqlc', JavaExec) {
            group = 'etendo build'
            description = 'Generate SQLC code for WAD library'
            
            mainClass.set(SQLC_CLASS)
            classpath = project.configurations.runtimeClasspath
            maxHeapSize = '1024m'
            
            doFirst {
                def coreInSources = AntLoader.isCoreInSources(project)
                def wadSrcDir = coreInSources 
                    ? "${project.projectDir}/src-wad/src" 
                    : "${project.buildDir}/etendo/src-wad/src"
                
                // Crear directorio de salida
                project.file(WAD_OUTPUT_DIR).mkdirs()
                
                args = [
                    wadSrcDir,
                    project.file(WAD_OUTPUT_DIR).absolutePath,
                    project.file(WAD_OUTPUT_DIR).absolutePath,  // mismo destino para AD
                    '**/*.xsql',
                    'false'
                ]
                
                project.logger.info("* Generating WAD SQLC from ${wadSrcDir}")
                project.logger.info("  Output: ${WAD_OUTPUT_DIR}")
            }
            
            // Copiar archivos Java estáticos que no son .xsql
            doLast {
                def coreInSources = AntLoader.isCoreInSources(project)
                def wadSrcDir = coreInSources 
                    ? project.file("${project.projectDir}/src-wad/src")
                    : project.file("${project.buildDir}/etendo/src-wad/src")
                
                if (wadSrcDir.exists()) {
                    project.copy {
                        from wadSrcDir
                        into project.file(WAD_OUTPUT_DIR)
                        include '**/*.java'
                    }
                }
            }
        }
        
        /**
         * Task: wadCompile
         * Compila el código WAD generado
         */
        project.tasks.register('wadCompile', JavaCompile) {
            group = 'etendo build'
            description = 'Compile WAD generated sources'
            dependsOn 'wadGenerateSqlc'
            
            source = project.fileTree(WAD_OUTPUT_DIR) {
                include '**/*.java'
            }
            
            destinationDirectory.set(project.file(WAD_CLASSES_DIR))
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
                project.file(WAD_CLASSES_DIR).mkdirs()
                project.logger.info("* Compiling WAD sources from ${WAD_OUTPUT_DIR}")
            }
        }
        
        /**
         * Task: wadJar
         * Genera el JAR openbravo-wad.jar
         */
        project.tasks.register('wadJar', Jar) {
            group = 'etendo build'
            description = 'Create openbravo-wad.jar'
            dependsOn 'wadCompile'
            
            archiveBaseName.set(WAD_JAR_NAME)
            archiveVersion.set('')  // Sin versión en el nombre
            
            from project.file(WAD_CLASSES_DIR)
            destinationDirectory.set(project.file("${project.buildDir}/lib"))
            
            doFirst {
                project.file("${project.buildDir}/lib").mkdirs()
                project.logger.info("* Creating ${WAD_JAR_NAME}.jar")
            }
        }
        
        /**
         * Task agregada: etendoWadLib
         * Equivalente a wad.lib de Ant
         */
        project.tasks.register('etendoWadLib') {
            group = 'etendo build'
            description = 'Generate complete WAD library (equivalent to ant wad.lib)'
            dependsOn 'wadJar'
            
            doLast {
                def jarFile = project.file("${project.buildDir}/lib/${WAD_JAR_NAME}.jar")
                if (jarFile.exists()) {
                    project.logger.lifecycle("* WAD library generated: ${jarFile.absolutePath}")
                } else {
                    project.logger.error("* WAD library generation failed!")
                }
            }
        }
    }
}
