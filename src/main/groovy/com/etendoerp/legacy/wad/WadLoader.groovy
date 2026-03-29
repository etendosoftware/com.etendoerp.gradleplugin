package com.etendoerp.legacy.wad

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar

class WadLoader {

    static void load(Project project) {
        createWadSqlcTask(project)
        createWadCompileTask(project)
        createWadJarTask(project)
        createWadLibTask(project)
    }

    private static void createWadSqlcTask(Project project) {
        project.tasks.register('wadGenerateSqlc') {
            description = 'Generates Java code from .xsql files for WAD'
            group = 'etendo-wad'

            // Inputs
            inputs.files(project.fileTree(dir: 'src-wad/src', include: '**/*.xsql'))
            inputs.files(project.fileTree(dir: 'modules', include: '**/src-wad/**/*.xsql'))
            inputs.files(project.tasks.named('prepareConfig')).withPropertyName('config')
            if (project.file('modules_core').exists()) {
                inputs.files(project.fileTree(dir: 'modules_core', include: '**/src-wad/**/*.xsql'))
            }

            // Output directory
            def outputDir = project.file('build/etendo/wad/src-gen')
            outputs.dir(outputDir)

            // Needs core compiled only if in sources
            if (com.etendoerp.legacy.ant.AntLoader.isCoreInSources(project)) {
                dependsOn 'core.lib'
            }

            doLast {
                // Ensure output directory exists
                outputDir.mkdirs()
                
                // Define classpath for Sqlc (needs openbravo-core.jar and libs)
                def sqlcClasspath = project.files('src-core/lib/openbravo-core.jar') + 
                                    project.fileTree(dir: 'lib', include: '**/*.jar') +
                                    project.fileTree(dir: 'lib/runtime', include: '**/*.jar') +
                                    project.configurations.findByName('compileClasspath') +
                                    project.files('config') // For properties if needed on CP

                def baseConfig = project.file('config').absolutePath
                
                // Copiar archivo de configuración de log4j dummy
                def log4jConfig = project.file('build/etendo/log4j2-no-db.xml')
                if (!log4jConfig.exists()) {
                    log4jConfig.parentFile.mkdirs()
                    // Crear el archivo al vuelo si no existe (alternativa a copiar desde resources para simplificar)
                    log4jConfig.text = '''<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
    </Appenders>
    <Loggers>
        <Root level="error">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>'''
                }

                // Helper to run Sqlc
                def runSqlc = { String rootDir, String packageFilter, String searchDir ->
                   if (!new File(rootDir).exists()) return

                   project.javaexec {
                       main = 'org.openbravo.data.Sqlc'
                       classpath = sqlcClasspath
                       // Disable DB logging and use a dummy config to avoid connection errors if DB doesn't exist
                       jvmArgs = [
                           "-Djava.security.egd=file:///dev/urandom",
                           "-Dlog4j.configurationFile=${log4jConfig.absolutePath}",
                           "-Dorg.openbravo.utils.OBRebuildAppender.disabled=true"
                       ]
                       maxHeapSize = '1024m'

                       // args: propertiesFile, type, sourceDir, outputDir, packageFilter, generateXml
                       args = [
                           "${baseConfig}/Openbravo.properties",
                           ".xsql",
                           rootDir,
                           outputDir.absolutePath,
                           packageFilter,
                           "false"
                       ]
                   }
                }

                // 1. src-wad/src/org
                runSqlc('src-wad/src/org', 'null', '')

                // 2. modules_core
                runSqlc('modules_core', '*/src-wad', '')

                // 3. extra modules (if any, usually build/etendo/modules in jar mode)
                // Skipping extra modules for now unless explicitly needed, usually modules matches

                // 4. modules
                runSqlc('modules', '*/src-wad', '')
            }
        }
    }

    private static void createWadCompileTask(Project project) {
        project.tasks.register('wadCompile', JavaCompile) {
            description = 'Compiles WAD Java sources'
            group = 'etendo-wad'
            
            dependsOn 'wadGenerateSqlc'
            dependsOn 'gradleCopyConfig'

            source = project.files('src-wad/src') + 
                     project.files('build/etendo/wad/src-gen') +
                     project.fileTree(dir: 'modules', include: '**/src-wad/**/*.java')
            
            if (project.file('modules_core').exists()) {
                source += project.fileTree(dir: 'modules_core', include: '**/src-wad/**/*.java')
            }

            destinationDir = project.file('build/etendo/wad/classes')

            classpath = project.files('src-core/lib/openbravo-core.jar') + 
                        project.fileTree(dir: 'lib', include: '**/*.jar') +
                        project.fileTree(dir: 'lib/runtime', include: '**/*.jar') +
                        project.configurations.findByName('compileClasspath')
            
            options.encoding = 'UTF-8'
            options.debug = true
        }
    }

    private static void createWadJarTask(Project project) {
        project.tasks.register('wadJar', Jar) {
            description = 'Creates openbravo-wad.jar'
            group = 'etendo-wad'
            
            dependsOn 'wadCompile'
            dependsOn 'gradleCopyConfig'
            
            // Detect mode
            def coreInSources = com.etendoerp.legacy.ant.AntLoader.isCoreInSources(project)
            def corePath = coreInSources ? "." : "build/etendo"

            archiveFileName = 'openbravo-wad.jar'
            destinationDirectory = project.file("${corePath}/src-wad/lib")

            // Reproducible JAR
            preserveFileTimestamps = false
            reproducibleFileOrder = true

            from project.file('build/etendo/wad/classes')
            
            // Also copy resources from src-wad/src (xml, html)
            from('src-wad/src') {
                include '**/*.xml'
                include '**/*.html'
            }

            // Copy resources from modules src-wad
            // Logic from build.xml:
            // <fileset dir="${base.modules}">
            //    <include name="*/src-wad/**/*" />
            //    <exclude name="*/src-wad/**/*.java" />
            //    <exclude name="*/src-wad/**/*.xsql" />
            // </fileset>
            // mapper type="regexp" from="(.*\${file.separator}src-wad)(.*)" to="\2"

            // Simplified: copy and structure manually if needed, or iterate
            // The mapping logic removes 'module/src-wad' prefix, effectively flattening or preserving relative package structure?
            // "from (.*src-wad)(.*) to \2" keeps the path AFTER src-wad.
            // e.g. modules/MyMod/src-wad/org/foo/Bar.xml -> org/foo/Bar.xml

            // Implementing copying resources with structure preservation
            doFirst {
                // We use a copy spec in doFirst to handle complex mapping if needed, 
                // but 'from' with closure inside Jar task is better.
            }
            
            // Using a closure to add resources dynamically
            project.file('modules').listFiles()?.each { moduleDir ->
               if (new File(moduleDir, 'src-wad').exists()) {
                   from(new File(moduleDir, 'src-wad')) {
                       include '**/*'
                       exclude '**/*.java'
                       exclude '**/*.xsql'
                   }
               }
            }
            
            if (project.file('modules_core').exists()) {
                project.file('modules_core').listFiles()?.each { moduleDir ->
                   if (new File(moduleDir, 'src-wad').exists()) {
                       from(new File(moduleDir, 'src-wad')) {
                           include '**/*'
                           exclude '**/*.java'
                           exclude '**/*.xsql'
                       }
                   }
                }
            }
        }
    }

    private static void createWadLibTask(Project project) {
        project.tasks.register('gradleWadLib') {
            description = 'Main WAD task (replaces wad.lib)'
            group = 'etendo-wad'
            dependsOn 'wadJar'
        }
    }
}
