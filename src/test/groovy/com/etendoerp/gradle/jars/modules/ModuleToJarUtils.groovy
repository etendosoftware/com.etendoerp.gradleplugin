package com.etendoerp.gradle.jars.modules

class ModuleToJarUtils {

    final static String AD_MODULE_LOCATION = "src-db/database/sourcedata/"
    final static String AD_MODULE_FILE = "AD_MODULE.xml"

    static void createADModuleFile(Map map=[:]) {
        def baseLocation = map.baseLocation ?: ""
        def moduleLocation = map.moduleLocation ?: AD_MODULE_LOCATION
        Map moduleProperties = map.moduleProperties as Map

        File finalLocation = new File("${baseLocation}/${moduleLocation}")

        if (!finalLocation.exists()) {
            finalLocation.mkdirs()
        }

        def adModuleFile = new File("${finalLocation.absolutePath}/${AD_MODULE_FILE}")
        adModuleFile.createNewFile()
        adModuleFile << generateADModule(moduleProperties)
    }

    static String generateADModule(Map map=[:]) {

        def name        = map.name        ?: ""
        def version     = map.version     ?: ""
        def description = map.description ?: ""
        def license     = map.license     ?: ""
        def javapackage = map.javapackage ?: ""
        def author      = map.author      ?: ""

         return """<?xml version='1.0' encoding='UTF-8'?>
            <data>
                <!--BB7C83119ECA418599BF60BF89D235E2--><AD_MODULE>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <AD_MODULE_ID><![CDATA[BB7C83119ECA418599BF60BF89D235E2]]></AD_MODULE_ID>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <AD_CLIENT_ID><![CDATA[0]]></AD_CLIENT_ID>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <AD_ORG_ID><![CDATA[0]]></AD_ORG_ID>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <ISACTIVE><![CDATA[Y]]></ISACTIVE>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <NAME><![CDATA[${name}]]></NAME>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <VERSION><![CDATA[${version}]]></VERSION>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <DESCRIPTION><![CDATA[${description}]]></DESCRIPTION>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <TYPE><![CDATA[M]]></TYPE>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <LICENSE><![CDATA[${license}]]></LICENSE>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <JAVAPACKAGE><![CDATA[${javapackage}]]></JAVAPACKAGE>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <LICENSETYPE><![CDATA[OBPL]]></LICENSETYPE>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <AUTHOR><![CDATA[${author}]]></AUTHOR>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <ISTRANSLATIONREQUIRED><![CDATA[N]]></ISTRANSLATIONREQUIRED>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <AD_LANGUAGE><![CDATA[en_US]]></AD_LANGUAGE>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <HASCHARTOFACCOUNTS><![CDATA[N]]></HASCHARTOFACCOUNTS>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <ISTRANSLATIONMODULE><![CDATA[N]]></ISTRANSLATIONMODULE>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <HASREFERENCEDATA><![CDATA[N]]></HASREFERENCEDATA>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <ISCOMMERCIAL><![CDATA[N]]></ISCOMMERCIAL>
                <!--BB7C83119ECA418599BF60BF89D235E2-->  <ISTRIALALLOWED><![CDATA[N]]></ISTRIALALLOWED>
                <!--BB7C83119ECA418599BF60BF89D235E2--></AD_MODULE>
            </data>
        """
    }

}
