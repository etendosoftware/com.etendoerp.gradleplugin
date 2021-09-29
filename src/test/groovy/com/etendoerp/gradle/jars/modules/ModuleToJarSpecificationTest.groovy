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

    String[] getFilesFromModule(def map=[:]) {

        // Arguments
        def module              = map.module              ?: ""
        def fileExtension       = map.fileExtension       ?: ""
        def locationDir         = map.locationDir         ?: ""
        def replacePathLocation = map.replacePathLocation ?: true
        def ignoreDir           = map.ignoreDir           ?: true
        def pathToIgnore        = map.pathToIgnore        ?: ""

        def files = []
        def baseLocation = "${getProjectDir().absolutePath}/modules/$module/"

        if (locationDir) {
            baseLocation += "$locationDir/"
        }

        def moduleFilesLocation = new File(baseLocation)

        moduleFilesLocation.eachFileRecurse {
            if (ignoreDir && it.isDirectory()) {
                return
            }

            if (pathToIgnore && it.absolutePath.contains(pathToIgnore as String)) {
                return
            }

            // Ignore build directory
            if (it.absolutePath.contains("${baseLocation}build/")) {
                return
            }

            if (it.name.endsWith(fileExtension as String)) {
                def fileLocation = it.absolutePath
                if (replacePathLocation) {
                    fileLocation = fileLocation.replace(baseLocation,"")
                }
                files.add(fileLocation)
            }
        }
        return files
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
