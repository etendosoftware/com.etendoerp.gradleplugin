package com.test.nontransactional;

import com.etendoerp.sequences.DefaultSequenceGenerator;
import org.hibernate.Session;

public class CustomGenerator extends DefaultSequenceGenerator {
    public CustomGenerator(String propertyValue) {
        super(propertyValue);
    }

    @Override
    public String generateValue(Session session, Object owner) {
        return "nseq-custom-gen";
    }
}
