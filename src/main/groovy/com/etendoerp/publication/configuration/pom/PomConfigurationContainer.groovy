package com.etendoerp.publication.configuration.pom

import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.PathUtils
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.buildfile.BuildMetadata
import com.etendoerp.publication.configuration.PublicationConfiguration
import com.etendoerp.publication.configuration.VersionContainer
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.GenerateMavenPom

import org.jdom2.*
import org.jdom2.input.*
import org.jdom2.xpath.*
import org.jdom2.output.*
import java.util.regex.Pattern

class PomConfigurationContainer {

    /**
     * Name of the configuration used to add dependencies of another subprojects.
     * Can be used in the generated build.gradle by the users to specify dependencies between subprojects (modules).
     */
    static final String SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER = "subprojectDependenciesContainer"

    /**
     * Property used to store the class in the subproject
     */
    static final String POM_CONTAINER_PROPERTY = "pomContainerProperty"

    /**
     * Property used to verify if the files are already parsed (AD_MODULE.xml, build.gradle)
     */
    static final String PARSED_FILES_FLAG = "PARSED_FILES_FLAG"

    Project mainProject
    Project subProject
    PomConfigurationType type
    GenerateMavenPom pomTask
    Map<String, PomProjectContainer> subprojectDependencies
    VersionContainer versionContainer

    /**
     * Temporary dir used to store the parsed files
     */
    File temporaryDir

    PomConfigurationContainer(Project mainProject, Project subProject, PomConfigurationType type) {
        this.mainProject = mainProject
        this.subProject = subProject
        this.type = type
        this.subProject.ext.set(POM_CONTAINER_PROPERTY, this)
        this.subprojectDependencies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        this.versionContainer = new VersionContainer(mainProject, subProject)
        load()
    }

    PomConfigurationContainer(Project mainProject, Project subProject, PomConfigurationType type, VersionContainer versionContainer) {
        this(mainProject, subProject, type)
        this.versionContainer = versionContainer
    }

    void putSubproject(PomProjectContainer pomProjectContainer) {
        Project project = pomProjectContainer.projectDependency
        String name = "${project.group}.${project.artifact}"
        this.subprojectDependencies.put(name, pomProjectContainer)
    }

    PomProjectContainer getSubproject(String name) {
        return this.subprojectDependencies.get(name)
    }

    /**
     * Obtains the PomConfigurationContainer from the project or creates a new one.
     * @param mainProject
     * @param subProject
     * @param pomType
     * @return
     */
    static PomConfigurationContainer getPomContainer(Project mainProject, Project subProject, PomConfigurationType pomType=PomConfigurationType.MULTIPLE_PUBLISH) {
        PomConfigurationContainer pom = subProject.findProperty(POM_CONTAINER_PROPERTY) as PomConfigurationContainer
        if (!pom) {
            mainProject.logger.info("* The POM container is not declared. Creating a new instance for ${subProject}")
            pom = new PomConfigurationContainer(mainProject, subProject, pomType)
        }
        return pom
    }

    void load() {
        // Create the configuration used to store subproject dependencies
        if (!this.subProject.configurations.findByName(type.internalConfigurationProperty)) {
            this.subProject.configurations.create(type.internalConfigurationProperty)
        }
    }

    void configurePomMultiplePublish() {
        def pom = this.pomTask.pom
        def configuration = this.subProject.configurations.findByName(this.type.internalConfigurationProperty)
        PomConfigurationUtils.configurePomDependencies(this.subProject, configuration, pom)
    }

    void configurePom() {
        if (!this.pomTask) {
            loadPomTask()
        }
        if (this.type == PomConfigurationType.MULTIPLE_PUBLISH) {
            configurePomMultiplePublish()
        }
    }

    void loadPomTask() {
        String subprojectName = this.subProject.projectDir.name
        def moduleCapitalize = PublicationUtils.capitalizeModule(subprojectName)
        def pomTaskName = "generatePomFileFor${moduleCapitalize}Publication"
        def pomTask = this.subProject.tasks.findByName(pomTaskName)
        if (!pomTask) {
            throw new IllegalArgumentException("The pom task for the project '${subProject}' is not defined.")
        }
        this.pomTask = pomTask as GenerateMavenPom
    }

    File parseProjectFiles(File temporaryDir=null) {
        this.temporaryDir = temporaryDir
        if (!temporaryDir) {
            this.temporaryDir = new File(this.mainProject.buildDir, "tmp" + File.separator + "${this.subProject.name}-parser")
            this.temporaryDir.mkdirs()
        }

        this.subProject.ext.set(PARSED_FILES_FLAG, true)

        // Parse AD_MODULE
        parseAdModuleXML(this.temporaryDir)

        // Parse build.gradle
        parseBuildGradleFile(this.temporaryDir)

        return this.temporaryDir
    }

    void parseBuildGradleFile(File temporaryDir) {
        String projectLocationPath = this.subProject.projectDir.absolutePath

        // Get the 'build.gradle' file
        String buildGradleLocation = "build.gradle"

        File originalBuildGradle = new File(projectLocationPath, buildGradleLocation)

        if (originalBuildGradle && originalBuildGradle.exists()) {
            this.mainProject.copy {
                includeEmptyDirs = false
                from(originalBuildGradle)
                into(temporaryDir)
            }
        }

        File newBuildGradleLocationFile = new File(this.temporaryDir.absolutePath, buildGradleLocation)
        if (newBuildGradleLocationFile && newBuildGradleLocationFile.exists()) {
            this.updateBuildGradle(newBuildGradleLocationFile)
        }
    }

    void updateBuildGradle(File buildGradleFile, Map<String, Object> properties = null) {
        String buildGradleText = buildGradleFile.text
        String auxBuildFile = ""

        boolean versionUpdated = false
        boolean dependenciesBlock = false

        boolean emptySubprojectDependencies = this.subprojectDependencies.isEmpty()

        buildGradleText.eachLine {
            if (!versionUpdated && isVersionLine(it)) {
                // replace version
                it = it.replace(this.versionContainer.oldVersion, this.subProject.version as String)
                versionUpdated = true
            }

            // Update dependencies
            if (it.startsWith(BuildMetadata.DEPENDENCIES)) {
                dependenciesBlock = true
            }

            // Check if the line contains a dependency to a subproject
            if (dependenciesBlock && !emptySubprojectDependencies) {
                it = containsSubprojectDependency(it)
            }

            auxBuildFile += "${it}\n"
        }

        buildGradleFile.text = auxBuildFile
    }

    static boolean isVersionLine(String line) {
        Pattern pattern = Pattern.compile("(^version)[\\s]*=(.)*")
        return line.replaceAll("\\s","").startsWith("${BuildMetadata.VERSION}=") || pattern.matcher(line).matches()
    }

    String containsSubprojectDependency(String line) {
        PomProjectContainer pomProjectContainer

        this.subprojectDependencies.entrySet().stream().filter({
            Project project = it.value.projectDependency
            return line.contains(project.group as String) && line.contains(project.findProperty(BuildMetadata.ARTIFACT) as String)
        }).map({
            it.value
        }).collect({
            pomProjectContainer = it
        })

        // The line contains a subproject dependency
        if (pomProjectContainer) {
            String projectGradleVersion = pomProjectContainer.buildGradleVersion
            Project projectDependency = pomProjectContainer.projectDependency

            // Replace the configuration name and the version
            if (projectGradleVersion) {
                String oldVersion = projectGradleVersion
                String newVersion = projectDependency.version
                line = line.replace(oldVersion, newVersion)
                line = line.replace(SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER, DependencyUtils.IMPLEMENTATION)
            }
        }
        return line
    }

    void parseAdModuleXML(File temporaryDir) {
        String projectLocationPath = this.subProject.projectDir.absolutePath

        // Get AD_MODULE.xml file
        String XMLLocationPath = PathUtils.createPath(
                EtendoArtifactMetadata.SRC_DB,
                EtendoArtifactMetadata.DATABASE,
                EtendoArtifactMetadata.SOURCEDATA
        ).concat(EtendoArtifactMetadata.AD_MODULE).concat(FileExtensions.XML)

        File originalXMLLocationFile = new File(projectLocationPath, XMLLocationPath)

        if (originalXMLLocationFile && originalXMLLocationFile.exists()) {
            this.mainProject.copy {
                includeEmptyDirs = false
                from(projectLocationPath) {
                    exclude ("build/**")
                    include ("**/${XMLLocationPath}")
                }
                into(temporaryDir)
            }
        }

        File newXMLLocationFile = new File(temporaryDir.absolutePath, XMLLocationPath)

        if (newXMLLocationFile && newXMLLocationFile.exists()) {
            updateAdModuleXML(this.subProject, newXMLLocationFile)
        }
    }

    static void updateAdModuleXML(Project project, File adModuleFile, Map<String, Object> properties = null) {
        Document doc = new SAXBuilder().build(new StringReader(adModuleFile.text))
        def versionList = XPathFactory.instance().compile('//VERSION').evaluate(doc)
        Element versionElement = versionList.get(0) as Element

        def cdata = new CDATA(project.version as String)
        versionElement.setContent(cdata)

        def fos = new FileOutputStream(adModuleFile)

        new XMLOutputter().with {
            format = Format.getRawFormat()
            format.setLineSeparator(LineSeparator.NONE)
            output(doc, fos)
        }
    }


}
