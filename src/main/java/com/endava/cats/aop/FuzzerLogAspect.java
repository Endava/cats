package com.endava.cats.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Aspect used to prefix logging with the right Fuzzer name
 */
@Aspect
@Component
public class FuzzerLogAspect {

    @Around("execution(* com.endava.cats.fuzzer.Fuzzer.fuzz(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        MDC.put("fuzzer", joinPoint.getTarget().getClass().getSimpleName());
        Object ret = joinPoint.proceed();
        MDC.put("fuzzer", "");
        return ret;
    }
}
