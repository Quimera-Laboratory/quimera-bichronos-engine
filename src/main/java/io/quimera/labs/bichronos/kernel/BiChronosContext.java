/*
 * SPDX-License-Identifier: GPL-2.0-only OR Apache-2.0
 *
 * BiChronos Engine v8.0 - Flat SoA (Structure of Arrays) Kernel Ring & State Buffer
 *
 * Zero heap allocations on the hot path. Every event log and snapshot tape
 * is a contiguous primitive array. Cache lines stay hot; GC stays dead.
 */
package io.quimera.labs.bichronos.kernel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;

public final class BiChronosContext {

    /* Fixed-point scale constants:
     * FACTOR_SCALE (10^8): standard B3 8-decimal daily DI factor
     * GUARD_SCALE  (10^12): q = 12 sub-centavo fixed-point digits (Ziv guard band < 2.2e-11 centavo)
     */
    public static final int FACTOR_DECIMALS = 8;
    public static final long FACTOR_SCALE   = 100_000_000L;       /* 10^8 */

    public static final int GUARD_DECIMALS  = 12;
    public static final long GUARD_SCALE    = 1_000_000_000_000L; /* 10^12 */
    public static final long HALF_GUARD     = 500_000_000_000L;   /* 0.5 * 10^12 */

    /* Configuration parameters */
    public final int base;                 /* 252, 360, or 365 */
    public final int roundingMode;         /* ROUND_TRUNCATE or ROUND_HALF_UP */
    public final int withdrawalModality;   /* WITHDRAW_TRANSLATION (A) or WITHDRAW_PRO_RATA (B) */
    public final int maxHorizon;           /* Maximum day index capacity */
    public final int maxEvents;            /* Maximum bitemporal event capacity */

    /* Anchor state at closed period boundary k_a (date a = d_{k_a}) */
    public int anchorDayIndex;             /* k_a */
    public long anchorPrincipalCents;      /* P_{k_a} in integer centavos */
    public long anchorVtildeSubCents;      /* \tilde{V}_{k_a} scaled by 10^12 */
    public BigInteger anchorVtildeBig;     /* \tilde{V}_{k_a} in arbitrary-precision BigInteger */
    public long anchorGuardSubCents;       /* g_{k_a} scaled by 10^12 */
    public long anchorPublishedS;          /* S_{k_a} in integer centavos */

    /* Append-only Bitemporal Event Tape E (Invariant I1)
     * e = (t_c, t_v, a_cents, alpha_pro_rata_scaled)
     */
    public int eventCount;
    public final int[] eventTc;            /* Accounting date t_c (registro) */
    public final int[] eventTv;            /* Value date t_v (efeito) */
    public final long[] eventAmountCents;  /* Net flow a(e) in centavos */
    public final long[] eventProRataAlpha; /* Optional pro-rata fraction scaled by 10^8 (Modality B) */

    /* Calendar step function g[t] = N(t_0, t), interest rate tape, and daily factor tape */
    public final int[] calendarG;          /* Maps civil date offset t -> accumulation index k */
    public final BigDecimal[] annualRateBd;/* Exact annual interest rate as BigDecimal (e.g. 10.65% a.a.) */
    public final BigDecimal[] dailyFactorBd;/* Exact daily factor as BigDecimal (scale 8 or 16) */
    public final long[] dailyFactorFk;     /* f_k scaled by 10^8 for each step k */

    /* Versioned Snapshot Tape (State per step k: P_k, \tilde{V}_k, g_k, S_k, J_{k,pub}, D_k, y_k) */
    public final long[] pCents;            /* Principal P_k in centavos */
    public final long[] vTildeSubCents;    /* \tilde{V}_k scaled by 10^12 (q = 12) */
    public final BigInteger[] vTildeBig;   /* \tilde{V}_k exact BigInteger for arbitrary balances */
    public final long[] guardSubCents;     /* Guard band g_k scaled by 10^12 */
    public final long[] sPublishedCents;   /* Published snapshot S_k = rho(V_k) in centavos */
    public final long[] jPublishedCents;   /* Published accrual J_{k,pub} = S_k - P_k in centavos */
    public final long[] dMovementCents;    /* Daily movement D_k = S_k - S_{k-1} - a_k */
    public final long[] yLegacyCents;      /* Legacy iterative recursion y_k = rho(f_k y_{k-1}) + a_k */

    /* Scratch aggregation buffers for exact O(n) bucket sorting by value date */
    public final long[] netFlowByStep;
    public final long[] proRataByStep;

    /* Closed-period retroactive adjustment outputs (Algorithm 5.14, Step 6) */
    public long lastClosedDeltaPCents;
    public long lastClosedDeltaJIdealSubCents;
    public long lastClosedDeltaJPubCents;
    public long lastClosingResidueCents;

    public BiChronosContext(
            final int base,
            final int roundingMode,
            final int withdrawalModality,
            final int maxHorizon,
            final int maxEvents) {
        this.base = base;
        this.roundingMode = roundingMode;
        this.withdrawalModality = withdrawalModality;
        this.maxHorizon = maxHorizon;
        this.maxEvents = maxEvents;

        this.eventTc = new int[maxEvents];
        this.eventTv = new int[maxEvents];
        this.eventAmountCents = new long[maxEvents];
        this.eventProRataAlpha = new long[maxEvents];

        this.calendarG = new int[maxHorizon + 1];
        this.annualRateBd = new BigDecimal[maxHorizon + 1];
        this.dailyFactorBd = new BigDecimal[maxHorizon + 1];
        this.dailyFactorFk = new long[maxHorizon + 1];

        this.pCents = new long[maxHorizon + 1];
        this.vTildeSubCents = new long[maxHorizon + 1];
        this.vTildeBig = new BigInteger[maxHorizon + 1];
        this.guardSubCents = new long[maxHorizon + 1];
        this.sPublishedCents = new long[maxHorizon + 1];
        this.jPublishedCents = new long[maxHorizon + 1];
        this.dMovementCents = new long[maxHorizon + 1];
        this.yLegacyCents = new long[maxHorizon + 1];

        this.netFlowByStep = new long[maxHorizon + 1];
        this.proRataByStep = new long[maxHorizon + 1];

        /* Default identity calendar g[t] = t, neutral factor f_k = 1.0, and zero rate */
        final BigDecimal defaultFactorBd = BigDecimal.ONE.setScale(FACTOR_DECIMALS, RoundingMode.UNNECESSARY);
        for (int i = 0; i <= maxHorizon; i++) {
            this.calendarG[i] = i;
            this.annualRateBd[i] = BigDecimal.ZERO;
            this.dailyFactorBd[i] = defaultFactorBd;
            this.dailyFactorFk[i] = FACTOR_SCALE;
        }
    }

    /**
     * Initializes the closed anchor at index k_a (date a) with integer balance x_0.
     * Uses Math.multiplyExact to guarantee detection of 64-bit overflow under scale 10^12.
     * For arbitrary-precision balances exceeding 64-bit long capacity (>= R$ 92.233,73),
     * use initAnchorBig.
     */
    public void initAnchor(final int kAnchor, final long x0Cents) {
        this.anchorDayIndex = kAnchor;
        this.anchorPrincipalCents = x0Cents;
        this.anchorVtildeSubCents = Math.multiplyExact(x0Cents, GUARD_SCALE);
        this.anchorVtildeBig = BigInteger.valueOf(x0Cents).multiply(BigInteger.valueOf(GUARD_SCALE));
        this.anchorGuardSubCents = 0L;
        this.anchorPublishedS = x0Cents;

        this.pCents[kAnchor] = x0Cents;
        this.vTildeSubCents[kAnchor] = this.anchorVtildeSubCents;
        this.vTildeBig[kAnchor] = this.anchorVtildeBig;
        this.guardSubCents[kAnchor] = 0L;
        this.sPublishedCents[kAnchor] = x0Cents;
        this.jPublishedCents[kAnchor] = 0L;
        this.dMovementCents[kAnchor] = 0L;
        this.yLegacyCents[kAnchor] = x0Cents;
    }

    /**
     * Initializes the closed anchor with arbitrary precision x_0 in centavos.
     */
    public void initAnchorBig(final int kAnchor, final BigInteger x0Cents) {
        this.anchorDayIndex = kAnchor;
        if (x0Cents.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0
                && x0Cents.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0) {
            this.anchorPrincipalCents = x0Cents.longValueExact();
            this.anchorPublishedS = this.anchorPrincipalCents;
        } else {
            this.anchorPrincipalCents = Long.MAX_VALUE;
            this.anchorPublishedS = Long.MAX_VALUE;
        }
        this.anchorVtildeBig = x0Cents.multiply(BigInteger.valueOf(GUARD_SCALE));
        if (this.anchorVtildeBig.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0
                && this.anchorVtildeBig.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0) {
            this.anchorVtildeSubCents = this.anchorVtildeBig.longValueExact();
        } else {
            this.anchorVtildeSubCents = Long.MAX_VALUE;
        }
        this.anchorGuardSubCents = 0L;

        this.pCents[kAnchor] = this.anchorPrincipalCents;
        this.vTildeSubCents[kAnchor] = this.anchorVtildeSubCents;
        this.vTildeBig[kAnchor] = this.anchorVtildeBig;
        this.guardSubCents[kAnchor] = 0L;
        this.sPublishedCents[kAnchor] = this.anchorPublishedS;
        this.jPublishedCents[kAnchor] = 0L;
        this.dMovementCents[kAnchor] = 0L;
        this.yLegacyCents[kAnchor] = this.anchorPublishedS;
    }

    /**
     * Invariant I1 (Append-only): Appends a new bitemporal event e = (t_c, t_v, a).
     * Reversals are appended as (t_c', t_v, -a). No existing entry is ever mutated.
     */
    public int appendEvent(final int tc, final int tv, final long amountCents) {
        return appendEventProRata(tc, tv, amountCents, 0L);
    }

    public int appendEventProRata(final int tc, final int tv, final long amountCents, final long alphaScaled) {
        if (eventCount >= maxEvents) {
            throw new IllegalStateException("BiChronos event ring capacity exceeded: " + maxEvents);
        }
        if (tv <= 0) {
            throw new IllegalArgumentException("Value date t_v must be > t_0 (0): " + tv);
        }
        final int idx = eventCount++;
        eventTc[idx] = tc;
        eventTv[idx] = tv;
        eventAmountCents[idx] = amountCents;
        eventProRataAlpha[idx] = alphaScaled;
        return idx;
    }

    /**
     * Copies a snapshot state range [fromIdx..toIdx] into external arrays for version immutability (I2).
     */

    /**
     * Financial boundary helper: initializes closed anchor from a BRL BigDecimal (e.g. new BigDecimal("10000.00")).
     */
    public void initAnchorBrl(final int kAnchor, final java.math.BigDecimal x0Brl) {
        initAnchor(kAnchor, FinancialDecimalBridge.brlToCentsExact(x0Brl));
    }

    /**
     * Financial boundary helper: sets the annual interest rate as a BigDecimal (e.g. new BigDecimal("10.65") for 10.65% a.a.)
     * at step k, computing and setting the exact truncated daily factor according to the context base.
     */
    public void setAnnualRateBd(final int stepK, final BigDecimal annualRatePercent) {
        if (annualRatePercent == null) {
            throw new IllegalArgumentException("Annual interest rate BigDecimal cannot be null at step " + stepK);
        }
        if (stepK < 0 || stepK > maxHorizon) {
            throw new IndexOutOfBoundsException("Step " + stepK + " outside horizon [0.." + maxHorizon + "]");
        }
        this.annualRateBd[stepK] = annualRatePercent;
        final BigDecimal factorBd = DayCountConvention.computeTruncatedDailyFactorBd(
                annualRatePercent, this.base, FACTOR_DECIMALS);
        this.dailyFactorBd[stepK] = factorBd;
        this.dailyFactorFk[stepK] = DayCountConvention.factorFromBigDecimal(factorBd, FACTOR_DECIMALS);
    }

    /**
     * Sets the annual interest rate as a BigDecimal for a range of steps [fromStep..toStep].
     */
    public void setAnnualRateBd(final int fromStep, final int toStep, final BigDecimal annualRatePercent) {
        for (int k = fromStep; k <= toStep; k++) {
            setAnnualRateBd(k, annualRatePercent);
        }
    }

    /**
     * Sets a daily interest rate as a BigDecimal (e.g. new BigDecimal("0.040167") for 0.040167% ao dia)
     * at step k, computing f_k = floor(10^p * (1 + taxa_diaria / 100)) / 10^p.
     */
    public void setDailyRateBd(final int stepK, final BigDecimal dailyRatePercent) {
        if (dailyRatePercent == null) {
            throw new IllegalArgumentException("Daily interest rate BigDecimal cannot be null at step " + stepK);
        }
        if (stepK < 0 || stepK > maxHorizon) {
            throw new IndexOutOfBoundsException("Step " + stepK + " outside horizon [0.." + maxHorizon + "]");
        }
        this.annualRateBd[stepK] = dailyRatePercent;
        final BigDecimal onePlusId = BigDecimal.ONE.add(
                dailyRatePercent.divide(DayCountConvention.BD_HUNDRED, DayCountConvention.MC_REGULATORY),
                DayCountConvention.MC_REGULATORY);
        final BigDecimal factorBd = onePlusId.setScale(FACTOR_DECIMALS, RoundingMode.DOWN);
        this.dailyFactorBd[stepK] = factorBd;
        this.dailyFactorFk[stepK] = DayCountConvention.factorFromBigDecimal(factorBd, FACTOR_DECIMALS);
    }

    /**
     * Sets a daily interest rate as a BigDecimal for a range of steps [fromStep..toStep].
     */
    public void setDailyRateBd(final int fromStep, final int toStep, final BigDecimal dailyRatePercent) {
        for (int k = fromStep; k <= toStep; k++) {
            setDailyRateBd(k, dailyRatePercent);
        }
    }

    /**
     * Financial boundary helper: sets the daily factor at step k from a B3/ANBIMA BigDecimal (e.g. new BigDecimal("1.00040167")).
     */
    public void setDailyFactorBd(final int stepK, final BigDecimal factorBd) {
        if (factorBd == null) {
            throw new IllegalArgumentException("Daily factor BigDecimal cannot be null at step " + stepK);
        }
        if (stepK < 0 || stepK > maxHorizon) {
            throw new IndexOutOfBoundsException("Step " + stepK + " outside horizon [0.." + maxHorizon + "]");
        }
        this.dailyFactorBd[stepK] = factorBd;
        this.dailyFactorFk[stepK] = DayCountConvention.factorFromBigDecimal(factorBd, FACTOR_DECIMALS);
    }

    /**
     * Sets the daily factor from an exact BigDecimal for a range of steps [fromStep..toStep].
     */
    public void setDailyFactorBd(final int fromStep, final int toStep, final BigDecimal factorBd) {
        for (int k = fromStep; k <= toStep; k++) {
            setDailyFactorBd(k, factorBd);
        }
    }

    public BigDecimal getAnnualRateBd(final int stepK) {
        final BigDecimal r = this.annualRateBd[stepK];
        return r != null ? r : BigDecimal.ZERO;
    }

    public BigDecimal getDailyFactorBd(final int stepK) {
        final BigDecimal f = this.dailyFactorBd[stepK];
        if (f != null) {
            return f;
        }
        return DayCountConvention.factorToBigDecimal(this.dailyFactorFk[stepK], FACTOR_DECIMALS);
    }

    /**
     * Financial boundary helper: appends an event with amount in BRL BigDecimal (e.g. new BigDecimal("5000.00")).
     */
    public int appendEventBrl(final int tc, final int tv, final java.math.BigDecimal amountBrl) {
        return appendEvent(tc, tv, FinancialDecimalBridge.brlToCentsExact(amountBrl));
    }

    /**
     * Financial boundary helper: appends a pro-rata withdrawal event (Modality B) with fraction alpha as BigDecimal (e.g. new BigDecimal("0.25")).
     */
    public int appendEventProRataBd(final int tc, final int tv, final java.math.BigDecimal alphaFraction) {
        final long alphaScaled = DayCountConvention.factorFromBigDecimal(alphaFraction, FACTOR_DECIMALS);
        return appendEventProRata(tc, tv, 0L, alphaScaled);
    }

    public SnapshotVersion captureVersion(final int sigma, final int upToStep) {
        return new SnapshotVersion(
                sigma,
                Arrays.copyOfRange(pCents, 0, upToStep + 1),
                Arrays.copyOfRange(vTildeSubCents, 0, upToStep + 1),
                Arrays.copyOfRange(guardSubCents, 0, upToStep + 1),
                Arrays.copyOfRange(sPublishedCents, 0, upToStep + 1),
                Arrays.copyOfRange(jPublishedCents, 0, upToStep + 1),
                Arrays.copyOfRange(dMovementCents, 0, upToStep + 1),
                Arrays.copyOfRange(yLegacyCents, 0, upToStep + 1)
        );
    }

    public record SnapshotVersion(
            int sigma,
            long[] pCents,
            long[] vTildeSubCents,
            long[] guardSubCents,
            long[] sPublishedCents,
            long[] jPublishedCents,
            long[] dMovementCents,
            long[] yLegacyCents
    ) {}
}
