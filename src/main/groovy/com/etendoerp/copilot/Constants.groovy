package com.etendoerp.copilot

class Constants {

    private Constants() {
        // Private constructor to prevent instantiation
    }

    static final String MODULES_PROJECT = 'modules'
    static final String COPILOT_MODULE = 'com.etendoerp.copilot'
    static final String ARG_PROPERTY = 'arg'
    static final String COPILOT_PORT_PROPERTY = 'COPILOT_PORT'
    static final String OPENAI_API_KEY_PROPERTY = 'OPENAI_API_KEY'
    static final String COPILOT_DOCKER_REPO = 'etendo_copilot_core'
    static final String TOOLS_CONFIG_FILE = 'tools_config.json'
    static final String COPILOT_IMAGE_TAG = 'COPILOT_IMAGE_TAG'
    static final String COPILOT_PULL_IMAGE = 'COPILOT_PULL_IMAGE'
    static final String COPILOT_DOCKER_CONTAINER_NAME = 'COPILOT_DOCKER_CONTAINER_NAME'
    static final String DEPENDENCIES_TOOLS_FILENAME = 'DEPENDENCIES_TOOLS_FILENAME'
}