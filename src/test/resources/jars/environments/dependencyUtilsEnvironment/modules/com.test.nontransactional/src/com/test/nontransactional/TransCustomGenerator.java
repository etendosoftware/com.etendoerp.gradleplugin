package com.test.nontransactional;

import com.etendoerp.sequences.DefaultSequenceGenerator;
import org.hibernate.Session;

public class TransCustomGenerator extends DefaultSequenceGenerator {
    public TransCustomGenerator(String propertyValue) {
        super(propertyValue);
    }

    @Override
    public String generateValue(Session session, Object owner) {
        return "custom-transactional-generator";
    }
}
