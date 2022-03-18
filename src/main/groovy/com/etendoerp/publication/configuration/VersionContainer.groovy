package com.etendoerp.publication.configuration

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublication
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication

class VersionContainer {
    static final String VERSION_CONTAINER_PROPERTY = "VERSION_CONTAINER_PROPERTY"
    static final String VERSION_COMMANDLINE_PROPERTY = "version"

    static final String MAYOR_VERSION = "mayor"
    static final String MINOR_VERSION = "minor"
    static final String PATCH_VERSION = "patch"

    static final List<String> VERSION_TYPES = [
            MAYOR_VERSION,
            MINOR_VERSION,
            PATCH_VERSION
    ]

    static int MIN_MAYOR_VERSION = 0
    static int INC_MAYOR_VERSION = 1

    static int MIN_MINOR_VERSION = 0
    static int INC_MINOR_VERSION = 1

    static int MIN_PATCH_VERSION = 0
    static int INC_PATCH_VERSION = 1

    Project mainProject
    Project subProject
    String oldVersion
    String version

    boolean versionUpdated = false

    int mayor
    int minor
    int patch

    // Default version to upgrade
    String versionToUpgrade = PATCH_VERSION

    VersionContainer(Project mainProject, Project subProject) {
        this(mainProject, subProject, subProject.version as String)
    }

    VersionContainer(Project mainProject, Project subProject, String version) {
        this.mainProject = mainProject
        this.subProject = subProject
        this.oldVersion = version
        this.version = version
        this.subProject.ext.set(VERSION_CONTAINER_PROPERTY, this)
        processVersion()
    }

    void processVersion() {
        def splitVersion = version.split("\\.")
        // TODO: Improvement - Check restrictions on the version
        if (splitVersion.size() >= 3) {
            this.mayor = validateVersion(splitVersion[0], MAYOR_VERSION)
            this.minor = validateVersion(splitVersion[1], MINOR_VERSION)
            this.patch = validateVersion(parsePatchVersion(splitVersion[2]), PATCH_VERSION)
        }
    }

    String getVersion() {
        this.version = "${this.mayor}.${this.minor}.${this.patch}"
        return this.version
    }

    /**
     * TODO: Improvement - Check if the patch version is only numbers
     * @param version
     * @return
     */
    static String parsePatchVersion(String version) {
        return version
    }

    String loadVersionTypeToUpgrade() {
        String defaultVersionType = PATCH_VERSION
        String userVersionType = this.mainProject.findProperty(VERSION_COMMANDLINE_PROPERTY)

        if (userVersionType && VERSION_TYPES.contains(userVersionType)) {
            defaultVersionType = userVersionType
        }

        return defaultVersionType
    }

    String upgradeVersion() {
        def currentVersion = this.getVersion()
        def versionTypeToUpgrade = this.loadVersionTypeToUpgrade()

        switch (versionTypeToUpgrade) {
            case MAYOR_VERSION:
                this.upgradeMayorVersion()
                break
            case MINOR_VERSION:
                this.upgradeMinorVersion()
                break
            case PATCH_VERSION:
                this.upgradePatchVersion()
                break
            default:
                mainProject.logger.info("Specified version to upgrade not found.")
        }
        def updatedVersion = this.getVersion()

        if (currentVersion != updatedVersion) {
            this.oldVersion = currentVersion
        }

        // Update publication version
        this.subProject.version = updatedVersion
        updatePublicationVersion(this.subProject, updatedVersion)
        this.versionUpdated = true

        return updatedVersion
    }

    static void updatePublicationVersion(Project subProject, String version) {
        if (subProject instanceof DefaultProject) {
            DefaultProject defaultProject = subProject as DefaultProject
            def subprojectName = "${subProject.projectDir.name}"
            ProjectPublicationRegistry registry = defaultProject.services.get(ProjectPublicationRegistry)
            def publications = registry.getPublications(ProjectPublication, defaultProject.getIdentityPath())

            publications.stream().filter({
                it instanceof DefaultMavenPublication
            }).filter({
                DefaultMavenPublication mavenPublication = it as DefaultMavenPublication
                def mavenPublicationName = "${mavenPublication.name}"
                return mavenPublicationName.equalsIgnoreCase(subprojectName)
            }).forEach({
                DefaultMavenPublication mavenPublication = it as DefaultMavenPublication
                mavenPublication.setVersion(version)
            })
        }
    }

    void upgradeMayorVersion() {
        this.mayor = this.mayor + INC_MAYOR_VERSION
        this.minor = MIN_MINOR_VERSION
        this.patch = MIN_PATCH_VERSION
    }

    void upgradeMinorVersion() {
        this.minor = this.minor + INC_MINOR_VERSION
        this.patch = MIN_PATCH_VERSION
    }

    void upgradePatchVersion() {
        this.patch = this.patch + INC_PATCH_VERSION
    }

    int validateVersion(String version, String versionType) {
        if (!version.isInteger()) {
            throw new IllegalArgumentException("The '${versionType}' version '${version}' could not be converted to integer to be upgraded. Location: ${this.project.projectDir}")
        }
        return version as Integer
    }

}
