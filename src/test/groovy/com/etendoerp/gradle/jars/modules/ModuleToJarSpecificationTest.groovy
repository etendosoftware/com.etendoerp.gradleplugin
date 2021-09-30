package com.etendoerp.gradle.jars.modules

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.internal.impldep.org.apache.ivy.util.FileUtil
import org.gradle.testkit.runner.BuildResult
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.TempDir
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files
import java.util.zip.ZipFile

abstract class ModuleToJarSpecificationTest extends EtendoSpecification {

    final static String BASE_JAR_LOCATION     = "src/test/resources/jars"
    final static String ENVIRONMENTS_LOCATION = "src/test/resources/jars/environments"

    def setup() {
        def ant = new AntBuilder()

        def baseDir = new File("${ENVIRONMENTS_LOCATION}/moduleToJarEnvironment")
        FileUtils.copyDirectory(baseDir, getProjectDir())

        //Override the default build file
        def buildJarFile = new File("${BASE_JAR_LOCATION}/build.xml")
        ant.copy(file: buildJarFile, todir: getProjectDir(), overwrite: true)
    }

    String moduleToPath(String module) {
        return module.replace(".","/")
    }

    /**
     * Get the files from the generated file.
     * Each file is converted to a path format.
     * Ex: com.test.CustomClass -> com/test/CustomClass.class
     * @param generated
     * @return
     */
    String[] getFilesFromGenerated(File generated) {
        def files = []
        generated.eachLine {
            files.add("${moduleToPath(it)}.class")
        }
        return files
    }

    String[] getFilesFromLocation(def map=[:]) {

        // Arguments
        def location            = map.location            ?: ""
        def fileExtension       = map.fileExtension       ?: ""
        def locationDir         = map.locationDir         ?: ""
        def replacePathLocation = map.replacePathLocation ?: true
        def ignoreDir           = map.ignoreDir           ?: true
        def pathToIgnore        = map.pathToIgnore        ?: ""
        def ignoreBuildDir      = map.ignoreBuildDir      ?: true

        def files = []
        def baseLocation = location

        if (locationDir) {
            baseLocation += "$locationDir/"
        }

        String pathToReplace = map.pathToReplace ?: baseLocation

        def filesLocation = new File(baseLocation as String)

        filesLocation.eachFileRecurse {
            if (ignoreDir && it.isDirectory()) {
                return
            }

            if (pathToIgnore && it.absolutePath.contains(pathToIgnore as String)) {
                return
            }

            // Ignore build directory
            if (ignoreBuildDir && it.absolutePath.contains("${baseLocation}build/")) {
                return
            }

            if (it.name.endsWith(fileExtension as String)) {
                def fileLocation = it.absolutePath
                if (replacePathLocation) {
                    fileLocation = fileLocation.replace(pathToReplace,"")
                }
                files.add(fileLocation)
            }
        }
        return files
    }

    String[] getFilesFromModule(def map=[:]) {
        def module = map.module
        map.location = "${getProjectDir().absolutePath}/modules/$module/"
        getFilesFromLocation(map)
    }

    String[] getFilesFromJar(def map = [:]) {

        File jarFile         = map.jarFile
        String fileExtension = map.fileExtension ?: ""
        String pathToIgnore  = map.pathToIgnore  ?: ""
        String pathToSearch  = map.pathToSearch  ?: ""
        Boolean ignoreDir    = map.ignoreDir     ?: true


        def files = []
        new ZipFile(jarFile).entries().each {
            // continue
            if (ignoreDir && it.isDirectory()) {
                return
            }

            if (pathToSearch && !it.name.contains(pathToSearch)) {
                return
            }

            if (pathToIgnore && it.name.contains(pathToIgnore)) {
                return
            }

            if (it.name.endsWith(fileExtension)) {
                files.add(it.name)
            }
        }
        return files
    }

}
