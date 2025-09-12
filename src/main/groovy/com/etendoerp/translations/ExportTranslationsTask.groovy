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

    void exportTranslations(java.sql.Connection connection, String exportDirectory, String language, String clientId, boolean isReducedVersion, List<String> moduleList) {
        project.logger.lifecycle("Exporting translations for language: ${language}, client: ${clientId}, modules: ${moduleList}")
        def trlModulesTables = getTrlModulesTables(connection)
        project.logger.lifecycle("Found ${trlModulesTables.size()} translation tables to process")
        
        for (String tableName : trlModulesTables) {
            project.logger.lifecycle("Processing table: ${tableName}")
            exportModuleTranslations(connection, exportDirectory, clientId, language, tableName, isReducedVersion, moduleList)
        }
        
        exportReferenceDataTranslations(connection, exportDirectory, language, isReducedVersion, moduleList)
        exportContributors(connection, exportDirectory, language)
    }

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

    String getSystemVersion(java.sql.Connection connection) {
        def sql = 'SELECT OB_VERSION FROM AD_SYSTEM_INFO'
        java.sql.PreparedStatement stmt = connection.prepareStatement(sql)
        java.sql.ResultSet rs = stmt.executeQuery()
        String version = '1.0'
        if (rs.next()) version = rs.getString('OB_VERSION') ?: '1.0'
        rs.close(); stmt.close()
        return version
    }

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

}
