package com.calu;

import java.lang.reflect.Method;
import org.junit.Test;

// Offline desktop-JVM battery for CalcActivity.Expr + trim (no Android runtime).
public class EvalTest {
    static int fails = 0;
    static Method trim;

    @Test
    public void testBattery() throws Exception {
        main(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        trim = CalcActivity.class.getDeclaredMethod("trim", double.class);
        trim.setAccessible(true);

        eq("2+3*4", 14);            // BODMAS: * before +
        eq("(2+3)*4", 20);
        eq("10/4", 2.5);
        eq("-3^2", -9);             // unary binds looser than ^
        eq("2^-2", 0.25);
        eq("2^3^2", 512);           // ^ right-assoc
        eq("50%", 0.5);             // postfix /100
        eq("50%-25", -24.5);        // regression: '-' after '%' keeps the '%'
        eq("20+10%", 20.1);
        eq("SQRT16", 4);
        eq("SQRT9+16", 19);
        eq("2*SQRT9", 6);           // implicit '*' after digit
        eq("2*(3+4)", 14);          // press() inserts '*' for typed 2(
        eq("(5)*3", 15);            // implicit '*' after ')'
        eq("50%*0.5", 0.25);        // implicit '*' after '%'
        eq(".5", 0.5);
        eq("0.1+0.2", 0.30000000000000004);
        eq("1E16+1", 1.0E16);       // chained sci-notation result parses
        eq("9.999800001E9+1", 9999800002.0);
        eq("1E-3+0.002", 0.003);
        eq("2*3%*4", 0.24);         // 3% postfix then implicit product
        eq("-5+2", -3);

        // New scientific & typographic symbol tests
        eq("5!", 120);
        eq("0!", 1);
        eq("SIN(30)", 0.5);
        eq("COS(60)", 0.5);
        eq("COS(90)", 0.0);
        eq("TAN(45)", 1.0);
        eq("LN(e)", 1.0);
        eq("PI*2", Math.PI * 2);
        eq("\u221A81\u00D72\u00F73\u22121", 5); // √81 × 2 ÷ 3 − 1

        thr("1/0", "div0");
        thr("SQRT-4", "sqrt<0");
        thr("TAN(90)", "tan90");
        thr("LN(0)", "ln<=0");
        thr("2+", "trailing");
        thr("(1+2", "bracket");

        // trim(): display formatting, via reflection
        tTrim(0.30000000000000004, "0.3");
        tTrim(9999800001.0, "9999800001");
        tTrim(1.0E16 + 1, "10000000000000000");
        tTrim(0.5, "0.5");
        tTrim(-24.5, "-24.5");
        tTrim(123456.7890123456789, "123456.789012");
        tTrim(1.0E19, "");
        tTrim(0.0, "0");

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILURES");
        if (fails > 0) System.exit(1);
    }

    static void eq(String e, double want) {
        try {
            double got = CalcActivity.Expr.eval(e);
            boolean ok = Math.abs(got - want) <= 1e-10 * Math.max(1, Math.abs(want));
            System.out.println((ok ? "ok   " : "FAIL ") + e + " = " + got
                    + (ok ? "" : "  want " + want));
            if (!ok) fails++;
        } catch (Exception ex) {
            System.out.println("FAIL " + e + " threw " + ex);
            fails++;
        }
    }

    static void thr(String e, String desc) {
        try {
            double v = CalcActivity.Expr.eval(e);
            System.out.println("FAIL " + desc + " expected throw, got " + v);
            fails++;
        } catch (Exception ex) {
            System.out.println("ok   throws " + desc + " (" + ex + ")");
        }
    }

    static void tTrim(double d, String want) {
        String got;
        try { got = (String) trim.invoke(null, d); }
        catch (Exception ex) { got = "EX:" + ex; }
        boolean ok = want.isEmpty() ? !got.contains(",") : got.equals(want);
        System.out.println((ok ? "ok   trim(" + d + ") = " + got
                : "FAIL trim(" + d + ") = " + got + "  want " + want));
        if (!ok) fails++;
    }
}
