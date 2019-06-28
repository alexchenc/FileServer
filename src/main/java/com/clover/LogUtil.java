package com.clover;

import org.apache.log4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

public class LogUtil {

    public static void error(Logger logger, String message, Throwable t) {
        if (t != null) {
            if (message != null) {
//                logger.error(message + t.getMessage(), t);
                System.out.println(message);
            }
            else {
//                logger.error(t.getMessage(), t);
                System.out.println(t.getMessage());
            }
        }
        else {
            if (message != null) {
//                logger.error(message);
                System.out.println(message);
            }
        }
    }

    public static void error(Logger logger, Throwable t) {
        LogUtil.error(logger, null, t);
    }

    public static void error(Logger logger, String message) {
        LogUtil.error(logger, message, null);
    }

    private static String getStackTrace(Throwable t)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try
        {
            t.printStackTrace(pw);
            return sw.toString();
        }
        finally
        {
            pw.close();
        }
    }
}
