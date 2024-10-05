package de.rsh.rycst.utils;

public final class MathUtils {
    public static double lerp(double a, double b, double p) {
    return (1 - p) * a + p * b;
  } 
}
