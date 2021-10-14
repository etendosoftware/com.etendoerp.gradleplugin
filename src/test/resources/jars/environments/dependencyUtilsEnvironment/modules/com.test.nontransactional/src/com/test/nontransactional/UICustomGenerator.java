package com.test.nontransactional;

import com.etendoerp.sequences.UINextSequenceValueInterface;
import com.etendoerp.sequences.annotations.SequenceFilter;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.model.ad.ui.Field;

@SequenceFilter("DE4B9F3CF90D4789A2DB2E6E2232051F")
public class UICustomGenerator implements UINextSequenceValueInterface {

    @Override
    public String generateNextSequenceValue(Field field, RequestContext requestContext) {
        return "nseq-custom-ui-gen";
    }
}
