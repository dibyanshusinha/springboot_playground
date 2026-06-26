package com.dibyanshusinha.apiserv.e2e.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(E2EScenarios.class)
public @interface E2EScenario {

    String service();

    String method();

    String route();

    String workflow();

    String response();
}
