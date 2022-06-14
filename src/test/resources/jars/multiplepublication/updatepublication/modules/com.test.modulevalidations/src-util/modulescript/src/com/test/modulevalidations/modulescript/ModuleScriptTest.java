package com.test.modulevalidations.modulescript;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.sql.Connection;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;
import org.openbravo.modulescript.ModuleScriptExecutionLimits;
import org.openbravo.modulescript.OpenbravoVersion;

public class ModuleScriptTest extends ModuleScript {

  @Override
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();
      Connection conn = cp.getTransactionConnection();
      System.out.println("*** ModuleScriptTest modulevalidations");

    } catch (Exception e) {
      handleError(e);
    }
  }

}

