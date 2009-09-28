package org.doogal.core;

public interface Interpreter {
    void eval(String cmd, Object... args) throws EvalException;

    void eval() throws EvalException;
}