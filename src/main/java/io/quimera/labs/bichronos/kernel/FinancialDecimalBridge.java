/*
 * SPDX-License-Identifier: Proprietary - Quimera Laboratory (All Rights Reserved)
 *
 * BiChronos Engine v8.0 - Financial & COSIF BigDecimal Boundary Bridge
 *
 * Why this exists:
 * In Java financial architectures (BACEN COSIF, B3, SPB, Open Finance), external APIs,
 * JDBC/JPA persistence, JSON/ISO-20022 payloads, and accounting ledgers speak BigDecimal:
 *   - Monetary amounts in BRL (R$) with scale 2 (centavos)
 *   - Internal ideal balances V_k in BRL (R$) with scale 14 (2 centavo decimals + q=12 sub-centavo decimals)
 *   - Daily factors f_k with scale 8 and accumulated B3 factors with scale 16
 *   - Pro-rata redemption fractions alpha_k with scale 8
 *
 * The kernel runs on flat primitive arrays (long[]) for L1 cache sympathy and zero GC pressure,
 * while FinancialDecimalBridge provides lossless, exact BigDecimal conversion at the boundary.
 */
package io.quimera.labs.bichronos.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FinancialDecimalBridge {

    /* Monetary scales in BRL (R$) */
    public static final int BRL_CENTS_SCALE    = 2;  /* R$ 0.01 = 1 centavo */
    public static final int BRL_INTERNAL_SCALE = 14; /* 2 (centavos) + 12 (q=12 sub-centavos) */

    private FinancialDecimalBridge() {}

    /**
     * Converts a BRL monetary BigDecimal (e.g. new BigDecimal("10000.00")) to exact integer centavos (1_000_000L).
     * Throws ArithmeticException if the input has fractional centavos (scale > 2 with non-zero digits).
     */
    public static long brlToCentsExact(final BigDecimal brlAmount) {
        return brlAmount.movePointRight(BRL_CENTS_SCALE).longValueExact();
    }

    /**
     * Converts integer centavos (e.g. 1_002_009L) to an exact BRL BigDecimal with scale 2 ("10020.09").
     */
    public static BigDecimal centsToBrl(final long cents) {
        return BigDecimal.valueOf(cents, BRL_CENTS_SCALE);
    }

    /**
     * Converts sub-centavo fixed-point state 	ilde{V}_k (scaled by 10^12 centavos) to a
     * BigDecimal in centavos with scale 12 (e.g. "1000401.670000000000").
     */
    public static BigDecimal subCentsToCentavosBd(final long subCents) {
        return BigDecimal.valueOf(subCents, BiChronosContext.GUARD_DECIMALS);
    }

    /**
     * Converts sub-centavo fixed-point state 	ilde{V}_k (scaled by 10^12 centavos) to a
     * BigDecimal in BRL (Reais, R$) with scale 14 (e.g. "10004.01670000000000").
     */
    public static BigDecimal subCentsToBrlBd(final long subCents) {
        return BigDecimal.valueOf(subCents, BRL_INTERNAL_SCALE);
    }

    /**
     * Applies Definition 5.3 rounding operator rho directly on a BigDecimal in centavos,
     * returning an exact integer centavo BigDecimal (scale 0):
     * ROUND_TRUNCATE: floor(x)
     * ROUND_HALF_UP:  floor(x + 1/2)
     */
    public static BigDecimal applyRhoCentavosBd(final BigDecimal centavosBd, final int roundingMode) {
        if (roundingMode == DayCountConvention.ROUND_TRUNCATE) {
            return centavosBd.setScale(0, RoundingMode.FLOOR);
        }
        return centavosBd.add(new BigDecimal("0.5")).setScale(0, RoundingMode.FLOOR);
    }

    /**
     * Applies Definition 5.3 rounding operator rho directly on a BigDecimal in BRL (Reais),
     * returning an exact BRL BigDecimal with scale 2 (centavos):
     * ROUND_TRUNCATE: floor(x * 100) / 100
     * ROUND_HALF_UP:  floor(x * 100 + 1/2) / 100
     */
    public static BigDecimal applyRhoBrlBd(final BigDecimal brlBd, final int roundingMode) {
        if (roundingMode == DayCountConvention.ROUND_TRUNCATE) {
            return brlBd.setScale(BRL_CENTS_SCALE, RoundingMode.FLOOR);
        }
        return brlBd.add(new BigDecimal("0.005")).setScale(BRL_CENTS_SCALE, RoundingMode.FLOOR);
    }

    /**
     * Exports a complete COSIF Accounting Snapshot Record in BigDecimal (BRL, R$) for a given step k.
     */
    public static CosifSnapshotEntry exportCosifEntryBrl(final BiChronosContext ctx, final int stepK) {
        final BigDecimal annualRateBd = ctx.getAnnualRateBd(stepK);
        final BigDecimal principalBrl = centsToBrl(ctx.pCents[stepK]);
        final BigDecimal idealBalanceBrl = subCentsToBrlBd(ctx.vTildeSubCents[stepK]);
        final BigDecimal guardBandBrl = subCentsToBrlBd(ctx.guardSubCents[stepK]);
        final BigDecimal publishedBalanceBrl = centsToBrl(ctx.sPublishedCents[stepK]);
        final BigDecimal publishedAccrualBrl = centsToBrl(ctx.jPublishedCents[stepK]);
        final BigDecimal dailyMovementBrl = centsToBrl(ctx.dMovementCents[stepK]);
        final BigDecimal legacyBalanceBrl = centsToBrl(ctx.yLegacyCents[stepK]);
        final BigDecimal dailyFactor = ctx.getDailyFactorBd(stepK);

        return new CosifSnapshotEntry(
                stepK,
                annualRateBd,
                dailyFactor,
                principalBrl,
                idealBalanceBrl,
                guardBandBrl,
                publishedBalanceBrl,
                publishedAccrualBrl,
                dailyMovementBrl,
                legacyBalanceBrl
        );
    }

    /**
     * Immutable financial/accounting DTO using BigDecimal for COSIF/BACEN integration and reporting.
     */
    public record CosifSnapshotEntry(
            int stepIndex,
            BigDecimal annualRateBd,         /* Scale 2+: Taxa a.a. em % (e.g. 10.65) */
            BigDecimal dailyFactorFk,        /* Scale 8:  e.g. 1.00040167 */
            BigDecimal principalBrl,         /* Scale 2:  P_k in R$ */
            BigDecimal idealBalanceBrl,      /* Scale 14: \tilde{V}_k in R$ */
            BigDecimal guardBandBrl,         /* Scale 14: g_k in R$ */
            BigDecimal publishedBalanceBrl,  /* Scale 2:  S_k = rho(V_k) in R$ */
            BigDecimal publishedAccrualBrl,  /* Scale 2:  J_{k,pub} = S_k - P_k in R$ */
            BigDecimal dailyMovementBrl,     /* Scale 2:  D_k in R$ */
            BigDecimal legacyBalanceBrl      /* Scale 2:  y_k in R$ */
    ) {}
}
