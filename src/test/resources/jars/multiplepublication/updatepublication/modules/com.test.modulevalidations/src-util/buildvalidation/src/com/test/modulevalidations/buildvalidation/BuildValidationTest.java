package com.test.modulevalidations.buildvalidation;

import java.util.ArrayList;
import java.util.List;

import org.openbravo.base.ExecutionLimits;
import org.openbravo.buildvalidation.BuildValidation;
import org.openbravo.modulescript.OpenbravoVersion;

public class BuildValidationTest extends BuildValidation {

  @Override
  public List<String> execute() {
    ArrayList<String> errors = new ArrayList<String>();
    try {
      System.out.println("*** BuildValidationTest modulevalidations");
    } catch (Exception e) {
      return handleError(e);
    }
    return errors;
  }

}
