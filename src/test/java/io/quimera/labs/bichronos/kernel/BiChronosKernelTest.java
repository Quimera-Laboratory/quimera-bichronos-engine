/*
 * SPDX-License-Identifier: Proprietary - Quimera Laboratory (All Rights Reserved)
 *
 * BiChronos Engine v8.0 - Comprehensive Verification Test Suite
 * Validates Scenarios A, B, C (Tables 1, 6, 7, 8), all 20 Invariants across
 * bases {252, 360, 365}, COSIF GL competence reconciliation, and Step 6(b) non-additivity fix.
 */
package io.quimera.labs.bichronos.kernel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BiChronosKernelTest {

    @Test
    public void testTable1Bases252And360And365() {
        /* Table 1: 10.65% a.a., x_0 = 1,000,000 centavos, Friday 16/10 to Friday 23/10 */
        final BiChronosContext ctx252 = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx252.initAnchor(0, 1_000_000L);
        ctx252.setAnnualRateBd(1, 5, BiChronosKernel.RATE_10_65_A_A);
        BiChronosKernel.executeRange(ctx252, 1, 1, 5);
        assertEquals(1_002_009L, ctx252.sPublishedCents[5]);

        final BiChronosContext ctx360 = new BiChronosContext(
                DayCountConvention.BASE_360, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx360.initAnchor(0, 1_000_000L);
        ctx360.setAnnualRateBd(1, 7, BiChronosKernel.RATE_10_65_A_A);
        BiChronosKernel.executeRange(ctx360, 1, 1, 7);
        assertEquals(1_001_969L, ctx360.sPublishedCents[7]);

        final BiChronosContext ctx365 = new BiChronosContext(
                DayCountConvention.BASE_365, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx365.initAnchor(0, 1_000_000L);
        ctx365.setAnnualRateBd(1, 7, BiChronosKernel.RATE_10_65_A_A);
        BiChronosKernel.executeRange(ctx365, 1, 1, 7);
        assertEquals(1_001_942L, ctx365.sPublishedCents[7]);
    }

    @Test
    public void testScenarioATable6ContiguousBusinessDays() {
        final BiChronosContext ctx = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        ctx.setAnnualRateBd(1, 5, BiChronosKernel.RATE_10_65_A_A);
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        /* Verify S_k, D_k, y_k and d_k = S_k - y_k from Table 6 */
        final long[] expectedS = {1_000_000L, 1_000_401L, 1_000_803L, 1_001_205L, 1_001_607L, 1_002_009L};
        final long[] expectedD = {0L, 401L, 402L, 402L, 402L, 402L};
        final long[] expectedY = {1_000_000L, 1_000_401L, 1_000_802L, 1_001_203L, 1_001_605L, 1_002_007L};

        for (int k = 1; k <= 5; k++) {
            assertEquals(expectedS[k], ctx.sPublishedCents[k], "S_k mismatch at k=" + k);
            assertEquals(expectedD[k], ctx.dMovementCents[k], "D_k mismatch at k=" + k);
            assertEquals(expectedY[k], ctx.yLegacyCents[k], "y_k mismatch at k=" + k);
        }

        /* Verify exact flow inversion \Phi_{5 -> 0}(V_5) == 1,000,000 */
        assertEquals(ExactRational.ofCents(1_000_000L), BiChronosKernel.invertFlowToAnchor(ctx, 1, 5));
    }

    @Test
    public void testScenarioBTable7RetroactiveEventAndStep6NonAdditivity() {
        final BiChronosContext ctx = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) {
            ctx.dailyFactorFk[k] = 100_040_167L;
        }

        /* Version sigma = 4 (Thursday 22/10): no retroactive event yet */
        BiChronosKernel.executeRange(ctx, 4, 1, 4);
        assertEquals(1_000_401L, ctx.sPublishedCents[1]);
        assertEquals(1_000_803L, ctx.sPublishedCents[2]);
        assertEquals(1_001_205L, ctx.sPublishedCents[3]);
        assertEquals(1_001_607L, ctx.sPublishedCents[4]);

        /* Register retroactive deposit a = 500,000 at t_v = 2 (Tuesday 20/10), t_c = 5 (Friday 23/10) */
        ctx.appendEvent(5, 2, 500_000L);
        BiChronosKernel.reprocess(ctx, 4, 5, 5, true);

        /* Verify Table 7 S_k^{\sigma'} values */
        assertEquals(1_000_401L, ctx.sPublishedCents[1]); /* k=1 untouched (I3) */
        assertEquals(1_500_803L, ctx.sPublishedCents[2]);
        assertEquals(1_501_406L, ctx.sPublishedCents[3]); /* 1,001,205 + 500,201 (fractional carry!) */
        assertEquals(1_502_009L, ctx.sPublishedCents[4]);
        assertEquals(1_502_612L, ctx.sPublishedCents[5]);

        /* Also test Step 6(b) closed-period adjustment when boundary a is at k = 3 (Wednesday 21/10) */
        final BiChronosContext ctxClosed = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctxClosed.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctxClosed.dailyFactorFk[k] = 100_040_167L;
        BiChronosKernel.executeRange(ctxClosed, 4, 1, 4);
        /* Lock period at k_a = 3 retaining fraction */
        BiChronosKernel.reconcileClosing(ctxClosed, 3, ctxClosed.sPublishedCents[3], 1.001, true);
        ctxClosed.appendEvent(5, 2, 500_000L);
        BiChronosKernel.reprocess(ctxClosed, 4, 5, 5, false);

        /* At k_a = 3, \Delta P = 500,000 and \Delta J_{pub}(3) = 201 (not 200!), so S_3^{\sigma'} = 1,501,406 */
        assertEquals(500_000L, ctxClosed.lastClosedDeltaPCents);
        assertEquals(201L, ctxClosed.lastClosedDeltaJPubCents);
        assertEquals(1_501_406L, ctxClosed.sPublishedCents[3]);
        assertEquals(1_502_612L, ctxClosed.sPublishedCents[5]);
    }

    @Test
    public void testScenarioCTable8HolidayPlateauAndSubCentavoYield() {
        /* Calendar mapping civil offsets t=0..7 (08/10 to 15/10) to business day index N(t_0, t):
         * t=0 (08/10 Thu): k=0
         * t=1 (09/10 Fri): k=1
         * t=2 (10/10 Sat): k=1 (plateau)
         * t=3 (11/10 Sun): k=1 (plateau)
         * t=4 (12/10 Mon Holiday): k=1 (plateau, deposit a=500 occurs here)
         * t=5 (13/10 Tue): k=2
         * t=6 (14/10 Wed): k=3
         * t=7 (15/10 Thu): k=4
         */
        final BiChronosContext ctx = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000L);
        final int[] g = {0, 1, 1, 1, 1, 2, 3, 4};
        System.arraycopy(g, 0, ctx.calendarG, 0, g.length);
        for (int k = 1; k <= 4; k++) {
            ctx.dailyFactorFk[k] = 100_040_167L;
        }
        /* Deposit a = 500 on holiday t_v = 4 (12/10), registered on t_c = 5 (13/10) */
        ctx.appendEvent(5, 4, 500L);
        BiChronosKernel.executeRange(ctx, 5, 1, 7);

        assertEquals(1_500L, ctx.sPublishedCents[1]);
        assertEquals(1_501L, ctx.sPublishedCents[2]); /* 13/10 Tue: V_2 = 1501.0043 -> S_2 = 1501, y_2 = 1500 */
        assertEquals(1_500L, ctx.yLegacyCents[2]);
        assertEquals(1_501L, ctx.sPublishedCents[3]); /* 14/10 Wed */
        assertEquals(1_502L, ctx.sPublishedCents[4]); /* 15/10 Thu: V_4 = 1502.2103 -> S_4 = 1502, y_4 = 1500 */
        assertEquals(1_500L, ctx.yLegacyCents[4]);
    }

    @Test
    public void testAll20InvariantsAcrossAllBases() {
        assertEquals(InvariantVerifier.ALL_20_INVARIANTS_MASK,
                InvariantVerifier.verifyAll20Invariants(DayCountConvention.BASE_252));
        assertEquals(InvariantVerifier.ALL_20_INVARIANTS_MASK,
                InvariantVerifier.verifyAll20Invariants(DayCountConvention.BASE_360));
        assertEquals(InvariantVerifier.ALL_20_INVARIANTS_MASK,
                InvariantVerifier.verifyAll20Invariants(DayCountConvention.BASE_365));
    }

    @Test
    public void testGlCompetenceProvisionAndFullLiquidation() {
        /* 100 accounts in sub-centavo trap: x_0 = 1,000, f_1 = 1.00040167 -> each has V_1 = 1000.40167, S_1 = 1000 */
        final BiChronosContext[] portfolio = new BiChronosContext[100];
        for (int i = 0; i < portfolio.length; i++) {
            portfolio[i] = new BiChronosContext(
                    DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                    DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
            portfolio[i].initAnchor(0, 1_000L);
            portfolio[i].dailyFactorFk[1] = 100_040_167L;
            BiChronosKernel.executeRange(portfolio[i], 1, 1, 1);
            assertEquals(1_000L, portfolio[i].sPublishedCents[1]);
        }
        /* Individual published sum is 100,000, but aggregate economic liability is floor(100 * 1000.40167) = 100,040 */
        final long glProvision = BiChronosKernel.computeGlCompetenceProvision(
                portfolio, 1, DayCountConvention.ROUND_TRUNCATE);
        assertEquals(40L, glProvision);

        /* Test H3(iv) full redemption extinguishing phantom sub-centavo fraction */
        portfolio[0].dailyFactorFk[2] = 100_040_167L;
        portfolio[0].appendEvent(2, 2, -1_000L);
        BiChronosKernel.executeRange(portfolio[0], 2, 2, 2);
        assertEquals(0L, portfolio[0].sPublishedCents[2]);
        assertEquals(0L, portfolio[0].vTildeSubCents[2]);
        assertEquals(0L, portfolio[0].guardSubCents[2]);
    }

    @Test
    public void testBigDecimalFinancialBoundaryAndCosifExport() {
        final java.math.BigDecimal x0Brl = new java.math.BigDecimal("10000.00");
        final java.math.BigDecimal dailyFactorBd = new java.math.BigDecimal("1.00040167");
        final java.math.BigDecimal depositBrl = new java.math.BigDecimal("5000.00");

        final BiChronosContext ctx = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchorBrl(0, x0Brl);
        for (int k = 1; k <= 5; k++) {
            ctx.setDailyFactorBd(k, dailyFactorBd);
        }
        ctx.appendEventBrl(5, 2, depositBrl);
        BiChronosKernel.executeRange(ctx, 5, 1, 5);

        /* Export step 5 as a COSIF BigDecimal DTO in BRL (R$) */
        final FinancialDecimalBridge.CosifSnapshotEntry entry5 =
                FinancialDecimalBridge.exportCosifEntryBrl(ctx, 5);

        assertEquals(new java.math.BigDecimal("15000.00"), entry5.principalBrl());
        assertEquals(new java.math.BigDecimal("15026.12"), entry5.publishedBalanceBrl());
        assertEquals(new java.math.BigDecimal("26.12"), entry5.publishedAccrualBrl());
        assertEquals(new java.math.BigDecimal("1.00040167"), entry5.dailyFactorFk());

        /* Verify B3 16-decimal accumulated factor in BigDecimal */
        final java.math.BigDecimal b3Factor16 = DayCountConvention.computeB3AccumulatedFactor16(
                ctx.dailyFactorFk, BiChronosContext.FACTOR_DECIMALS, 0, 5);
        assertEquals(16, b3Factor16.scale());
        assertTrue(b3Factor16.toPlainString().startsWith("1.0020099640"));
    }

    @Test
    public void testReprocessTheReprocessedWithInterleavedAmortizations() {
        final BiChronosContext ctx = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 15, 10);
        ctx.initAnchor(0, 5_000_000L); // R$ 50,000.00
        for (int k = 1; k <= 10; k++) {
            ctx.dailyFactorFk[k] = 100_040_167L;
        }

        // Phase 1 (sigma = 4): Initial amortization of R$ 10,000.00 at t_v = 4
        ctx.appendEvent(4, 4, -1_000_000L);
        BiChronosKernel.executeRange(ctx, 4, 1, 10);

        // Phase 2 (sigma = 11, Reprocessing 1): Retroactive credit of R$ 8,000.00 at t_v = 2
        ctx.appendEvent(11, 2, 800_000L);
        BiChronosKernel.reprocess(ctx, 4, 11, 10, true);

        // Phase 3 (sigma = 12, Reprocessing 2 - "Reprocessar o reprocessado"):
        // Interleaved retroactive amortization of R$ 5,000.00 at t_v = 6
        ctx.appendEvent(12, 6, -500_000L);
        BiChronosKernel.reprocess(ctx, 11, 12, 10, true);

        // Phase 4 (sigma = 13, Reprocessing 3):
        // Retroactive deposit of R$ 3,000.00 at t_v = 3 (prior to both amortizations at k=4 and k=6)
        ctx.appendEvent(13, 3, 300_000L);
        BiChronosKernel.reprocess(ctx, 12, 13, 10, true);

        // Phase 5: Single-pass batch baseline with all 4 events known from t_0
        final BiChronosContext ctxBatch = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 15, 10);
        ctxBatch.initAnchor(0, 5_000_000L);
        for (int k = 1; k <= 10; k++) {
            ctxBatch.dailyFactorFk[k] = 100_040_167L;
        }
        ctxBatch.appendEvent(4, 4, -1_000_000L);
        ctxBatch.appendEvent(11, 2, 800_000L);
        ctxBatch.appendEvent(12, 6, -500_000L);
        ctxBatch.appendEvent(13, 3, 300_000L);
        BiChronosKernel.executeRange(ctxBatch, 13, 1, 10);

        // Confluence Verification (Theorem 4.3 & Invariants I2, I17, I18):
        // The sequentially reprocessed state MUST match the batch execution bit-for-bit
        for (int k = 0; k <= 10; k++) {
            assertEquals(ctxBatch.sPublishedCents[k], ctx.sPublishedCents[k], "Published balance S mismatch at step " + k);
            assertEquals(ctxBatch.pCents[k], ctx.pCents[k], "Principal P mismatch at step " + k);
            assertEquals(ctxBatch.jPublishedCents[k], ctx.jPublishedCents[k], "Accrual J mismatch at step " + k);
            assertEquals(ctxBatch.vTildeSubCents[k], ctx.vTildeSubCents[k], "Sub-centavo state V_tilde mismatch at step " + k);
            assertEquals(ctxBatch.guardSubCents[k], ctx.guardSubCents[k], "Guard band g_k mismatch at step " + k);
        }

        // Verify runtime invariants
        InvariantVerifier.assertRuntimeInvariants(ctx, 13, 10, 1.001);
    }

    @Test
    public void testReprocessReprocessedOfReprocessedAtExactSamePoint() {
        final BiChronosContext ctx = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 15, 10);
        ctx.initAnchor(0, 3_000_000L); // R$ 30,000.00
        for (int k = 1; k <= 10; k++) {
            ctx.dailyFactorFk[k] = 100_040_167L;
        }

        // Phase 1 (sigma = 2): Baseline execution with no events at t_v = 3 yet
        BiChronosKernel.executeRange(ctx, 2, 1, 10);

        // Phase 2 (sigma = 11, Reprocessamento 1 no ponto t_v = 3):
        // Retroactive deposit of R$ 10,000.00 at t_v = 3
        ctx.appendEvent(11, 3, 1_000_000L);
        BiChronosKernel.reprocess(ctx, 2, 11, 10, true);

        // Phase 3 (sigma = 12, Reprocessamento 2 - "Reprocessar o reprocessado no MESMO ponto t_v = 3"):
        // Partial amortization of R$ 4,000.00 at the exact same t_v = 3
        ctx.appendEvent(12, 3, -400_000L);
        BiChronosKernel.reprocess(ctx, 11, 12, 10, true);

        // Phase 4 (sigma = 13, Reprocessamento 3 - "Reprocessar o reprocessado do reprocessado no MESMO ponto t_v = 3"):
        // Further adjustment / amortization of R$ 1,000.00 at the exact same t_v = 3
        ctx.appendEvent(13, 3, -100_000L);
        BiChronosKernel.reprocess(ctx, 12, 13, 10, true);

        // Phase 5 (sigma = 14, Reprocessamento 4 - Mais um ajuste no MESMO ponto t_v = 3):
        // Fine adjustment of +R$ 500.00 at the exact same t_v = 3
        ctx.appendEvent(14, 3, 50_000L);
        BiChronosKernel.reprocess(ctx, 13, 14, 10, true);

        // Phase 6: Single-pass batch baseline with all 4 events known at t_v = 3 from the start
        final BiChronosContext ctxBatch = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 15, 10);
        ctxBatch.initAnchor(0, 3_000_000L);
        for (int k = 1; k <= 10; k++) {
            ctxBatch.dailyFactorFk[k] = 100_040_167L;
        }
        ctxBatch.appendEvent(11, 3, 1_000_000L);
        ctxBatch.appendEvent(12, 3, -400_000L);
        ctxBatch.appendEvent(13, 3, -100_000L);
        ctxBatch.appendEvent(14, 3, 50_000L);
        BiChronosKernel.executeRange(ctxBatch, 14, 1, 10);

        // Confluence Verification at the exact same point across all steps
        for (int k = 0; k <= 10; k++) {
            assertEquals(ctxBatch.sPublishedCents[k], ctx.sPublishedCents[k], "S_k mismatch at step " + k);
            assertEquals(ctxBatch.pCents[k], ctx.pCents[k], "P_k mismatch at step " + k);
            assertEquals(ctxBatch.jPublishedCents[k], ctx.jPublishedCents[k], "J_k mismatch at step " + k);
            assertEquals(ctxBatch.vTildeSubCents[k], ctx.vTildeSubCents[k], "V_tilde mismatch at step " + k);
            assertEquals(ctxBatch.guardSubCents[k], ctx.guardSubCents[k], "g_k mismatch at step " + k);
        }

        InvariantVerifier.assertRuntimeInvariants(ctx, 14, 10, 1.001);
    }

    @Test
    public void testSpatialTaylorExpansionTheorem45AndZeroRemainderR2() {
        final BiChronosContext ctx = new BiChronosContext(
                DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) {
            ctx.dailyFactorFk[k] = 100_040_167L;
        }

        // Retroactive deposit of 500,000 cents at t_v = 2 registered at t_c = 5
        ctx.appendEvent(5, 2, 500_000L);

        // Theorem 4.5(ii) & Invariant I11: Exact 2nd-order Taylor Remainder R_2 MUST be ZERO (0/1)
        final ExactRational r2 = BiChronosKernel.computeSpatialTaylorRemainderR2(ctx, 4, 5, 5);
        assertEquals(ExactRational.ZERO, r2, "Theorem 4.5: 2nd-order Spatial Taylor Remainder R_2 must be zero");

        // First-order Taylor differential dPsi_tau
        final ExactRational dPsi = BiChronosKernel.computeFirstOrderTaylorDifferential(
                ctx, 4, 5, 5, ExactRational.ZERO, ctx.anchorDayIndex);
        assertNotNull(dPsi);
        assertTrue(dPsi.compareTo(ExactRational.ZERO) > 0);
    }
}
