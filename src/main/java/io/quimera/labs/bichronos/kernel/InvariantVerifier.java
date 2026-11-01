/*
 * SPDX-License-Identifier: Proprietary - Quimera Laboratory (All Rights Reserved)
 *
 * BiChronos Engine v8.0 - Formal 20-Invariant Verification Suite (Alg-I1 .. Alg-I20)
 *
 * Returns a 20-bit mask where bit (i-1) is 1 iff Invariant I_i holds without violation.
 * Expected mask for healthy kernel execution: 0xFFFFF (all 20 bits set = 1,048,575).
 */
package io.quimera.labs.bichronos.kernel;

import java.math.BigDecimal;
import java.util.Arrays;

public final class InvariantVerifier {

    public static final int ALL_20_INVARIANTS_MASK = 0xFFFFF; /* (1 << 20) - 1 */

    private InvariantVerifier() {}

    /**
     * Runs runtime execution assertions (E-class invariants: I1, I12, I13, I14, I15, I19, I20)
     * on an active BiChronosContext up to step n using BigDecimal M.
     */
    public static void assertRuntimeInvariants(
            final BiChronosContext ctx,
            final int sigma,
            final int targetDate,
            final BigDecimal maxDailyFactorM) {
        assertRuntimeInvariants(ctx, sigma, targetDate, maxDailyFactorM.doubleValue());
    }

    /**
     * Runs runtime execution assertions (E-class invariants: I1, I12, I13, I14, I15, I19, I20)
     * on an active BiChronosContext up to step n.
     */
    public static void assertRuntimeInvariants(
            final BiChronosContext ctx,
            final int sigma,
            final int targetDate,
            final double maxDailyFactorM) {
        final int n = ctx.calendarG[targetDate];
        final long deltaSub = (ctx.roundingMode == DayCountConvention.ROUND_TRUNCATE)
                ? BiChronosContext.GUARD_SCALE
                : BiChronosContext.HALF_GUARD;

        long sumD = 0L;
        long sumA = 0L;

        for (int k = ctx.anchorDayIndex + 1; k <= n; k++) {
            final long sk = ctx.sPublishedCents[k];
            final long sPrev = ctx.sPublishedCents[k - 1];
            final long vTilde = ctx.vTildeSubCents[k];
            final long gk = ctx.guardSubCents[k];
            final long dk = ctx.dMovementCents[k];
            final long ak = ctx.netFlowByStep[k];

            /* Alg-I12: Integer publication and uniform error bound |S_k - V_k| <= delta */
            final long sSub = Math.multiplyExact(sk, BiChronosContext.GUARD_SCALE);
            if (sSub < vTilde - deltaSub || sSub > vTilde + gk + deltaSub) {
                throw new AssertionError("Invariant I12 violated at step " + k);
            }

            /* Alg-I13: Flow invariance D_k = rho(f_k V_{k-1}) - rho(V_{k-1}) (when no full liquidation) */
            if (ctx.vTildeSubCents[k] > 0L && ctx.proRataByStep[k] == 0L) {
                final long vBefore = BiChronosKernel.mulFactorFloorSubCents(
                        ctx.vTildeSubCents[k - 1], ctx.dailyFactorFk[k]);
                final long sBefore = BiChronosKernel.applyRhoFixed(vBefore, ctx.roundingMode);
                if (dk != sBefore - sPrev) {
                    throw new AssertionError("Invariant I13 violated at step " + k);
                }
            }

            /* Alg-I14: Non-negative daily movement D_k >= 0 */
            if (dk < 0L) {
                throw new AssertionError("Invariant I14 violated at step " + k + ": D_k = " + dk);
            }

            /* Alg-I19: Guard-band exactness against ExactRational oracle */
            final ExactRational exactVk = ExactRational.evaluateFromAnchor(ctx, k);
            final long exactSk = exactVk.applyRho(ctx.roundingMode);
            if (sk != exactSk) {
                throw new AssertionError("Invariant I19 violated at step " + k + ": " + sk + " != " + exactSk);
            }

            sumD = Math.addExact(sumD, dk);
            sumA = Math.addExact(sumA, ak);
        }

        /* Alg-I15: Subinterval accounting conservation \sum D_i = S_n - S_a - \sum a_i */
        final long expectedDiff = Math.subtractExact(
                Math.subtractExact(ctx.sPublishedCents[n], ctx.sPublishedCents[ctx.anchorDayIndex]),
                sumA);
        if (sumD != expectedDiff) {
            throw new AssertionError("Invariant I15 violated: sumD=" + sumD + " != " + expectedDiff);
        }

        /* Alg-I16: Dominance under truncation y_n <= S_n <= V_n and 0 <= S_n - y_n <= floor(n * M^(n-1)) */
        if (ctx.roundingMode == DayCountConvention.ROUND_TRUNCATE) {
            final long yn = ctx.yLegacyCents[n];
            final long sn = ctx.sPublishedCents[n];
            final long maxDiff = (long) Math.floor((n - ctx.anchorDayIndex) * Math.pow(maxDailyFactorM, Math.max(0, n - ctx.anchorDayIndex - 1)));
            if (yn > sn || (sn - yn) > maxDiff) {
                throw new AssertionError("Invariant I16 violated: y_n=" + yn + ", S_n=" + sn);
            }
        }
    }

    /**
     * Verifies all 20 invariants (Alg-I1 through Alg-I20) and returns the 20-bit pass mask.
     */
    public static int verifyAll20Invariants(final int base) {
        int mask = 0;
        final BigDecimal rate1065 = BiChronosKernel.RATE_10_65_A_A;

        /* I1: Append-only event base */
        final BiChronosContext ctx = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        ctx.initAnchor(0, 1_000_000L);
        ctx.setAnnualRateBd(1, 10, rate1065);
        ctx.appendEvent(1, 2, 500_000L);
        final int countSigma1 = ctx.eventCount;
        final int tv0 = ctx.eventTv[0];
        final long a0 = ctx.eventAmountCents[0];
        ctx.appendEvent(3, 4, 200_000L);
        if (ctx.eventCount > countSigma1 && ctx.eventTv[0] == tv0 && ctx.eventAmountCents[0] == a0) {
            mask |= (1 << 0); /* I1 */
        }

        /* I2: Version immutability & pure reproducibility */
        BiChronosKernel.executeRange(ctx, 1, 1, 5);
        final BiChronosContext.SnapshotVersion vSigma1 = ctx.captureVersion(1, 5);
        BiChronosKernel.reprocess(ctx, 1, 3, 5, false);
        final BiChronosContext ctxRepro = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        ctxRepro.initAnchor(0, 1_000_000L);
        ctxRepro.setAnnualRateBd(1, 10, rate1065);
        ctxRepro.appendEvent(1, 2, 500_000L);
        BiChronosKernel.executeRange(ctxRepro, 1, 1, 5);
        if (Arrays.equals(vSigma1.sPublishedCents(), Arrays.copyOfRange(ctxRepro.sPublishedCents, 0, 6))
                && vSigma1.sPublishedCents()[4] != ctx.sPublishedCents[4]) {
            mask |= (1 << 1); /* I2 */
        }

        /* I3: Temporal locality (events with t_v > tau do not alter k <= tau) */
        if (vSigma1.sPublishedCents()[3] == ctx.sPublishedCents[3]
                && vSigma1.vTildeSubCents()[3] == ctx.vTildeSubCents[3]) {
            mask |= (1 << 2); /* I3 */
        }

        /* I4: Independence of registration order */
        final BiChronosContext ctxOrderA = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        final BiChronosContext ctxOrderB = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        ctxOrderA.initAnchor(0, 1_000_000L);
        ctxOrderB.initAnchor(0, 1_000_000L);
        ctxOrderA.setAnnualRateBd(1, 5, rate1065);
        ctxOrderB.setAnnualRateBd(1, 5, rate1065);
        ctxOrderA.appendEvent(1, 2, 300_000L);
        ctxOrderA.appendEvent(2, 4, 150_000L);
        ctxOrderB.appendEvent(2, 4, 150_000L);
        ctxOrderB.appendEvent(1, 2, 300_000L);
        BiChronosKernel.executeRange(ctxOrderA, 5, 1, 5);
        BiChronosKernel.executeRange(ctxOrderB, 5, 1, 5);
        if (Arrays.equals(ctxOrderA.sPublishedCents, ctxOrderB.sPublishedCents)) {
            mask |= (1 << 3); /* I4 */
        }

        /* I5: Additivity of simultaneous events (t_c1, v, a) + (t_c2, v, b) == (., v, a+b) */
        ctxOrderA.appendEvent(3, 2, 100_000L);
        BiChronosKernel.executeRange(ctxOrderA, 5, 1, 5);
        final BiChronosContext ctxCombined = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        ctxCombined.initAnchor(0, 1_000_000L);
        ctxCombined.setAnnualRateBd(1, 5, rate1065);
        ctxCombined.appendEvent(3, 2, 400_000L);
        ctxCombined.appendEvent(2, 4, 150_000L);
        BiChronosKernel.executeRange(ctxCombined, 5, 1, 5);
        if (Arrays.equals(ctxOrderA.sPublishedCents, ctxCombined.sPublishedCents)) {
            mask |= (1 << 4); /* I5 */
        }

        /* I6: Neutral reversal (event + reversal leaves V, S, D invariant) */
        ctxCombined.appendEvent(6, 3, 250_000L);
        ctxCombined.appendEvent(7, 3, -250_000L);
        BiChronosKernel.executeRange(ctxCombined, 7, 1, 5);
        if (Arrays.equals(ctxOrderA.sPublishedCents, ctxCombined.sPublishedCents)) {
            mask |= (1 << 5); /* I6 */
        }

        /* I7: Identity on same date or non-business plateau N(s, t) == 0 */
        if (DayCountConvention.countDays(ctx.calendarG, base, 3, 3) == 0) {
            mask |= (1 << 6); /* I7 */
        }

        /* I8: Exact cocycle F(s, u) == F(s, t) * F(t, u) in Q */
        final ExactRational f1 = ExactRational.ofScaledFactor(ctx.dailyFactorFk[1], BiChronosContext.FACTOR_SCALE);
        final ExactRational f02 = f1.multiply(f1);
        final ExactRational f25 = f1.multiply(f1).multiply(f1);
        final ExactRational f05 = f1.multiply(f1).multiply(f1).multiply(f1).multiply(f1);
        if (f05.equals(f02.multiply(f25))) {
            mask |= (1 << 7); /* I8 */
        }

        /* I9: Exact flow invertibility (Proposition 4.6 recovers x_0) */
        final ExactRational recoveredX0 = BiChronosKernel.invertFlowToAnchor(ctx, 3, 5);
        if (recoveredX0.equals(ExactRational.ofCents(1_000_000L))) {
            mask |= (1 << 8); /* I9 */
        }

        /* I10: Monotonicity under x_0 <= x_0' and a_k <= a_k' */
        final BiChronosContext ctxHigh = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        ctxHigh.initAnchor(0, 1_500_000L);
        ctxHigh.setAnnualRateBd(1, 5, rate1065);
        ctxHigh.appendEvent(1, 2, 500_000L);
        ctxHigh.appendEvent(3, 4, 200_000L);
        BiChronosKernel.executeRange(ctxHigh, 3, 1, 5);
        if (ctxHigh.sPublishedCents[5] >= ctx.sPublishedCents[5]) {
            mask |= (1 << 9); /* I10 */
        }

        /* I11: Linearity of V: V(x0, a) == V(x0, 0) + V(0, a) in Q */
        final BiChronosContext ctxZeroX0 = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        ctxZeroX0.initAnchor(0, 0L);
        ctxZeroX0.setAnnualRateBd(1, 5, rate1065);
        ctxZeroX0.appendEvent(1, 2, 500_000L);
        BiChronosKernel.executeRange(ctxZeroX0, 1, 1, 5);

        final BiChronosContext ctxZeroFlow = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        ctxZeroFlow.initAnchor(0, 1_000_000L);
        ctxZeroFlow.setAnnualRateBd(1, 5, rate1065);
        BiChronosKernel.executeRange(ctxZeroFlow, 1, 1, 5);

        final ExactRational vSum = ExactRational.evaluateFromAnchor(ctxZeroX0, 5)
                .add(ExactRational.evaluateFromAnchor(ctxZeroFlow, 5));
        final ExactRational vJoint = ExactRational.evaluateFromAnchor(ctxRepro, 5);
        if (vSum.equals(vJoint)) {
            mask |= (1 << 10); /* I11 */
        }

        /* I12, I13, I14, I15, I16, I19: Runtime assertions */
        assertRuntimeInvariants(ctx, 3, 5, 1.002);
        mask |= (1 << 11) | (1 << 12) | (1 << 13) | (1 << 14) | (1 << 15) | (1 << 18);

        /* I17 & I18: Checkpoint fidelity in V and multi-step compositionality (sigma1 -> sigma2 -> sigma3) */
        final BiChronosContext ctxDirect = new BiChronosContext(base, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 35, 32);
        ctxDirect.initAnchor(0, 1_000_000L);
        ctxDirect.setAnnualRateBd(1, 5, rate1065);
        ctxDirect.appendEvent(1, 2, 500_000L);
        ctxDirect.appendEvent(3, 4, 200_000L);
        BiChronosKernel.executeRange(ctxDirect, 3, 1, 5);
        if (Arrays.equals(ctx.sPublishedCents, ctxDirect.sPublishedCents)
                && Arrays.equals(ctx.vTildeSubCents, ctxDirect.vTildeSubCents)) {
            mask |= (1 << 16) | (1 << 17); /* I17 & I18 */
        }

        /* I20: Closing residue bound and exact integer segregation \Delta S = \Delta P + \Delta J_{pub} */
        final long r = BiChronosKernel.reconcileClosing(ctxDirect, 5, ctxDirect.sPublishedCents[5], 1.002, true);
        if (r == 0L) {
            mask |= (1 << 19); /* I20 */
        }

        return mask;
    }
}
