/*
 * @author: H. Haven Liu, THe MITRE Corporation
 */

package org.mitre.bch.cath.simulation.utils;

import org.mitre.bch.cath.simulation.model.CathLabSim;
import org.mitre.bch.cath.simulation.model.CathSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Stream;

/** Helper function for logger
 *
 */
public class LoggerHelper {
    /** Logger object */
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerHelper.class);
    /** CathLabSim instance of the simulation */
    private CathLabSim model;

    /** Base Format string */
    private String baseFormat = "{}({}) - {}({}) - ";

    /** Constructor of the LoggerHelper
     *
     * @param model CathLabSim instance of the simulation
     */
    public LoggerHelper(CathLabSim model) {
        this.model = model;
    }

    /** Log message of INFO level
     * This appends simulation time to the logging message, otherwise mirrors the Logger.info signature
     * @param format Logger format string
     * @param arguments Logger vararg arguments
     */
    public void info(int stackIndex, String format, Object... arguments) {
        String fmt = baseFormat + format;
        Object[] firstPart = getClassMethodStack(stackIndex);
        Object[] combArgs = Stream.concat(Arrays.stream(firstPart), Arrays.stream(arguments)).toArray(Object[]::new);
        LOGGER.info(fmt, combArgs);

    }

    /** Log message of INFO level
     * This appends simulation time to the logging message, otherwise mirrors the Logger.info signature
     * @param format Logger format string
     * @param arguments Logger vararg arguments
     */
    public void info(String format, Object... arguments) {
        this.info(5, format, arguments);
    }

    /**
     * Get Class name, method name, line number of an element in the stacktrace
     * as well as the time in the simulation
     * @param stackIndex the index of the element in the stack
     * @param formatted whether return formatted string[] or raw elements
     * @return Class name, method name, line number, time in simulation
     */
    public Object[] getClassMethodStack(int stackIndex, boolean formatted) {
        StackTraceElement el = Thread.currentThread().getStackTrace()[stackIndex];
        String[] classNameParts = el.getClassName().split("\\.");
        String className = classNameParts[classNameParts.length - 1];
        double time = this.model.schedule.getTime();
        String methodName = el.getMethodName().startsWith("lambda$") ? el.getMethodName().substring(7, el.getMethodName().length() - 2) : el.getMethodName();
        String classMethod = String.format("%s.%s",className, methodName);

        return formatted ?
                new Object[]{String.format("%-22s", classMethod), String.format("%3s", String.valueOf(el.getLineNumber())), String.format("% 6d", (int) time), toTime(time)} :
                new Object[]{className, methodName, el.getLineNumber(), time} ;
    }


    /**
     * Get Class name, method name, line number of an element in the stacktrace
     * as well as the time in the simulation
     * @param stackIndex the index of the element in the stack
     * @return Class name, method name, line number, time in simulation
     */
    private Object[] getClassMethodStack(int stackIndex) {
        return getClassMethodStack(stackIndex, true);
    }

    /** Log message of ERROR level
     * This appends simulation time to the logging message, otherwise mirrors the Logger.error signature
     * @param format Logger format string
     * @param arguments Logger vararg arguments
     */
    public void error(String format, Object... arguments) {
        StackTraceElement el = Thread.currentThread().getStackTrace()[2];
        String fmt = baseFormat + format;
        String[] classNameParts = el.getClassName().split("\\.");
        String className = classNameParts[classNameParts.length - 1];
        double time = this.model.schedule.getTime();
        String methodName = el.getMethodName().startsWith("lambda$") ? el.getMethodName().substring(7, el.getMethodName().length() - 2) : el.getMethodName();
        String classMethod = String.format("%s.%s",className, methodName);
        Object[] firstPart = {String.format("%-22s", classMethod), String.format("%3s", String.valueOf(el.getLineNumber())), String.format("% 6d", (int) time), toTime(time)};
        Object[] combArgs = Stream.concat(Arrays.stream(firstPart), Arrays.stream(arguments)).toArray(Object[]::new);
        LOGGER.error(fmt, combArgs);
    }

    public static class CustomException extends Exception {
        public CustomException(String msg) {
            super(msg);
        }
    }

    /**
     * Print stack from a function
     */
    public void stack(){
        for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
            LOGGER.info("{} {} {}", el.getClassName(), el.getMethodName(), el.getLineNumber());
        }
    }

    /** Convert simulation time (double) to a "day hour:min" string
     *
     * @param time simulation time
     * @return "day hour:min" string
     */
    public static String toTime(double time){
        int day = (int) time / CathSchedule.MIN_PER_DAY;
        int hour = ((int) time - day * CathSchedule.MIN_PER_DAY) / 60;
        int minute = (int) time - day * CathSchedule.MIN_PER_DAY - hour * 60;

        return String.format("%d %02d:%02d", day, hour, minute);
    }
}
