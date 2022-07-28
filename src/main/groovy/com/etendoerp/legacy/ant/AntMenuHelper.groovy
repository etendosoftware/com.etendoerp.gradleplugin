package com.etendoerp.legacy.ant

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import org.gradle.api.Project

class AntMenuHelper {

    static antMenu(Project project, String titleOptions, Map options, String preMessage = null, String antProp = null, String exitMessage = null) {
        def customAntProp = antProp ?: generateRandomAntProp()

        def message = preMessage ?: ""

        def defaultExitValue = "0"
        message += "* ${titleOptions}  \n"
        message += "* [$defaultExitValue] - Exit \n"

        def optionsToChoose = [defaultExitValue]

        options.each {
            def key = it.key as String
            message += "* [${key}] - ${it.value} \n"
            optionsToChoose.add(key)
        }
        message += "* Insert one of the following options:"

        def validArguments = optionsToChoose.join(",")

        project.ant.input(message: message, validargs: validArguments, addproperty: customAntProp)
        def selectedOption = project.ant[customAntProp]
        println("* Option selected: ${selectedOption} - ${options.getOrDefault(selectedOption,"")} \n")

        if (selectedOption == defaultExitValue) {
            throw new IllegalArgumentException("Exit. ${exitMessage ?: ""}")
        }

        return  selectedOption
    }

    static antUserInput(Project project, String message, String defaultValue, antProp = null) {
        def customAntProp = antProp ?: generateRandomAntProp()

        project.ant.input(message: message, addproperty: customAntProp, defaultValue: defaultValue)

        def userInput = project.ant[customAntProp]
        println("* User input: ${userInput}")
        return userInput
    }

    static generateRandomAntProp() {
        return "randomAntProp${UUID.randomUUID().toString()}"
    }


    static boolean confirmationMenu(Project project, String message) {
        if (project.extensions.findByType(EtendoPluginExtension).ignoreDisplayMenu) {
            return true
        }

        def defaultValue = "Y"
        StringBuilder preMessage = new StringBuilder(message)
        preMessage.append("* CONTINUE ? [${defaultValue}/n]:")

        def userChoice = antUserInput(project, preMessage.toString(), defaultValue)
        return defaultValue.equalsIgnoreCase(userChoice as String)
    }

}