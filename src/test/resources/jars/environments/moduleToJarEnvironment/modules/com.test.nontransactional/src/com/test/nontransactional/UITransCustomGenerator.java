package com.test.nontransactional;

import com.etendoerp.sequences.UINextSequenceValueInterface;
import com.etendoerp.sequences.annotations.SequenceFilter;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.model.ad.ui.Field;

@SequenceFilter("FD0F7402E657413B809827804FC5D325")
public class UITransCustomGenerator implements UINextSequenceValueInterface {
    @Override
    public String generateNextSequenceValue(Field field, RequestContext requestContext) {
        return "ui-custom-trans-generator";
    }
}
