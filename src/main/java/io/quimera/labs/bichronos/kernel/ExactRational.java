/*
 * SPDX-License-Identifier: Proprietary - Quimera Laboratory (All Rights Reserved)
 *
 * BiChronos Engine v8.0 - Exact Rational Fallback Oracle (Proposition 5.13 & Step 3)
 *
 * Only invoked when Ziv's guard band [V_tilde_k, V_tilde_k + g_k] straddles a rounding
 * jump threshold of rho (probability < 2.2e-11 for q=12, n=21).
 * Bridges seamlessly with BigDecimal for exact decimal inspection and audit logs.
 */
package io.quimera.labs.bichronos.kernel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

public final class ExactRational implements Comparable<ExactRational> {

    public static final ExactRational ZERO = new ExactRational(BigInteger.ZERO, BigInteger.ONE);
    public static final ExactRational ONE  = new ExactRational(BigInteger.ONE, BigInteger.ONE);

    public final BigInteger num;
    public final BigInteger den;

    public ExactRational(final BigInteger num, final BigInteger den) {
        if (den.signum() == 0) {
            throw new ArithmeticException("Zero denominator in ExactRational");
        }
        if (num.signum() == 0) {
            this.num = BigInteger.ZERO;
            this.den = BigInteger.ONE;
            return;
        }
        final BigInteger gcd = num.gcd(den);
        if (den.signum() > 0) {
            this.num = num.divide(gcd);
            this.den = den.divide(gcd);
        } else {
            this.num = num.negate().divide(gcd);
            this.den = den.negate().divide(gcd);
        }
    }

    public static ExactRational ofCents(final long cents) {
        return new ExactRational(BigInteger.valueOf(cents), BigInteger.ONE);
    }

    public static ExactRational ofScaledFactor(final long fkScaled, final long scaleP) {
        return new ExactRational(BigInteger.valueOf(fkScaled), BigInteger.valueOf(scaleP));
    }

    /**
     * Constructs an ExactRational from any exact BigDecimal (e.g. a daily factor or monetary amount).
     */
    public static ExactRational ofBigDecimal(final BigDecimal bd) {
        final BigDecimal stripped = bd.stripTrailingZeros();
        final int scale = stripped.scale();
        if (scale >= 0) {
            return new ExactRational(stripped.unscaledValue(), BigInteger.TEN.pow(scale));
        }
        return new ExactRational(
                stripped.unscaledValue().multiply(BigInteger.TEN.pow(-scale)),
                BigInteger.ONE);
    }

    /**
     * Converts this ExactRational to a BigDecimal with the specified scale and RoundingMode.
     * Note: When denominator is a power of 10 (like 10^{p*k} in our financial recursion),
     * toBigDecimal(p*k, RoundingMode.UNNECESSARY) is 100% exact without any rounding!
     */
    public BigDecimal toBigDecimal(final int scale, final RoundingMode roundingMode) {
        return new BigDecimal(this.num).divide(new BigDecimal(this.den), scale, roundingMode);
    }

    public BigDecimal toBigDecimal(final MathContext mc) {
        return new BigDecimal(this.num).divide(new BigDecimal(this.den), mc);
    }

    public ExactRational add(final ExactRational other) {
        final BigInteger n = this.num.multiply(other.den).add(other.num.multiply(this.den));
        final BigInteger d = this.den.multiply(other.den);
        return new ExactRational(n, d);
    }

    public ExactRational subtract(final ExactRational other) {
        final BigInteger n = this.num.multiply(other.den).subtract(other.num.multiply(this.den));
        final BigInteger d = this.den.multiply(other.den);
        return new ExactRational(n, d);
    }

    public ExactRational multiply(final ExactRational other) {
        return new ExactRational(this.num.multiply(other.num), this.den.multiply(other.den));
    }

    public ExactRational divide(final ExactRational other) {
        return new ExactRational(this.num.multiply(other.den), this.den.multiply(other.num));
    }

    /**
     * Applies rounding operator rho: R -> Z (Definition 5.3) in exact rational arithmetic.
     */
    public long applyRho(final int roundingMode) {
        if (roundingMode == DayCountConvention.ROUND_TRUNCATE) {
            return floor();
        }
        /* ROUND_HALF_UP: floor(x + 1/2) = floor((2*num + den) / (2*den)) */
        final BigInteger twoNumPlusDen = this.num.shiftLeft(1).add(this.den);
        final BigInteger twoDen = this.den.shiftLeft(1);
        BigInteger[] qr = twoNumPlusDen.divideAndRemainder(twoDen);
        if (qr[1].signum() < 0) {
            return qr[0].subtract(BigInteger.ONE).longValueExact();
        }
        return qr[0].longValueExact();
    }

    public long floor() {
        BigInteger[] qr = this.num.divideAndRemainder(this.den);
        if (qr[1].signum() < 0) {
            return qr[0].subtract(BigInteger.ONE).longValueExact();
        }
        return qr[0].longValueExact();
    }

    /**
     * Evaluates exact V_target from anchor k_a using exact rational recursion (Step 3 fallback),
     * including target step flow by default.
     */
    public static ExactRational evaluateFromAnchor(
            final BiChronosContext ctx,
            final int targetStep) {
        return evaluateFromAnchor(ctx, targetStep, true);
    }

    /**
     * Evaluates exact V_target from anchor k_a using exact rational recursion:
     * If includeTargetStepFlow == false, evaluates V_target^- right before targetStep's cash flow a(targetStep)
     * (used to resolve sBeforeCents = rho(V_target^-)).
     * If includeTargetStepFlow == true, evaluates V_target after targetStep's cash flow.
     */
    public static ExactRational evaluateFromAnchor(
            final BiChronosContext ctx,
            final int targetStep,
            final boolean includeTargetStepFlow) {
        ExactRational v = ExactRational.ofCents(ctx.anchorPrincipalCents);
        for (int k = ctx.anchorDayIndex + 1; k <= targetStep; k++) {
            final ExactRational fk = ExactRational.ofScaledFactor(
                    ctx.dailyFactorFk[k], BiChronosContext.FACTOR_SCALE);
            final long ak = ctx.netFlowByStep[k];
            final long alpha = ctx.proRataByStep[k];
            final ExactRational vBefore = v.multiply(fk);

            if (k == targetStep && !includeTargetStepFlow) {
                return vBefore;
            }

            if (ak < 0L && ak == -vBefore.applyRho(ctx.roundingMode)) {
                v = ExactRational.ZERO;
            } else if (ctx.withdrawalModality == DayCountConvention.WITHDRAW_PRO_RATA && alpha > 0L) {
                final ExactRational keep = new ExactRational(
                        BigInteger.valueOf(BiChronosContext.FACTOR_SCALE - alpha),
                        BigInteger.valueOf(BiChronosContext.FACTOR_SCALE));
                v = vBefore.multiply(keep);
            } else {
                v = vBefore.add(ExactRational.ofCents(ak));
            }
        }
        return v;
    }

    @Override
    public int compareTo(final ExactRational o) {
        return this.num.multiply(o.den).compareTo(o.num.multiply(this.den));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ExactRational other)) return false;
        return this.num.equals(other.num) && this.den.equals(other.den);
    }

    @Override
    public int hashCode() {
        return 31 * num.hashCode() + den.hashCode();
    }

    @Override
    public String toString() {
        return num + "/" + den;
    }
}
