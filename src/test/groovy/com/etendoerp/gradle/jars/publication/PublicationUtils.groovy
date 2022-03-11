package com.etendoerp.gradle.jars.publication

import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import groovy.json.JsonSlurper

import java.util.zip.ZipFile

class PublicationUtils {

    static List<String> VALID_REPOSITORIES_TO_CLEAN = [
            "etendo-multiplepublish-test"
    ]

    static void cleanRepositoryModules(String repository) {
        if (!(repository in VALID_REPOSITORIES_TO_CLEAN)) {
            throw new IllegalArgumentException("The repository '${repository}' is not in the valid repositories list.")
        }
        HttpURLConnection uc
        try {
            def obj = getRepositoryModules(repository)
            for (def module :obj.items ){
                URL url2 = new URL("https://repo.futit.cloud/service/rest/v1/components/${module.id}")
                uc = url2.openConnection() as HttpURLConnection
                uc.setRequestMethod("DELETE")
                uc.setRequestProperty("Authorization", generateBasicAuth());
                uc.getInputStream()
            }
        } catch (Exception e ) {
            print(e)
        }
    }

    static Object getRepositoryModules(String repository) {
        Object response = null
        HttpURLConnection uc
        try {
            URL url = new URL( "https://repo.futit.cloud/service/rest/v1/components?repository=${repository}")
            uc = url.openConnection() as HttpURLConnection
            uc.setRequestMethod("GET")
            String basicAuth = generateBasicAuth()
            uc.setRequestProperty("Authorization", basicAuth);
            def responseText = uc.getInputStream().getText()
            def jsonSlurper = new JsonSlurper()
            response = jsonSlurper.parseText(responseText)
        } catch (Exception e ) {
            print(e)
        }
        return response
    }

    static String generateBasicAuth() {
        String userPass = System.getProperty("nexusUser") + ":" + System.getProperty("nexusPassword")
        return  "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()));
    }

    static File download(String address, File parent, String filename) {
        File dataFile = new File(parent, filename)

        def url = new URL(address)
        def uc = url.openConnection()
        String basicAuth = generateBasicAuth()
        uc.setRequestProperty("Authorization", basicAuth);
        dataFile.withOutputStream { out ->
            out << uc.getInputStream().readAllBytes()
        }
        return dataFile
    }

    static void correctModuleVersion(String repo, Map<String, List<String>> modulesData) {
        def modules = getRepositoryModules(repo)
        for (def module : modules.items) {
            String moduleName = "${module.group}.${module.name}"
            if (modulesData.containsKey(moduleName)) {
                assert module.version in modulesData.get(moduleName)
            }
        }
    }

    static void downloadAndValidatePomFile(String address, File parent, String filename, String moduleVersion, Map<String , Map> dependencies) {
        def pomFile = download(address, parent, filename)
        validatePomFile(pomFile, moduleVersion, dependencies)
    }

    static void validatePomFile(File pomFile, String moduleVersion, Map<String , Map> dependencies) {
        String pomFileText = pomFile.text
        def project = new XmlParser().parseText(pomFileText)
        assert project.version.text() == moduleVersion

        def xmlDependencies = project.dependencies

        Map<String, Object> pomDependenciesMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

        // Load the pom dependencies map from the POM xml
        xmlDependencies.dependency.each { dep ->
            def group = dep.groupId.text()
            def artifact = dep.artifactId.text()
            def version = dep.version.text()
            def map = [
                    "group" : group,
                    "artifact" : artifact,
                    "version" : version
            ]

            String name = "${group}.${artifact}"
            pomDependenciesMap.put(name, map)
        }

        dependencies.entrySet().each {
            String depName = it.key
            Map depValues = it.value
            assert pomDependenciesMap.containsKey(depName)
            def pomValues = pomDependenciesMap.get(depName)
            assert depValues.group == pomValues.group
            assert depValues.artifact == pomValues.artifact
            assert depValues.version == pomValues.version
        }
    }

    static void downloadAndValidateZipFile(String address, File parent, String filename, String moduleVersion, Map<String , Map> dependencies) {
        def zipFile = download(address, parent, filename)

        File buildFile = obtainFileFromZipType(zipFile, "build.gradle", parent, "tempzip")
        validateBuildGradleFile(buildFile, moduleVersion, dependencies)

        File adModuleFile = obtainFileFromZipType(zipFile, "AD_MODULE.xml", parent, "tempzip")
        validateAdModuleFile(adModuleFile, moduleVersion)
    }

    /**
     * Only has to validate the 'AD_MODULE.xml' file
     * @param address
     * @param parent
     * @param filename
     * @param moduleVersion
     */
    static void downloadAndValidateJarFile(String address, File parent, String filename, String moduleVersion) {
        def jarFile = download(address, parent, filename)
        File adModuleFile = obtainFileFromZipType(jarFile, "AD_MODULE.xml", parent, "tempjar")
        validateAdModuleFile(adModuleFile, moduleVersion)
    }

    static File obtainFileFromZipType(File zipTypeFile, String filePath, File outputFile, String destine) {
        File tempDir = new File("${outputFile.absolutePath}/${destine}")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        File data = new File(tempDir, filePath)
        if (!data.exists()) {
            data.createNewFile()
        }

        def zipFile = new ZipFile(zipTypeFile)

        for (def entry : zipFile.entries()) {
            if (!entry.isDirectory() && entry.name.contains(filePath)) {
                data.text = zipFile.getInputStream(entry).text
                break
            }
        }
        return data
    }

    static void validateBuildGradleFile(File buildGradleFile, String moduleVersion, Map<String , Map> dependencies) {
        String buildFileText = buildGradleFile.text

        // Validate version
        for (String line : buildFileText.lines()) {
            if (PomConfigurationContainer.isVersionLine(line)) {
                assert line.contains(moduleVersion)
                break
            }
        }

        // Validate dependencies
        dependencies.entrySet().each {
            String dep = "${it.value.group}:${it.value.artifact}:${it.value.version}"
            assert buildFileText.contains(dep)
        }
    }

    static void validateAdModuleFile(File adModuleFile, String moduleVersion) {
        String adModuleText = adModuleFile.text
        def data = new XmlParser().parseText(adModuleText)
        assert data.AD_MODULE.VERSION.text() == moduleVersion
    }

}
