/*
 * SPDX-License-Identifier: Proprietary - Quimera Laboratory (All Rights Reserved)
 *
 * BiChronos Engine v8.0 - Financial Time & Caratheodory Plateau Convention
 *
 * "Bad programmers worry about the code. Good programmers worry about
 *  data structures and their relationships." - Linus Torvalds
 *
 * BigDecimal is used at the contract boundary (parsing B3/ANBIMA rates,
 * truncating daily factors to exact decimal places p, and formatting audit reports)
 * without polluting the L1-cache primitive arrays in the inner loop.
 */
package io.quimera.labs.bichronos.kernel;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class DayCountConvention {

    public static final int BASE_252 = 252;
    public static final int BASE_360 = 360;
    public static final int BASE_365 = 365;

    /* Rounding policy flags (Definition 5.3) */
    public static final int ROUND_TRUNCATE = 0;   /* rho(x) = floor(x), delta = 1 */
    public static final int ROUND_HALF_UP  = 1;   /* rho(x) = floor(x + 1/2), delta = 1/2 */

    /* Partial withdrawal modalities (Hypothesis H3(iii)) */
    public static final int WITHDRAW_TRANSLATION = 0; /* Modality A: integer translation */
    public static final int WITHDRAW_PRO_RATA    = 1; /* Modality B: proportional (1 - alpha_k) */

    /* IEEE 754R Decimal128 MathContext (34 decimal digits) for regulatory factor calculation */
    public static final MathContext MC_REGULATORY = MathContext.DECIMAL128;
    public static final BigDecimal BD_HUNDRED     = new BigDecimal("100");

    private DayCountConvention() {}

    /**
     * Maps internal BiChronos rounding flag to standard java.math.RoundingMode.
     */
    public static RoundingMode toJavaRoundingMode(final int roundingMode) {
        return (roundingMode == ROUND_TRUNCATE) ? RoundingMode.FLOOR : RoundingMode.HALF_UP;
    }

    /**
     * Computes N(s, t) = g[t] - g[s] using a flat precomputed accumulation index table.
     * For B in {360, 365} with Actual/B, g == null is allowed and N(s, t) = t - s.
     */
    public static int countDays(final int[] gTable, final int base, final int s, final int t) {
        if (base == BASE_360 || base == BASE_365) {
            return gTable != null ? (gTable[t] - gTable[s]) : (t - s);
        }
        return gTable[t] - gTable[s];
    }

    /**
     * Converts an already published B3/ANBIMA daily factor BigDecimal (e.g. new BigDecimal("1.00040167"))
     * into the unscaled fixed-point kernel representation (scaled by 10^p, truncated with RoundingMode.DOWN).
     */
    public static long factorFromBigDecimal(final BigDecimal factor, final int scaleDecimalsP) {
        return factor.setScale(scaleDecimalsP, RoundingMode.DOWN)
                .movePointRight(scaleDecimalsP)
                .longValueExact();
    }

    /**
     * Converts an internal scaled factor back into an exact BigDecimal with scale p (e.g. 8 decimals).
     */
    public static BigDecimal factorToBigDecimal(final long fkScaled, final int scaleDecimalsP) {
        return BigDecimal.valueOf(fkScaled, scaleDecimalsP);
    }

    /**
     * Computes the exact n-th root of a BigDecimal value using Newton-Raphson iteration
     * in the given MathContext (e.g. DECIMAL128 with 34 decimal places), avoiding IEEE 754 double precision loss.
     */
    public static BigDecimal computeNthRootBd(final BigDecimal value, final int n, final MathContext mc) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cannot compute root of negative value: " + value);
        }
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (n == 1) {
            return value;
        }

        /* Initial approximation using double pow */
        final double initialGuess = Math.pow(value.doubleValue(), 1.0 / n);
        BigDecimal x = BigDecimal.valueOf(initialGuess);
        final BigDecimal bigN = BigDecimal.valueOf(n);
        final BigDecimal bigNMinus1 = BigDecimal.valueOf(n - 1);

        /* Newton-Raphson iteration: x_{k+1} = (1/n) * ((n-1)*x_k + value / x_k^{n-1}) */
        for (int iter = 0; iter < 10; iter++) {
            final BigDecimal xPowNMinus1 = x.pow(n - 1, mc);
            final BigDecimal term = value.divide(xPowNMinus1, mc);
            final BigDecimal nextX = bigNMinus1.multiply(x, mc).add(term, mc).divide(bigN, mc);
            if (nextX.subtract(x).abs().compareTo(new BigDecimal("1e-32")) <= 0) {
                x = nextX;
                break;
            }
            x = nextX;
        }
        return x;
    }

    /**
     * Computes the truncated daily factor f_k = floor(10^p * (1 + i_annual)^(1/B)) / 10^p
     * from an annual percentage rate BigDecimal (e.g. new BigDecimal("10.65") for 10.65% a.a.),
     * returning the exact BigDecimal truncated to p decimal places (B3/ANBIMA convention)
     * using exact Decimal128 Newton-Raphson root evaluation.
     */
    public static BigDecimal computeTruncatedDailyFactorBd(
            final BigDecimal annualRatePercent,
            final int base,
            final int scaleDecimalsP) {
        final BigDecimal onePlusI = BigDecimal.ONE.add(
                annualRatePercent.divide(BD_HUNDRED, MC_REGULATORY), MC_REGULATORY);
        /* High-precision Decimal128 root evaluation then exact decimal truncation via BigDecimal */
        final BigDecimal root = computeNthRootBd(onePlusI, base, MC_REGULATORY);
        return root.setScale(scaleDecimalsP, RoundingMode.DOWN);
    }

    /**
     * Computes the truncated daily factor directly as a kernel fixed-point long (scaled by 10^p)
     * from an annual percentage rate BigDecimal.
     */
    public static long computeTruncatedDailyFactor(
            final BigDecimal annualRatePercent,
            final int base,
            final int scaleDecimalsP) {
        return computeTruncatedDailyFactorBd(annualRatePercent, base, scaleDecimalsP)
                .movePointRight(scaleDecimalsP)
                .longValueExact();
    }

    /**
     * Computes the B3 16-decimal truncated accumulated factor (Observation 5.2):
     * 	ilde{F}_{16}(s, t) = floor(10^16 * \prod_{k=s+1}^t f_k) / 10^16 using exact BigDecimal product.
     */
    public static BigDecimal computeB3AccumulatedFactor16(
            final long[] dailyFactorFk,
            final int scaleDecimalsP,
            final int fromStepExclusive,
            final int toStepInclusive) {
        BigDecimal acc = BigDecimal.ONE;
        for (int k = fromStepExclusive + 1; k <= toStepInclusive; k++) {
            final BigDecimal fk = BigDecimal.valueOf(dailyFactorFk[k], scaleDecimalsP);
            acc = acc.multiply(fk);
        }
        return acc.setScale(16, RoundingMode.DOWN);
    }
}
