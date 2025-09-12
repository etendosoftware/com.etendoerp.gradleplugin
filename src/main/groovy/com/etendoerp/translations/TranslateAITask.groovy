package com.etendoerp.translations

import com.etendoerp.connections.DatabaseConnection
import com.etendoerp.utils.OpenAIUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import groovy.xml.XmlUtil

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.OutputKeys
import java.io.StringWriter

class TranslateAITask extends DefaultTask {

    @Input
    String language = 'es_ES'
    @Input
    String clientId = '0'
    @Input
    String modules = ''
    @Input
    String coreModuleOutput = 'core'
    @Input
    String openaiModel = 'gpt-5-nano'

    TranslateAITask() {
        group = 'Translation'
        description = 'Auto-translate using OpenAI for the specified language(s) and module(s). ' +
                     'REQUIRED: modules parameter, OPENAI_API_KEY in gradle.properties. ' +
                     'OPTIONAL: language (comma-separated), coreModuleOutput (name for core module output), openaiModel'
    }

    @TaskAction
    void translate() {
        // Read project properties if available
        language = project.findProperty('language') ?: language
        clientId = project.findProperty('client') ?: clientId
        modules = project.findProperty('modules') ?: modules
        coreModuleOutput = project.findProperty('coreModuleOutput') ?: coreModuleOutput
        openaiModel = project.findProperty('openaiModel') ?: openaiModel

        // Read OpenAI API key from gradle.properties
        def openaiApiKey = project.findProperty('OPENAI_API_KEY')
        if (!openaiApiKey) {
            throw new IllegalArgumentException(
                "OPENAI_API_KEY is required but not found in gradle.properties. " +
                "Please add 'OPENAI_API_KEY=sk-your-api-key-here' to your gradle.properties file."
            )
        }

        // Parse multiple languages
        def languageList = language.split(',').collect { it.trim() }
        
        // Validate required parameters
        if (modules.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "The 'modules' parameter is required. " +
                "Please specify modules using -Pmodules=<module_list> where module_list can be:\n" +
                "  - 'all' for all modules including core\n" +
                "  - 'core' or '0' for core module only\n" +
                "  - Comma-separated list of module java packages or IDs\n" +
                "Examples:\n" +
                "  -Pmodules=all\n" +
                "  -Pmodules=core\n" +
                "  -Pmodules=com.etendoerp.module1,com.etendoerp.module2\n" +
                "Note: OPENAI_API_KEY must be set in gradle.properties"
            )
        }

        if (openaiApiKey.toString().trim().isEmpty()) {
            throw new IllegalArgumentException(
                "OPENAI_API_KEY is empty in gradle.properties. " +
                "Please set a valid OpenAI API key: OPENAI_API_KEY=sk-your-api-key-here"
            )
        }
        
        def moduleList = []
        if (modules.toLowerCase() == 'all') {
            moduleList = ['all']
        } else {
            moduleList = modules.split(',').collect { it.trim() }
        }

        project.logger.lifecycle("Starting AI translation for languages: ${languageList}, modules: ${moduleList}")

        // Prepare export dir - source.path is required
        def sourcesPath = project.findProperty('source.path')
        if (!sourcesPath) {
            throw new IllegalArgumentException(
                "The 'source.path' property is required but not found. " +
                "Please add 'source.path=<your_etendo_sources_path>' to your gradle.properties file. " +
                "Example: source.path=/opt/EtendoERP"
            )
        }
        
        def exportBaseDir = new File("${sourcesPath}/modules")
        if (!exportBaseDir.exists()) {
            project.logger.warn("Modules directory does not exist: ${exportBaseDir.absolutePath}")
            return
        }

        // Use existing DatabaseConnection utility to load datasource
        DatabaseConnection dbConn = new DatabaseConnection(project)
        if (!dbConn.loadDatabaseConnection()) {
            throw new IllegalStateException('Could not load database connection using project config (Openbravo.properties).')
        }

        java.sql.Connection connection = null
        try {
            connection = dbConn.getConnection()
            
            // Translate for each language
            for (String lang : languageList) {
                project.logger.lifecycle("Processing AI translations for language: ${lang}")
                processAITranslations(connection, exportBaseDir.absolutePath, lang, clientId, moduleList, openaiApiKey.toString())
            }
            
            project.logger.lifecycle("AI translation completed successfully for languages: ${languageList}")
        } finally {
            if (connection) try { connection.close() } catch (Exception ignored) {}
        }
    }

    void processAITranslations(java.sql.Connection connection, String modulesDirectory, String language, String clientId, List<String> moduleList, String apiKey) {
        project.logger.lifecycle("Processing AI translations for language: ${language}, modules: ${moduleList}")
        
        def modules = getModules(connection)
        def processedCount = 0
        def skippedCount = 0
        
        for (def module : modules) {
            String moduleId = module.moduleId
            String javaPackage = module.javaPackage
            
            // Check if this module should be processed
            boolean shouldProcessModule = false
            if (moduleList.contains('all')) {
                shouldProcessModule = true
            } else {
                for (String targetModule : moduleList) {
                    if (moduleId.equals('0') && (targetModule.toLowerCase() == 'core' || targetModule.equals('0'))) {
                        shouldProcessModule = true
                        break
                    } else if (javaPackage != null && javaPackage.toLowerCase().equals(targetModule.toLowerCase())) {
                        shouldProcessModule = true
                        break
                    } else if (moduleId.equals(targetModule)) {
                        shouldProcessModule = true
                        break
                    }
                }
            }
            
            if (!shouldProcessModule) {
                project.logger.debug("Skipping module ${moduleId} (${javaPackage}) - not in target modules")
                continue
            }

            // Determine module directory
            String moduleDirectory
            if (moduleId.equals('0')) {
                moduleDirectory = modulesDirectory + '/' + coreModuleOutput + '.' + language + '/referencedata/translation/' + language + '/'
            } else {
                moduleDirectory = modulesDirectory + '/' + javaPackage + '.' + language + '/referencedata/translation/' + language + '/'
            }

            File moduleDir = new File(moduleDirectory)
            if (!moduleDir.exists()) {
                project.logger.info("Module directory not found: ${moduleDirectory} - probably no translations exist for this module, skipping")
                skippedCount++
                continue
            }

            project.logger.lifecycle("Processing module: ${javaPackage ?: 'core'} (${moduleId})")
            
            // Process all XML files in this module directory
            def xmlFiles = moduleDir.listFiles().findAll { it.name.endsWith('.xml') && !it.name.startsWith('CONTRIBUTORS') }
            
            for (File xmlFile : xmlFiles) {
                project.logger.lifecycle("Processing file: ${xmlFile.name}")
                int translatedRecords = processXMLFile(connection, xmlFile, language, apiKey)
                processedCount += translatedRecords
            }
        }
        
        project.logger.lifecycle("AI Translation summary for ${language}: ${processedCount} records translated, ${skippedCount} modules skipped (no translation directory)")
    }

    int processXMLFile(java.sql.Connection connection, File xmlFile, String language, String apiKey) {
        def translatedCount = 0
        def xmlModified = false
        
        try {
            // Parse XML file using DOM
            def documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            def document = documentBuilder.parse(xmlFile)
            def root = document.documentElement
            def tableName = root.getAttribute('table')
            
            project.logger.lifecycle("Processing table: ${tableName}")
            
            // Process each row in the XML
            def rows = root.getElementsByTagName('row')
            for (int i = 0; i < rows.length; i++) {
                def row = rows.item(i)
                def recordId = row.getAttribute('id')
                def isTranslated = row.getAttribute('trl')
                
                // Only process if not already translated
                if (isTranslated != 'Y') {
                    def shouldTranslate = false
                    
                    // Check if any field needs translation
                    def values = row.getElementsByTagName('value')
                    for (int j = 0; j < values.length; j++) {
                        def value = values.item(j)
                        def columnName = value.getAttribute('column')
                        def isTrl = value.getAttribute('isTrl')
                        def original = value.getAttribute('original')
                        def currentText = value.textContent
                        
                        // Translate if field is not translated and has original text
                        if (isTrl != 'Y' && original && !original.trim().isEmpty() && 
                            (currentText.trim().isEmpty() || currentText.equals(original))) {
                            shouldTranslate = true
                            break
                        }
                    }
                    
                    if (shouldTranslate) {
                        def translatedFields = 0
                        
                        // Translate each field that needs translation
                        for (int j = 0; j < values.length; j++) {
                            def value = values.item(j)
                            def columnName = value.getAttribute('column')
                            def isTrl = value.getAttribute('isTrl')
                            def original = value.getAttribute('original')
                            def currentText = value.textContent
                            
                            if (isTrl != 'Y' && original && !original.trim().isEmpty() && 
                                (currentText.trim().isEmpty() || currentText.equals(original))) {
                                
                                def translatedText = translateText(original, language)
                                if (translatedText && !translatedText.equals(original)) {
                                    // Clean the translated text to remove unwanted line breaks
                                    def cleanTranslatedText = translatedText.replaceAll(/\n|\r/, ' ').trim()
                                    
                                    // Update XML with translation - DOM maintains attribute order
                                    value.textContent = cleanTranslatedText
                                    value.setAttribute('isTrl', 'Y')
                                    translatedFields++
                                    xmlModified = true
                                    
                                    project.logger.lifecycle("✓ UPDATED XML: ${tableName}.${columnName}[${recordId}] -> '${original}' ➜ '${cleanTranslatedText}'")
                                }
                            }
                        }
                        
                        if (translatedFields > 0) {
                            // Mark the entire record as translated in XML
                            row.setAttribute('trl', 'Y')
                            translatedCount++
                            
                            project.logger.lifecycle("✅ Record ${recordId} in ${tableName} completed: ${translatedFields} fields translated in XML")
                        }
                    }
                }
            }
            
            // Save XML file if it was modified
            if (xmlModified) {
                saveXMLFile(document, xmlFile)
                project.logger.lifecycle("💾 XML file saved: ${xmlFile.name}")
            }
            
        } catch (Exception e) {
            project.logger.error("Error processing XML file ${xmlFile.name}: ${e.message}", e)
        }
        
        return translatedCount
    }

    String translateText(String textToTranslate, String targetLanguage) {
        String systemPrompt = "You are a professional translator. Translate the provided text to ${targetLanguage}. Only return the translated text, nothing else. Maintain the original meaning and context. If the text contains technical terms or proper nouns, keep them appropriately."
        
        def messages = [
            [
                role: "user",
                content: textToTranslate
            ]
        ]
        
        return OpenAIUtils.callOpenAI(project, systemPrompt, messages, openaiModel, null, 1, 60)
    }

    void saveXMLFile(def document, File xmlFile) {
        try {
            // Write the modified XML using DOM Transformer (same as ExportTranslationsTask)
            def tf = TransformerFactory.newInstance()
            try { tf.setAttribute('indent-number', 2) } catch (Exception ignored) {}
            def transformer = tf.newTransformer()
            transformer.setOutputProperty(OutputKeys.ENCODING, 'UTF-8')
            transformer.setOutputProperty(OutputKeys.INDENT, 'no')  // Disable indentation to prevent line breaks in content
            transformer.setOutputProperty(OutputKeys.METHOD, 'xml')
            
            // Transform to string first
            def stringWriter = new StringWriter()
            def source = new DOMSource(document)
            def result = new StreamResult(stringWriter)
            transformer.transform(source, result)
            
            // Post-process the XML string to add specific line breaks
            def xmlContent = stringWriter.toString()
            // Add line break before <compiereTrl>
            xmlContent = xmlContent.replaceAll(/(<compiereTrl)/, '\n$1')
            // Ensure file ends with a line break
            if (!xmlContent.endsWith('\n')) {
                xmlContent += '\n'
            }
            
            // Write the final content to file
            xmlFile.text = xmlContent
            
            project.logger.debug("XML file saved successfully: ${xmlFile.name}")
            
        } catch (Exception e) {
            project.logger.error("Error saving XML file ${xmlFile.name}: ${e.message}", e)
            throw e
        }
    }

    List getModules(java.sql.Connection connection) {
        def modules = []
        def sql = "SELECT ad_module_id AS moduleId, JAVAPACKAGE AS javaPackage FROM ad_module ORDER BY javaPackage"
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        java.sql.ResultSet rs = stmt.executeQuery()
        while (rs.next()) {
            modules.add([moduleId: rs.getString('moduleId'), javaPackage: rs.getString('javaPackage')])
        }
        rs.close(); stmt.close()
        return modules
    }
}
