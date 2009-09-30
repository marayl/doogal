package org.doogal.core;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import javax.mail.internet.ParseException;

public interface Doogal extends Closeable, Interpreter {
    void readConfig(final File config) throws EvalException, IOException,
            ParseException;

    void readConfig() throws EvalException, IOException, ParseException;

    void setDefault(String def);
}
