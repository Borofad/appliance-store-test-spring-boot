package com.epam.rd.autocode.assessment.appliances.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Logs method calls without touching the business code.
 *
 * - Every public method of the service layer: DEBUG (entry, exit with duration, exceptions).
 * - Methods/classes annotated with {@link Loggable}: INFO.
 * - Any call slower than one second: WARN.
 *
 * Exceptions are logged here only as a one-line trace of the call. They are reported (with the
 * full stack trace for unexpected ones) by GlobalExceptionHandler, so errors are not logged twice at WARN/ERROR.
 */
@Slf4j
@Aspect
@Component
public class LoggingServices {

    private static final long SLOW_CALL_MILLIS = 1000;
    private static final int MAX_TEXT_LENGTH = 200;

    @Pointcut("execution(public * com.epam.rd.autocode.assessment.appliances.service.impl..*(..))")
    public void serviceMethods() {
    }

    @Pointcut("@annotation(com.epam.rd.autocode.assessment.appliances.aspect.Loggable) || @within(com.epam.rd.autocode.assessment.appliances.aspect.Loggable)")
    public void loggableMethods() {
    }

    @Around("serviceMethods() || loggableMethods()")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Loggable loggable = findLoggable(joinPoint);
        boolean explicit = loggable != null;

        // fast path: do no work when the level is switched off
        if (!(explicit ? log.isInfoEnabled() : log.isDebugEnabled())) {
            return joinPoint.proceed();
        }

        boolean showValues = !explicit || loggable.logArguments();
        String method = methodName(joinPoint);
        emit(explicit, ">> {}({})", method,
                showValues ? describeArguments(joinPoint.getArgs()) : "arguments hidden");

        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            emit(explicit, "<< {} returned {} in {} ms", method,
                    showValues ? abbreviate(result) : "result hidden", millisSince(start));
            return result;
        } catch (Throwable ex) {
            emit(explicit, "!! {} threw {}: {} after {} ms", method, ex.getClass().getSimpleName(),
                    showValues ? abbreviate(ex.getMessage()) : "message hidden", millisSince(start));
            throw ex;
        } finally {
            long elapsed = millisSince(start);
            if (elapsed > SLOW_CALL_MILLIS) {
                log.warn("Slow call: {} took {} ms", method, elapsed);
            }
        }
    }

    private void emit(boolean info, String message, Object... args) {
        if (info) {
            log.info(message, args);
        } else {
            log.debug(message, args);
        }
    }

    /** The annotation on the method (looked up on the real class, not the interface), else on the class. */
    private Loggable findLoggable(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        if (target == null) {
            return null;
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Method specific = AopUtils.getMostSpecificMethod(method, target.getClass());
        Loggable onMethod = AnnotatedElementUtils.findMergedAnnotation(specific, Loggable.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(target), Loggable.class);
    }

    private String methodName(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
    }

    private String describeArguments(Object[] args) {
        return Arrays.stream(args).map(this::abbreviate).collect(Collectors.joining(", "));
    }

    /** One short line: no line breaks (prevents log forging) and a length limit. */
    private String abbreviate(Object value) {
        String text = String.valueOf(value).replaceAll("[\\r\\n\\t]+", " ");
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) + "..." : text;
    }

    private long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}