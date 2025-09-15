package com.etendoerp.translations

import com.etendoerp.connections.DatabaseConnection
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.parsers.DocumentBuilderFactory
import java.sql.SQLException

/**
 * Gradle task for exporting translation files from Etendo database.
 * 
 * This task exports translation data for specified languages and modules from the database
 * to XML files that can be used for localization and translation management.
 * 
 * <p>The task supports:</p>
 * <ul>
 *   <li>Multiple languages (comma-separated)</li>
 *   <li>Multiple modules (comma-separated or "all")</li>
 *   <li>Reduced version export (excluding help, description, and tooltip fields)</li>
 *   <li>Reference data export</li>
 *   <li>Contributors export</li>
 * </ul>
 * 
 * <p>Required parameters:</p>
 * <ul>
 *   <li>{@code modules} - Comma-separated list of modules or "all"</li>
 *   <li>{@code source.path} - Path to Etendo sources directory</li>
 * </ul>
 * 
 * <p>Optional parameters:</p>
 * <ul>
 *   <li>{@code language} - Comma-separated list of languages (default: es_ES)</li>
 *   <li>{@code client} - Client ID (default: 0)</li>
 *   <li>{@code reducedVersion} - Whether to exclude help/description fields (default: false)</li>
 *   <li>{@code coreModuleOutput} - Name for core module output directory (default: core)</li>
 * </ul>
 * 
 * @author Etendo Software
 * @since 1.0
 */
class ExportTranslationsTask extends DefaultTask {

    @Input
    String language = 'es_ES'
    @Input
    String clientId = '0'
    @Input
    boolean reducedVersion = false
    @Input
    String modules = ''
    @Input
    String coreModuleOutput = 'core'

    ExportTranslationsTask() {
        group = 'Translation'
        description = 'Export translation files for the specified language(s) and module(s). ' +
                     'REQUIRED: modules parameter (comma-separated or "all"). ' +
                     'OPTIONAL: language (comma-separated), coreModuleOutput (name for core module output)'
    }

    /**
     * Main task action that orchestrates the translation export process.
     * 
     * <p>This method:</p>
     * <ol>
     *   <li>Reads and validates input parameters from Gradle properties</li>
     *   <li>Parses language and module lists</li>
     *   <li>Establishes database connection</li>
     *   <li>Exports translations for each specified language</li>
     *   <li>Handles cleanup and error reporting</li>
     * </ol>
     * 
     * @throws IllegalArgumentException if required parameters are missing or invalid
     * @throws IllegalStateException if database connection cannot be established
     */
    @TaskAction
    void export() {
        // Read project properties if available
        language = project.findProperty('language') ?: language
        clientId = project.findProperty('client') ?: clientId
        reducedVersion = (project.findProperty('reducedVersion') ?: reducedVersion.toString()).toBoolean()
        modules = project.findProperty('modules') ?: modules
        coreModuleOutput = project.findProperty('coreModuleOutput') ?: coreModuleOutput

        // Parse multiple languages
        def languageList = language.split(',').collect { it.trim() }
        
        // Validate and parse multiple modules
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
                "  -Pmodules=com.etendoerp.module1,com.etendoerp.module2"
            )
        }
        
        def moduleList = []
        if (modules.toLowerCase() == 'all') {
            moduleList = ['all']
        } else {
            moduleList = modules.split(',').collect { it.trim() }
        }

        project.logger.lifecycle("Starting translation export for languages: ${languageList}, modules: ${moduleList}")

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
        if (!exportBaseDir.exists()) exportBaseDir.mkdirs()

        // Use existing DatabaseConnection utility to load datasource
        DatabaseConnection dbConn = new DatabaseConnection(project)
        if (!dbConn.loadDatabaseConnection()) {
            throw new IllegalStateException('Could not load database connection using project config (Openbravo.properties).')
        }

        java.sql.Connection connection = null
        try {
            connection = dbConn.getConnection()
            
            // Export for each language
            for (String lang : languageList) {
                project.logger.lifecycle("Exporting translations for language: ${lang}")
                exportTranslations(connection, exportBaseDir.absolutePath, lang, clientId, reducedVersion, moduleList)
            }
            
            project.logger.lifecycle("Translation export completed successfully for languages: ${languageList}")
        } finally {
            if (connection) try { connection.close() } catch (Exception ignored) {}
        }
    }

    // --- The following methods are adapted from the in-script implementation ---

    }

    /**
     * Exports translation data for a specific language from the database.
     * 
     * <p>This method handles the complete export process for a single language:</p>
     * <ol>
     *   <li>Checks if the language is initialized, initializes if necessary</li>
     *   <li>Retrieves all translation tables from the database</li>
     *   <li>Exports module translations for each table</li>
     *   <li>Exports reference data translations</li>
     *   <li>Exports contributors information</li>
     * </ol>
     * 
     * @param connection the database connection to use
     * @param exportDirectory the base directory where exported files will be saved
     * @param language the language code to export (e.g., "es_ES", "fr_FR")
     * @param clientId the client ID to filter data by
     * @param isReducedVersion whether to exclude help, description, and tooltip fields
     * @param moduleList list of modules to export, or ["all"] for all modules
     */
    void exportTranslations(java.sql.Connection connection, String exportDirectory, String language, String clientId, boolean isReducedVersion, List<String> moduleList) {
        project.logger.lifecycle("Exporting translations for language: ${language}, client: ${clientId}, modules: ${moduleList}")

        // Check if language is initialized, initialize if necessary
        if (!isLanguageInitialized(connection, language)) {
            project.logger.lifecycle("Language ${language} is not initialized. Initializing...")
            initializeLanguage(connection, language)
        } else {
            project.logger.lifecycle("Language ${language} is already initialized.")
        }

        def trlModulesTables = getTrlModulesTables(connection)
        project.logger.lifecycle("Found ${trlModulesTables.size()} translation tables to process")
        
        for (String tableName : trlModulesTables) {
            project.logger.lifecycle("Processing table: ${tableName}")
            exportModuleTranslations(connection, exportDirectory, clientId, language, tableName, isReducedVersion, moduleList)
        }
        
        exportReferenceDataTranslations(connection, exportDirectory, language, isReducedVersion, moduleList)
        exportContributors(connection, exportDirectory, language)
    }

    /**
     * Retrieves all translation tables (_TRL suffix) that should be processed for export.
     * 
     * <p>This method queries the database to find all tables ending with '_TRL'
     * that have corresponding base tables with AD_Module_ID columns or related parent tables.</p>
     * 
     * @param connection the database connection to use
     * @return a list of translation table names (uppercase)
     */
    List<String> getTrlModulesTables(java.sql.Connection connection) {
        def tables = []
        def sql = """
            select upper(t.tablename) AS tablename
            from aD_table t
            where lower(t.tablename) like '%trl'
             and exists
              (select 1
               from ad_column c,
                 ad_table t2
               where t2.ad_table_id = c.ad_table_id
               and lower(columnname) = 'ad_module_id'
               and lower(t2.tablename) || '_trl' = lower(t.tablename)
               union
               select 1
                 from ad_table t1, ad_column c, ad_table t2, ad_column c2
                where t1.ad_table_id = c.ad_table_id
                  and c.isparent='Y'
                  and lower(t2.tablename)||'_id' = lower(c.columnname)
                  and lower(t1.tablename) || '_trl' = lower(t.tablename)
                  and exists (select 1 from ad_column where ad_table_id = t2.ad_table_id and lower(columnname) = 'ad_module_id')
               union
               select 1
                  from ad_table t1, ad_column c, ad_table t2, ad_column c2, ad_table t3, ad_column c3
                where t2.ad_table_id = c2.ad_table_id
                  and t1.ad_table_id = c.ad_table_id
                  and c.isparent='Y'
                  and c2.isparent='Y'
                  and lower(t3.tablename)||'_id' = lower(c2.columnname)
                  and lower(t2.tablename)||'_id' = lower(c.columnname)
                  and lower(t1.tablename) || '_trl' = lower(t.tablename)
                  and exists (select 1 from ad_column where ad_table_id = t3.ad_table_id and lower(columnname) = 'ad_module_id')
                  and not exists (select 1 from ad_column where ad_table_id = t2.ad_table_id and lower(columnname) = 'ad_module_id')
               )
        """
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        java.sql.ResultSet rs = stmt.executeQuery()
        while (rs.next()) { tables.add(rs.getString('tablename')) }
        rs.close(); stmt.close()
        return tables
    }

    /**
     * Exports translation data for all modules and their associated tables.
     * 
     * <p>This method iterates through all modules and exports translation data
     * for tables that have the AD_Module_ID column, filtering by the specified module list.</p>
     * 
     * @param connection the database connection to use
     * @param rootDirectory the root directory for exported files
     * @param clientId the client ID to filter data by
     * @param language the target language code
     * @param trlTable the translation table name
     * @param isReducedVersion whether to exclude help/description fields
     * @param moduleList list of modules to export, or ["all"] for all modules
     */
    void exportModuleTranslations(java.sql.Connection connection, String rootDirectory, String clientId, String language, String trlTable, boolean isReducedVersion, List<String> moduleList) {
        def modules = getModules(connection)
        for (def module : modules) {
            String moduleId = module.moduleId
            String javaPackage = module.javaPackage
            String baseTable = trlTable.toLowerCase().replaceAll('_trl\$', '')
            
            // Check if this module should be exported
            boolean shouldExportModule = false
            if (moduleList.contains('all')) {
                shouldExportModule = true
            } else {
                // Check if module matches any of the specified modules
                for (String targetModule : moduleList) {
                    if (moduleId.equals('0') && (targetModule.toLowerCase() == 'core' || targetModule.equals('0'))) {
                        shouldExportModule = true
                        break
                    } else if (javaPackage != null && javaPackage.toLowerCase().equals(targetModule.toLowerCase())) {
                        shouldExportModule = true
                        break
                    } else if (moduleId.equals(targetModule)) {
                        shouldExportModule = true
                        break
                    }
                }
            }
            
            if (!shouldExportModule) {
                project.logger.debug("Skipping module ${moduleId} (${javaPackage}) - not in target modules")
                continue
            }
            
            // Only export if the base table has AD_Module_ID column (i.e., it's module-related)
            if (columnExistsInTable(connection, baseTable, 'AD_Module_ID')) {
                exportTableForModule(connection, language, false, false, baseTable, baseTable + '_ID',
                        rootDirectory, moduleId, getModuleLanguage(connection, moduleId), javaPackage,
                        true, isReducedVersion)
            } else {
                project.logger.debug("Skipping table ${baseTable} as it doesn't have AD_Module_ID column")
            }
        }
    }

    /**
     * Retrieves all modules from the AD_MODULE table.
     * 
     * @param connection the database connection to use
     * @return a list of maps containing moduleId and javaPackage for each module
     */
    List getModules(java.sql.Connection connection) {
        def modules = []
        def sql = "select ad_module_id AS moduleId, JAVAPACKAGE AS javaPackage from ad_module"
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        java.sql.ResultSet rs = stmt.executeQuery()
        while (rs.next()) {
            modules.add([moduleId: rs.getString('moduleId'), javaPackage: rs.getString('javaPackage')])
        }
        rs.close(); stmt.close()
        return modules
    }

    /**
     * Retrieves the base language for a specific module.
     * 
     * @param connection the database connection to use
     * @param moduleId the module ID to get the language for
     * @return the language code for the module, defaults to "en_US" if not set
     */
    String getModuleLanguage(java.sql.Connection connection, String moduleId) {
        def sql = 'SELECT AD_LANGUAGE AS language FROM AD_MODULE WHERE AD_MODULE_ID = ?'
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, moduleId)
        java.sql.ResultSet rs = stmt.executeQuery()
        String moduleLanguage = 'en_US'
        if (rs.next()) moduleLanguage = rs.getString('language') ?: 'en_US'
        rs.close(); stmt.close()
        return moduleLanguage
    }

    /**
     * Exports translation data for a specific table and module to an XML file.
     * 
     * <p>This method creates an XML file containing translation data for the specified table.
     * The XML structure includes:</p>
     * <ul>
     *   <li>Language and table metadata</li>
     *   <li>Row elements for each translated record</li>
     *   <li>Value elements for each translatable column</li>
     *   <li>Original values and translation status indicators</li>
     * </ul>
     * 
     * <p>The method handles both regular translation tables (_TRL suffix) and reference data tables.</p>
     * 
     * @param connection the database connection to use
     * @param language the target language code
     * @param exportReferenceData whether this is a reference data export
     * @param exportAll whether to export all records regardless of dataset restrictions
     * @param table the base table name (without _TRL suffix)
     * @param tableID the primary key column name for the table
     * @param rootDirectory the root directory for exported files
     * @param moduleId the module ID being exported
     * @param moduleLanguage the base language of the module
     * @param javaPackage the Java package name of the module
     * @param isTrl whether this is a translation table (_TRL)
     * @param isReducedVersion whether to exclude help/description fields
     */
    void exportTableForModule(java.sql.Connection connection, String language, boolean exportReferenceData,
                              boolean exportAll, String table, String tableID, String rootDirectory,
                              String moduleId, String moduleLanguage, String javaPackage, boolean isTrl,
                              boolean isReducedVersion) {
        try {
            String trlTable = table
            if (isTrl && !table.endsWith('_TRL')) trlTable = table + '_TRL'

            // Get translatable columns that actually exist in the TRL table
            def trlColumns = getTrlColumns(connection, table, isReducedVersion)
            if (trlColumns.isEmpty()) {
                project.logger.lifecycle("No translatable columns found for table ${table}, skipping")
                return
            }
            
            String keyColumn = table + '_ID'
            boolean hasCentrallyMaintained = hasCentrallyMaintainedColumn(connection, table)
            boolean hasModuleId = columnExistsInTable(connection, table, 'AD_Module_ID')

            def sqlBuilder = new StringBuilder()
            sqlBuilder.append('select ')
            if (isTrl) sqlBuilder.append('t.AD_Language, t.IsTranslated, ')
            sqlBuilder.append('t.').append(keyColumn)
            
            // Only include columns that exist in both tables
            for (def column : trlColumns) {
                sqlBuilder.append(', t.').append(column.columnName)
                if (isTrl) {
                    // Check if column exists in base table too
                    if (columnExistsInTable(connection, table, column.columnName)) {
                        sqlBuilder.append(', o.').append(column.columnName).append(' as original_').append(column.columnName)
                    }
                }
            }
            
            sqlBuilder.append(' from ').append(trlTable).append(' t, ').append(table).append(' o')
            if (exportReferenceData && !exportAll) sqlBuilder.append(', AD_DATASET_TABLE DT, AD_DATASET D')
            sqlBuilder.append(' where ')
            if (isTrl) sqlBuilder.append('t.AD_Language = ? and ')
            sqlBuilder.append('o.').append(keyColumn).append('= t.').append(keyColumn)
            if (hasCentrallyMaintained) sqlBuilder.append(" and o.IsCentrallyMaintained = 'N' ")
            sqlBuilder.append(" and o.AD_Client_ID='0' ")
            
            // Only filter by module if the table actually has AD_Module_ID column
            if (!exportReferenceData && hasModuleId) {
                sqlBuilder.append(' and o.AD_Module_ID = ? ')
            }
            
            if (exportReferenceData && !exportAll) {
                sqlBuilder.append(" and DT.AD_Table_ID = (select AD_Table_ID from AD_Table where Upper(TableName) = Upper(?)) ")
                sqlBuilder.append(' and D.AD_Dataset_ID = DT.AD_Dataset_ID and D.AD_Module_ID = ? ')
            }
            sqlBuilder.append(' order by t.').append(keyColumn)

            String sql = sqlBuilder.toString()
            project.logger.lifecycle("Executing SQL for table ${table}: ${sql}")

            java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
            int paramIndex = 1
            if (isTrl) stmt.setString(paramIndex++, language)
            
            // Only set module parameter if the table has AD_Module_ID column
            if (!exportReferenceData && hasModuleId) {
                stmt.setString(paramIndex++, moduleId)
            }
            
            if (exportReferenceData && !exportAll) {
                stmt.setString(paramIndex++, table)
                stmt.setString(paramIndex++, moduleId)
            }

            java.sql.ResultSet rs = stmt.executeQuery()
            def documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            def document = documentBuilder.newDocument()
            def root = document.createElement('compiereTrl')
            root.setAttribute('language', language)
            root.setAttribute('table', table.toUpperCase())
            root.setAttribute('baseLanguage', moduleLanguage)
            root.setAttribute('version', getSystemVersion(connection))
            document.appendChild(root)

            int recordCount = 0
            boolean hasRecords = false
            while (rs.next()) {
                hasRecords = true
                recordCount++
                def rowElement = document.createElement('row')
                rowElement.setAttribute('id', rs.getString(keyColumn))
                if (isTrl) rowElement.setAttribute('trl', rs.getString('IsTranslated') ?: 'N')
                for (def column : trlColumns) {
                    try {
                        String value = rs.getString(column.columnName)
                        // Always create the value element, even if empty
                        def valueElement = document.createElement('value')
                        valueElement.setAttribute('column', column.columnName)
                        
                        // Follow exact original logic for isTrl calculation
                        String originalValue = null
                        String isTrlValue = "Y"  // Start with Y like original
                        
                        if (isTrl && columnExistsInTable(connection, table, column.columnName)) {
                            try {
                                originalValue = rs.getString('original_' + column.columnName)
                            } catch (Exception ignored) {
                                // Original column doesn't exist, continue without it
                            }
                        }
                        
                        // Apply exact original logic: if origString is null, set to "" and isTrl to "N"
                        if (originalValue == null) {
                            originalValue = ""
                            isTrlValue = "N"
                        }
                        
                        // Apply exact original logic: if valueString is null, set to "" and isTrl to "N"
                        String currentValue = value
                        if (currentValue == null) {
                            currentValue = ""
                            isTrlValue = "N"
                        }
                        
                        // Apply exact original logic: if origString equals valueString, set isTrl to "N"
                        if (originalValue.equals(currentValue)) {
                            isTrlValue = "N"
                        }
                        
                        // Set attributes in correct order: column, isTrl, original
                        valueElement.setAttribute('column', column.columnName)
                        if (isTrl && columnExistsInTable(connection, table, column.columnName)) {
                            valueElement.setAttribute('isTrl', isTrlValue)
                            valueElement.setAttribute('original', originalValue)
                        }
                        
                        // Set content even if value is null or empty
                        valueElement.setTextContent(currentValue)
                        rowElement.appendChild(valueElement)
                    } catch (SQLException ex) {
                        // Column doesn't exist in result set, skip it
                        project.logger.debug("Column ${column.columnName} not found in result set for table ${table}")
                    }
                }
                root.appendChild(rowElement)
            }

            rs.close(); stmt.close()

            if (hasRecords) {
                String directory = ''
                if (moduleId.equals('0')) {
                    // Core module: use coreModuleOutput parameter instead of 'core'
                    directory = rootDirectory + '/' + coreModuleOutput + '.' + language + '/referencedata/translation/' + language + '/'
                } else {
                    // Regular module: put in javaPackage.language/referencedata/translation/language/
                    directory = rootDirectory + '/' + javaPackage + '.' + language + '/referencedata/translation/' + language + '/'
                }
                new File(directory).mkdirs()
                
                // Use uppercase table name for filename
                String upperTableName = trlTable.toUpperCase()
                String fileName = directory + upperTableName + '_' + language + '.xml'
                project.logger.lifecycle("Writing XML file: ${fileName} with ${recordCount} records")

                def tf = TransformerFactory.newInstance()
                try { tf.setAttribute('indent-number', 2) } catch (Exception ignored) {}
                def transformer = tf.newTransformer()
                transformer.setOutputProperty(OutputKeys.ENCODING, 'UTF-8')
                transformer.setOutputProperty(OutputKeys.INDENT, 'yes')
                def source = new DOMSource(document)
                def result = new StreamResult(new File(fileName))
                transformer.transform(source, result)
            } else {
                project.logger.lifecycle("No records found for table ${table}, skipping XML generation")
            }

        } catch (Exception e) {
            project.logger.error("Error processing table ${table}: ${e.message}", e)
        }
    }

    /**
     * Retrieves the list of translatable columns for a given table.
     * 
     * <p>This method queries the AD_Column table to find all text columns
     * (reference IDs 10 and 14) that are active and translatable.
     * In reduced version mode, it excludes help, description, and tooltip columns.</p>
     * 
     * @param connection the database connection to use
     * @param tableName the base table name (without _TRL suffix)
     * @param isReducedVersion whether to exclude help/description fields
     * @return a list of maps containing columnName for each translatable column
     */
    List getTrlColumns(java.sql.Connection connection, String tableName, boolean isReducedVersion) {
        def columns = []
        String trlTableName = tableName + '_TRL'
        
        def sql = """
            select ColumnName AS columnName
            from AD_Table t, AD_Column c
            where c.AD_Table_ID=t.AD_Table_ID 
            and upper(t.TableName)=?
            and c.AD_Reference_ID in ('10','14')
            and c.IsActive = 'Y'
        """
        if (isReducedVersion) {
            sql += " and upper(ColumnName) not like '%HELP%' and upper(ColumnName) not like '%DESCRIPTION%' and upper(ColumnName) not like '%MSGTIP%' "
        }
        sql += ' order by IsMandatory desc, ColumnName'

        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, trlTableName.toUpperCase())
        java.sql.ResultSet rs = stmt.executeQuery()
        while (rs.next()) { 
            String columnName = rs.getString('columnName')
            // Skip system columns that are not translatable content
            if (!columnName.equalsIgnoreCase('AD_Language') && 
                !columnName.equalsIgnoreCase('IsTranslated') && 
                !columnName.equalsIgnoreCase('AD_Client_ID') && 
                !columnName.equalsIgnoreCase('AD_Org_ID') &&
                !columnName.endsWith('_ID')) {
                columns.add([columnName: columnName])
            }
        }
        rs.close(); stmt.close()
        return columns
    }

    /**
     * Checks if a table has an IsCentrallyMaintained column.
     * 
     * <p>This is used to determine whether to filter out centrally maintained records
     * during export, as these are typically not translated.</p>
     * 
     * @param connection the database connection to use
     * @param tableName the table name to check
     * @return true if the table has IsCentrallyMaintained column, false otherwise
     */
    boolean hasCentrallyMaintainedColumn(java.sql.Connection connection, String tableName) {
        def sql = """
            select count(*) as count
            from AD_Table t, AD_Column c
            where c.AD_Table_ID=t.AD_Table_ID 
            and upper(c.ColumnName)='ISCENTRALLYMAINTAINED'
            and c.IsActive = 'Y'
            and upper(t.tableName) = upper(?)
        """
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, tableName)
        java.sql.ResultSet rs = stmt.executeQuery()
        boolean hasCentrallyMaintained = false
        if (rs.next()) hasCentrallyMaintained = rs.getInt('count') > 0
        rs.close(); stmt.close()
        return hasCentrallyMaintained
    }

    /**
     * Checks if a specific column exists in a given table.
     * 
     * @param connection the database connection to use
     * @param tableName the table name to check
     * @param columnName the column name to check for
     * @return true if the column exists and is active, false otherwise
     */
    boolean columnExistsInTable(java.sql.Connection connection, String tableName, String columnName) {
        def sql = """
            select count(*) as count
            from AD_Table t, AD_Column c
            where c.AD_Table_ID=t.AD_Table_ID 
            and upper(c.ColumnName)=upper(?)
            and c.IsActive = 'Y'
            and upper(t.tableName) = upper(?)
        """
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, columnName)
        stmt.setString(2, tableName)
        java.sql.ResultSet rs = stmt.executeQuery()
        boolean exists = false
        if (rs.next()) exists = rs.getInt('count') > 0
        rs.close(); stmt.close()
        return exists
    }

    /**
     * Retrieves the current system version from the AD_SYSTEM_INFO table.
     * 
     * @param connection the database connection to use
     * @return the system version string, defaults to "1.0" if not found
     */
    String getSystemVersion(java.sql.Connection connection) {
        def sql = 'SELECT OB_VERSION FROM AD_SYSTEM_INFO'
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        java.sql.ResultSet rs = stmt.executeQuery()
        String version = '1.0'
        if (rs.next()) version = rs.getString('OB_VERSION') ?: '1.0'
        rs.close(); stmt.close()
        return version
    }

    /**
     * Exports reference data translations for all applicable tables and modules.
     * 
     * <p>This method processes all tables that are marked as reference data
     * in the dataset configuration and exports their translations.</p>
     * 
     * @param connection the database connection to use
     * @param rootDirectory the root directory for exported files
     * @param language the target language code
     * @param isReducedVersion whether to exclude help/description fields
     * @param moduleList list of modules to export, or ["all"] for all modules
     */
    void exportReferenceDataTranslations(java.sql.Connection connection, String rootDirectory, String language, boolean isReducedVersion, List<String> moduleList) {
        try {
            def referenceDataTables = getReferenceDataTables(connection)
            for (def refData : referenceDataTables) {
                // Check if this module should be exported
                boolean shouldExportModule = false
                if (moduleList.contains('all')) {
                    shouldExportModule = true
                } else {
                    for (String targetModule : moduleList) {
                        if (refData.moduleId.equals('0') && (targetModule.toLowerCase() == 'core' || targetModule.equals('0'))) {
                            shouldExportModule = true
                            break
                        } else if (refData.javaPackage != null && refData.javaPackage.toLowerCase().equals(targetModule.toLowerCase())) {
                            shouldExportModule = true
                            break
                        } else if (refData.moduleId.equals(targetModule)) {
                            shouldExportModule = true
                            break
                        }
                    }
                }
                
                if (shouldExportModule) {
                    exportTableForModule(connection, language, true, false, refData.tableName, refData.tableName + '_ID',
                            rootDirectory, refData.moduleId, refData.moduleLanguage, refData.javaPackage,
                            true, isReducedVersion)
                } else {
                    project.logger.debug("Skipping reference data for module ${refData.moduleId} (${refData.javaPackage}) - not in target modules")
                }
            }
        } catch (Exception e) {
            project.logger.error('Error exporting reference data: ' + e.message, e)
        }
    }

    /**
     * Retrieves all tables that are configured as reference data for export.
     * 
     * <p>This method queries the dataset configuration to find all tables
     * that have export allowed and have corresponding translation tables.</p>
     * 
     * @param connection the database connection to use
     * @return a list of maps containing table metadata (moduleId, tableName, etc.)
     */
    List getReferenceDataTables(java.sql.Connection connection) {
        def tables = []
        def sql = """
            SELECT D.AD_MODULE_ID as moduleId, M.ISINDEVELOPMENT, t.tablename, 
                   t.AD_Table_ID as tableId, M.AD_Language as moduleLanguage, M.JavaPackage as javaPackage
            FROM AD_DATASET D,
                 AD_DATASET_TABLE DT,
                 AD_TABLE T,
                 AD_MODULE M
            WHERE EXPORTALLOWED='Y'
              AND DT.AD_DATASET_ID = D.AD_DATASET_ID
              AND T.AD_TABLE_ID = DT.AD_TABLE_ID
              AND M.AD_MODULE_ID = D.AD_MODULE_ID
              AND EXISTS (SELECT 1 
                            FROM AD_TABLE T1
                           WHERE UPPER(T1.TABLENAME) = UPPER(T.TABLENAME)||'_TRL')
        """
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        java.sql.ResultSet rs = stmt.executeQuery()
        while (rs.next()) {
            tables.add([
                moduleId: rs.getString('moduleId'),
                tableName: rs.getString('tablename'),
                tableId: rs.getString('tableId'),
                moduleLanguage: rs.getString('moduleLanguage') ?: 'en_US',
                javaPackage: rs.getString('javaPackage')
            ])
        }
        rs.close(); stmt.close()
        return tables
    }

    /**
     * Exports contributors information for a specific language.
     * 
     * <p>This method creates an XML file containing information about
     * who contributed translations for the specified language.</p>
     * 
     * @param connection the database connection to use
     * @param exportDirectory the directory where the contributors file will be saved
     * @param language the language code to get contributors for
     */
    void exportContributors(java.sql.Connection connection, String exportDirectory, String language) {
        try {
            def contributorsText = getContributors(connection, language)
            if (contributorsText) {
                // Put contributors in the same structure: coreModuleOutput.language/referencedata/translation/language/
                def coreDirectory = new File(exportDirectory + '/' + coreModuleOutput + '.' + language + '/referencedata/translation/' + language + '/')
                coreDirectory.mkdirs()
                def contributorsFile = new File(coreDirectory, 'CONTRIBUTORS_' + language + '.xml')
                def documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                def document = documentBuilder.newDocument()
                def root = document.createElement('Contributors')
                root.setAttribute('language', language)
                root.setTextContent(contributorsText)
                document.appendChild(root)
                def tf = TransformerFactory.newInstance()
                try { tf.setAttribute('indent-number', 2) } catch (Exception ignored) {}
                def transformer = tf.newTransformer()
                transformer.setOutputProperty(OutputKeys.ENCODING, 'UTF-8')
                transformer.setOutputProperty(OutputKeys.INDENT, 'yes')
                def source = new DOMSource(document)
                def result = new StreamResult(contributorsFile)
                transformer.transform(source, result)
                project.logger.lifecycle('Contributors file created: ' + contributorsFile.absolutePath)
            }
        } catch (Exception e) {
            project.logger.error('Error exporting contributors: ' + e.message, e)
        }
    }

    /**
     * Retrieves the contributors information for a specific language.
     * 
     * @param connection the database connection to use
     * @param language the language code to get contributors for
     * @return the contributors text, or null if not found
     */
    String getContributors(java.sql.Connection connection, String language) {
        def sql = 'select TranslatedBy from ad_language where ad_language = ?'
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, language)
        java.sql.ResultSet rs = stmt.executeQuery()
        String contributors = null
        if (rs.next()) contributors = rs.getString('TranslatedBy')
        rs.close(); stmt.close()
        return contributors
    }

    /**
     * Checks if a language has been initialized in the system.
     * 
     * <p>A language is considered initialized if there are translation records
     * in the AD_ELEMENT_TRL table for that language.</p>
     * 
     * @param connection the database connection to use
     * @param language the language code to check
     * @return true if the language is initialized, false otherwise
     */
    boolean isLanguageInitialized(java.sql.Connection connection, String language) {
        def sql = 'SELECT COUNT(*) as count FROM AD_ELEMENT_TRL WHERE AD_LANGUAGE = ?'
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, language)
        java.sql.ResultSet rs = stmt.executeQuery()
        boolean initialized = false
        if (rs.next()) initialized = rs.getInt('count') > 0
        rs.close(); stmt.close()
        return initialized
    }

    /**
     * Initializes a language in the Etendo system if it hasn't been initialized yet.
     * 
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Retrieves the language ID from the AD_LANGUAGE table</li>
     *   <li>Updates the language to be marked as a system language</li>
     *   <li>Generates a UUID for the process instance</li>
     *   <li>Creates a process instance record</li>
     *   <li>Executes the AD_LANGUAGE_CREATE stored procedure</li>
     * </ol>
     * 
     * @param connection the database connection to use
     * @param language the language code to initialize (e.g., "es_ES")
     * @throws IllegalStateException if the language is not found in AD_LANGUAGE table
     */
    void initializeLanguage(java.sql.Connection connection, String language) {
        project.logger.lifecycle("Initializing language: ${language}")

        // Step 1: Get language ID
        String languageId = getLanguageId(connection, language)
        if (!languageId) {
            throw new IllegalStateException("Language ${language} not found in AD_LANGUAGE table")
        }
        project.logger.lifecycle("Found language ID: ${languageId}")

        // Step 2: Update to set as system language
        updateSystemLanguage(connection, languageId)

        // Step 3: Get UUID for p_instance
        String uuid = getUUID(connection)
        project.logger.lifecycle("Generated UUID for p_instance: ${uuid}")

        // Step 4: Create p_instance record
        createPInstance(connection, uuid, languageId)

        // Step 5: Execute ad_language_create
        executeLanguageCreate(connection, languageId)

        project.logger.lifecycle("Language ${language} initialized successfully")
    }

    /**
     * Retrieves the internal language ID for a given language code.
     * 
     * @param connection the database connection to use
     * @param language the language code (e.g., "es_ES")
     * @return the language ID from AD_LANGUAGE table, or null if not found
     */
    String getLanguageId(java.sql.Connection connection, String language) {
        def sql = 'SELECT AD_LANGUAGE_ID FROM AD_LANGUAGE WHERE AD_LANGUAGE = ?'
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, language)
        java.sql.ResultSet rs = stmt.executeQuery()
        String languageId = null
        if (rs.next()) languageId = rs.getString('AD_LANGUAGE_ID')
        rs.close(); stmt.close()
        return languageId
    }

    /**
     * Updates a language to be marked as a system language.
     * 
     * @param connection the database connection to use
     * @param languageId the internal language ID to update
     */
    void updateSystemLanguage(java.sql.Connection connection, String languageId) {
        def sql = "UPDATE AD_LANGUAGE SET ISSYSTEMLANGUAGE = 'Y' WHERE AD_LANGUAGE_ID = ?"
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, languageId)
        int rowsAffected = stmt.executeUpdate()
        stmt.close()
        project.logger.lifecycle("Updated ${rowsAffected} rows to set language as system language")
    }

    /**
     * Generates a new UUID using the database's GET_UUID() function.
     * 
     * @param connection the database connection to use
     * @return a new UUID string
     */
    String getUUID(java.sql.Connection connection) {
        def sql = 'SELECT GET_UUID()'
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        java.sql.ResultSet rs = stmt.executeQuery()
        String uuid = null
        if (rs.next()) uuid = rs.getString(1)
        rs.close(); stmt.close()
        return uuid
    }

    /**
     * Creates a process instance record in AD_PINSTANCE table.
     * 
     * <p>This record is required for executing the AD_LANGUAGE_CREATE procedure.</p>
     * 
     * @param connection the database connection to use
     * @param uuid the UUID to use as the process instance ID
     * @param languageId the language ID to associate with the process instance
     */
    void createPInstance(java.sql.Connection connection, String uuid, String languageId) {
        def sql = """
            INSERT INTO AD_PINSTANCE
            (AD_PINSTANCE_ID, AD_PROCESS_ID, RECORD_ID, ISPROCESSING, CREATED, AD_USER_ID, UPDATED, RESULT, ERRORMsg, AD_CLIENT_ID, AD_ORG_ID, CREATEDBY, UPDATEDBY, ISACTIVE)
            VALUES (?, '179', ?, 'N', NOW(), '100', NOW(), 1, NULL, '0', '0', '100', '100', 'Y')
        """
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, uuid)
        stmt.setString(2, languageId)
        int rowsAffected = stmt.executeUpdate()
        stmt.close()
        project.logger.lifecycle("Created p_instance record with ID: ${uuid}")
    }

    /**
     * Executes the AD_LANGUAGE_CREATE stored procedure to initialize the language.
     * 
     * @param connection the database connection to use
     * @param languageId the language ID to initialize
     */
    void executeLanguageCreate(java.sql.Connection connection, String languageId) {
        def sql = 'SELECT AD_LANGUAGE_CREATE(?)'
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        stmt.setString(1, languageId)
        java.sql.ResultSet rs = stmt.executeQuery()
        if (rs.next()) {
            project.logger.lifecycle("Executed AD_LANGUAGE_CREATE for language ID: ${languageId}, result: ${rs.getString(1)}")
        }
        rs.close(); stmt.close()
    }

}

