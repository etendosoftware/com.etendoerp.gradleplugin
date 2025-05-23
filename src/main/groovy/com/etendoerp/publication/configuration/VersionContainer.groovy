package com.etendoerp.publication.configuration

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

class VersionContainer {
    static final String VERSION_CONTAINER_PROPERTY = "VERSION_CONTAINER_PROPERTY"
    static final String VERSION_COMMANDLINE_PROPERTY = "update"

    static final String MAJOR_VERSION = "major"
    static final String MINOR_VERSION = "minor"
    static final String PATCH_VERSION = "patch"

    static final List<String> VERSION_TYPES = [
            MAJOR_VERSION,
            MINOR_VERSION,
            PATCH_VERSION
    ]

    static int MIN_MAJOR_VERSION = 0
    static int INC_MAJOR_VERSION = 1

    static int MIN_MINOR_VERSION = 0
    static int INC_MINOR_VERSION = 1

    static int MIN_PATCH_VERSION = 0
    static int INC_PATCH_VERSION = 1

    Project mainProject
    Project subProject
    String oldVersion
    String version

    boolean versionUpdated = false

    int major
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
            this.major = validateVersion(splitVersion[0], MAJOR_VERSION)
            this.minor = validateVersion(splitVersion[1], MINOR_VERSION)
            this.patch = validateVersion(parsePatchVersion(splitVersion[2]), PATCH_VERSION)
        }
    }

    String getVersion() {
        this.version = "${this.major}.${this.minor}.${this.patch}"
        return this.version
    }

    /**
     * TODO: Improvement - Check if the patch version is only numbers
     * @param version
     * @return
     */
    static String parsePatchVersion(String version) {
        return version.replace("-SNAPSHOT", "")
    }

    String loadVersionTypeToUpgrade() {
        String userVersionType = this.mainProject.findProperty(VERSION_COMMANDLINE_PROPERTY)

        if (userVersionType && !VERSION_TYPES.contains(userVersionType)) {
            throw new IllegalArgumentException("The version type '${userVersionType}' is not valid. Location: ${this.mainProject.projectDir}. Valid types are: ${VERSION_TYPES}.")
        }
        if(userVersionType == null) {
            userVersionType = PATCH_VERSION
        }
        return userVersionType
    }

    String upgradeVersion() {
        def currentVersion = this.getVersion()
        def versionTypeToUpgrade = this.loadVersionTypeToUpgrade()

        switch (versionTypeToUpgrade) {
            case MAJOR_VERSION:
                this.upgradeMajorVersion()
                break
            case MINOR_VERSION:
                this.upgradeMinorVersion()
                break
            case PATCH_VERSION:
                this.upgradePatchVersion()
                break
            default:
                throw new IllegalArgumentException("The version type '${versionTypeToUpgrade}' is not valid. Location: ${this.mainProject.projectDir}. Valid types are: ${VERSION_TYPES}")
        }
        def updatedVersion = this.getVersion()

        if (currentVersion != updatedVersion) {
            this.oldVersion = currentVersion
        }

        // Update publication version
        this.subProject.version = updatedVersion
        updatePublicationVersion(this.mainProject, this.subProject, updatedVersion)
        this.versionUpdated = true

        return updatedVersion
    }

    static void updatePublicationVersion(Project mainProject, Project subProject, String version) {
        if (subProject.version != version) {
            subProject.version = version
            mainProject.logger.lifecycle("Project '${subProject.name}' version set to '$version'")
        }

        def publishing = subProject.extensions.findByType(PublishingExtension)
        if (publishing) {
            publishing.publications.withType(MavenPublication).each { mavenPub ->
                mavenPub.setVersion(version)
                if (mavenPub.name.equalsIgnoreCase(subProject.name)) { // Or some other identifying feature
                    mainProject.logger.info("Checked/ensured publication '${mavenPub.name}' in project '${subProject.name}' reflects version '$version'")
                }
            }
        } else {
            mainProject.logger.info("No PublishingExtension found for subproject '${subProject.name}' to update publication versions directly.")
        }
    }

    void upgradeMajorVersion() {
        this.major = this.major + INC_MAJOR_VERSION
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
            throw new IllegalArgumentException("The '${versionType}' version '${version}' could not be converted to integer to be upgraded. Location: ${this.mainProject.projectDir}")
        }
        return version as Integer
    }

}
