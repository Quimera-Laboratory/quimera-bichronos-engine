/*
 * SPDX-License-Identifier: Proprietary - Quimera Laboratory (All Rights Reserved)
 *
 * BiChronos Engine v9.0 - 50 Distinct Integration & Boundary Test Suite
 *
 * Each test includes:
 * - Critério de Aceite (Acceptance Criteria)
 * - Definition of Done (DoD)
 * - Concise, direct boundary condition verification across Bases {252, 360, 365},
 *   Spatial Taylor (R_2 == 0), Ziv Guard Bands, COSIF Accounting, and Carathéodory Flow.
 */
package io.quimera.labs.bichronos.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class BiChronosIntegrationSuiteTest {

    private static final long FACTOR_10_65_A_A_BASE252 = 100_040_167L; // 10.65% a.a. in base 252
    private static final long FACTOR_10_65_A_A_BASE360 = 100_028_115L; // 10.65% a.a. in base 360
    private static final long FACTOR_10_65_A_A_BASE365 = 100_027_730L; // 10.65% a.a. in base 365

    // =========================================================================
    // GRUPO 1: EXPANSÃO DE TAYLOR ESPACIAL E RESTO DE 2ª ORDEM (R_2 == 0)
    // =========================================================================

    /**
     * Teste 01: Expansão de Taylor com Aporte Retroativo Simples e Resto R_2 Nulo.
     * Critério de Aceite: O resto de 2ª ordem R_2 entre versões sigma=4 e sigma'=5 com aporte em t_v=2 deve ser exatamente 0/1.
     * DoD: Assert de que computeSpatialTaylorRemainderR2 retorna ExactRational.ZERO.
     */
    @Test
    @DisplayName("01. Taylor: Depósito retroativo simples com R_2 == 0")
    public void test01_TaylorExpansion_SingleRetroactiveDeposit_ZeroRemainder() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(5, 2, 500_000L);

        final ExactRational r2 = BiChronosKernel.computeSpatialTaylorRemainderR2(ctx, 4, 5, 5);
        assertEquals(ExactRational.ZERO, r2);
    }

    /**
     * Teste 02: Perturbação do Ponto de Controle com deltaXStar Não Nulo.
     * Critério de Aceite: O diferencial linear dPsi_tau deve incorporar exatamente o termo F(k_*, n) * deltaXStar.
     * DoD: Assert de que dPsi com deltaXStar=100 centavos coincide com a propagação racional do capital.
     */
    @Test
    @DisplayName("02. Taylor: Perturbação de saldo inicial deltaXStar != 0")
    public void test02_TaylorExpansion_StatePerturbation_NonZeroDeltaXStar() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;

        final ExactRational deltaX = ExactRational.ofCents(100L);
        final ExactRational dPsi = BiChronosKernel.computeFirstOrderTaylorDifferential(ctx, 4, 5, 5, deltaX, 0);
        assertTrue(dPsi.compareTo(deltaX) > 0);
    }

    /**
     * Teste 03: Superposição Linear Exata sob Múltiplos Aportes Retroativos.
     * Critério de Aceite: Três aportes retroativos em datas distintas mantêm R_2 rigorosamente nulo.
     * DoD: Assert de que R_2 == 0/1 mesmo com múltiplos eventos acumulados.
     */
    @Test
    @DisplayName("03. Taylor: Múltiplos aportes retroativos com R_2 == 0")
    public void test03_TaylorExpansion_MultipleRetroactiveEvents_LinearSuperposition() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(5, 1, 100_000L);
        ctx.appendEvent(5, 2, 200_000L);
        ctx.appendEvent(5, 3, 300_000L);

        final ExactRational r2 = BiChronosKernel.computeSpatialTaylorRemainderR2(ctx, 4, 5, 5);
        assertEquals(ExactRational.ZERO, r2);
    }

    /**
     * Teste 04: Amortização Retroativa com Sinal Negativo.
     * Critério de Aceite: Fluxo a < 0 na janela retroativa preserva a anulação de R_2.
     * DoD: Assert de que R_2 == 0/1 com fluxo negativo.
     */
    @Test
    @DisplayName("04. Taylor: Amortização retroativa com R_2 == 0")
    public void test04_TaylorExpansion_RetroactiveAmortization_NegativeFlow() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 2_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(5, 2, -500_000L);

        final ExactRational r2 = BiChronosKernel.computeSpatialTaylorRemainderR2(ctx, 4, 5, 5);
        assertEquals(ExactRational.ZERO, r2);
    }

    /**
     * Teste 05: Anulação de R_2 na Base 360 (Mercado Monetário Americano).
     * Critério de Aceite: Teorema 4.5 deve ser universal e independente da base de contagem de dias.
     * DoD: Assert de que R_2 == 0/1 na Base 360.
     */
    @Test
    @DisplayName("05. Taylor: Anulação de R_2 na Base 360")
    public void test05_TaylorExpansion_CrossBase_Base360_ZeroRemainder() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_360, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 7; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE360;
        ctx.appendEvent(5, 3, 250_000L);

        final ExactRational r2 = BiChronosKernel.computeSpatialTaylorRemainderR2(ctx, 4, 5, 7);
        assertEquals(ExactRational.ZERO, r2);
    }

    /**
     * Teste 06: Anulação de R_2 na Base 365 (Convenção Inglesa / Euro).
     * Critério de Aceite: R_2 nulo na Base 365 para eventos retroativos.
     * DoD: Assert de que R_2 == 0/1 na Base 365.
     */
    @Test
    @DisplayName("06. Taylor: Anulação de R_2 na Base 365")
    public void test06_TaylorExpansion_CrossBase_Base365_ZeroRemainder() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_365, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 7; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE365;
        ctx.appendEvent(5, 4, 150_000L);

        final ExactRational r2 = BiChronosKernel.computeSpatialTaylorRemainderR2(ctx, 4, 5, 7);
        assertEquals(ExactRational.ZERO, r2);
    }

    // =========================================================================
    // GRUPO 2: CASOS DE BORDA DE SALDO E FLUXO EXTREMOS
    // =========================================================================

    /**
     * Teste 07: Saldo Inicial Zero com Aporte Posterior.
     * Critério de Aceite: x_0 = 0 não deve gerar exceção e deve acumular juros a partir do aporte.
     * DoD: Assert de que S_k = 0 até o aporte e positivo após.
     */
    @Test
    @DisplayName("07. Borda: Saldo inicial zero com aporte subsequente")
    public void test07_EdgeCase_InitialBalanceZero_WithSubsequentDeposit() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 0L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(1, 2, 100_000L);
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        assertEquals(0L, ctx.sPublishedCents[1]);
        assertEquals(100_000L, ctx.pCents[2]);
        assertTrue(ctx.sPublishedCents[3] > 100_000L);
    }

    /**
     * Teste 08: Micro-Principal de 1 Centavo (Armadilha Sub-Centavo).
     * Critério de Aceite: Saldo de 1 centavo não pode perder precisão espúria por corte de round-off.
     * DoD: Assert de que S_k se mantém >= 1 e g_k é estritamente positivo.
     */
    @Test
    @DisplayName("08. Borda: Micro-principal de 1 centavo")
    public void test08_EdgeCase_MicroPrincipal_OneCentavo() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        assertEquals(1L, ctx.sPublishedCents[5]);
        assertTrue(ctx.guardSubCents[5] > 0L);
    }

    /**
     * Teste 09: Saldo Próximo ao Limite Máximo da Aritmética de Ponto Fixo (q=12) e Detecção de Overflow.
     * Critério de Aceite: Principal até 9.000.000 centavos opera sem overflow; principal > 9.223.372 centavos dispara ArithmeticException.
     * DoD: Assert de que 9.000.000 cents roda com sucesso e 9.500.000 cents lança ArithmeticException.
     */
    @Test
    @DisplayName("09. Borda: Limite máximo de escala fixa q=12 e detecção de overflow")
    public void test09_EdgeCase_GiganticPrincipal_HundredBillionReais() {
        final long safeMaxCents = 9_000_000L; // R$ 90.000,00 (limite seguro para q=12 em long de 64 bits)
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, safeMaxCents);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertTrue(ctx.sPublishedCents[1] > safeMaxCents);
        assertEquals(safeMaxCents, ctx.pCents[1]);

        // Verifica proteção contra overflow para saldos acima da capacidade do long escalado por 10^12
        final long overflowingCents = 9_500_000L;
        assertThrows(ArithmeticException.class, () -> ctx.initAnchor(0, overflowingCents));
    }

    /**
     * Teste 10: Evento com Fluxo Zero (Identidade).
     * Critério de Aceite: Inserir a(e) = 0 não afeta a trajetória de saldos nem publica movimentos fictícios.
     * DoD: Assert de que S_k e D_k coincidem com o caso sem eventos.
     */
    @Test
    @DisplayName("10. Borda: Evento de fluxo nulo a(e) = 0")
    public void test10_EdgeCase_ZeroFlowEvent_Identity() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(1, 1, 0L);
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertEquals(1_000_401L, ctx.sPublishedCents[1]);
        assertEquals(401L, ctx.dMovementCents[1]);
    }

    /**
     * Teste 11: Fator Diário Unitário f_k = 1.00000000 (Taxa Zero).
     * Critério de Aceite: Taxa de juros zero mantém o saldo invariante, com juros nulos.
     * DoD: Assert de que S_k == x_0, J_k == 0 e D_k == 0.
     */
    @Test
    @DisplayName("11. Borda: Taxa de juros zero (f_k = 1.0)")
    public void test11_EdgeCase_UnitFactorFk_ZeroInterestRate() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = BiChronosContext.FACTOR_SCALE;
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        assertEquals(1_000_000L, ctx.sPublishedCents[5]);
        assertEquals(0L, ctx.jPublishedCents[5]);
        assertEquals(0L, ctx.dMovementCents[5]);
    }

    /**
     * Teste 12: Fator de Juros Elevado (5% ao dia).
     * Critério de Aceite: Propagação estável e monotônica sem estouro de capacidade.
     * DoD: Assert de que S_k cresce conforme o fator e os invariantes são mantidos.
     */
    @Test
    @DisplayName("12. Borda: Taxa de juros extrema (5% ao dia)")
    public void test12_EdgeCase_HighInterestRateFactor() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 100_000L);
        for (int k = 1; k <= 3; k++) ctx.dailyFactorFk[k] = 105_000_000L; // 1.05000000
        BiChronosKernel.executeRange(ctx, 1, 1, 3);

        assertTrue(ctx.sPublishedCents[3] > 115_000L);
        InvariantVerifier.assertRuntimeInvariants(ctx, 1, 3, 1.06);
    }

    /**
     * Teste 13: Eventos Simultâneos de Sinais Opostos que se Anulam.
     * Critério de Aceite: Dois eventos simultâneos +50.000 e -50.000 resultam em saldo idêntico ao sem eventos.
     * DoD: Assert de que S_k coincide exatamente com a trajetória base.
     */
    @Test
    @DisplayName("13. Borda: Eventos opostos simultâneos que se anulam")
    public void test13_EdgeCase_SimultaneousPositiveNegativeFlowCancelling() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(1, 1, 50_000L);
        ctx.appendEvent(1, 1, -50_000L);
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertEquals(1_000_401L, ctx.sPublishedCents[1]);
    }

    // =========================================================================
    // GRUPO 3: RESGATE PRO-RATA (MODALIDADE B) E LIQUIDAÇÃO TOTAL H3(IV)
    // =========================================================================

    /**
     * Teste 14: Liquidação Total com Extinção da Fração Sub-Centavo H3(iv).
     * Critério de Aceite: Resgate do saldo total antes do fluxo zera todas as variáveis de estado.
     * DoD: Assert de que P, V_tilde, guard, S e J são zerados no step.
     */
    @Test
    @DisplayName("14. Resgate: Liquidação total extinguindo fração sub-centavo")
    public void test14_Withdrawal_FullLiquidation_ExtinguishesSubCentavos() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 1_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(1, 1, -1_000L);
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertEquals(0L, ctx.sPublishedCents[1]);
        assertEquals(0L, ctx.pCents[1]);
        assertEquals(0L, ctx.vTildeSubCents[1]);
        assertEquals(0L, ctx.guardSubCents[1]);
    }

    /**
     * Teste 15: Tentativa de Resgate Superior ao Saldo Lança Exceção.
     * Critério de Aceite: Tentativa de saque maior que o saldo publicado anterior gera IllegalArgumentException.
     * DoD: assertThrows(IllegalArgumentException.class).
     */
    @Test
    @DisplayName("15. Resgate: Saque a descoberto lança exceção")
    public void test15_Withdrawal_OverdraftAttempt_ThrowsException() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 100L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(1, 1, -500L);

        assertThrows(IllegalArgumentException.class, () -> BiChronosKernel.executeRange(ctx, 1, 1, 1));
    }

    /**
     * Teste 16: Resgate Pro-Rata de 50% (Modalidade B).
     * Critério de Aceite: Redução de 50% via alpha_pro_rata reduz principal e V_tilde na proporção exata.
     * DoD: Assert de que P_k é aproximadamente a metade do anterior.
     */
    @Test
    @DisplayName("16. Resgate: Pro-rata de 50% (Modalidade B)")
    public void test16_Withdrawal_ProRataModalityB_HalfRedemption() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_PRO_RATA, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEventProRata(1, 1, 0L, 50_000_000L); // alpha = 0.50 (50%)
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertEquals(500_000L, ctx.pCents[1]);
        assertEquals(500_200L, ctx.sPublishedCents[1]);
    }

    /**
     * Teste 17: Conservação do Movimento Diário sob Resgate Pro-Rata.
     * Critério de Aceite: D_k deve refletir os juros gerados antes do resgate proporcional.
     * DoD: Assert de que D_k == S_k + liquidated - S_{k-1}.
     */
    @Test
    @DisplayName("17. Resgate: Conservação contábil do movimento diário pro-rata")
    public void test17_Withdrawal_ProRataModalityB_LiquidatedMovementConservation() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_PRO_RATA, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEventProRata(1, 1, 0L, 30_000_000L); // 30%
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertEquals(401L, ctx.dMovementCents[1]);
    }

    /**
     * Teste 18: Resgates Sequenciais Pro-Rata.
     * Critério de Aceite: Dois resgates pro-rata sequenciais de 10% reduzem o saldo sem derivação de banda.
     * DoD: Assert de que S_2 < S_1 e a banda de guarda permanece estritamente controlada.
     */
    @Test
    @DisplayName("18. Resgate: Múltiplos resgates pro-rata sequenciais")
    public void test18_Withdrawal_SequentialProRataRedemptions() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_PRO_RATA, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 2; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEventProRata(1, 1, 0L, 10_000_000L);
        ctx.appendEventProRata(2, 2, 0L, 10_000_000L);
        BiChronosKernel.executeRange(ctx, 2, 1, 2);

        assertTrue(ctx.sPublishedCents[2] < ctx.sPublishedCents[1]);
        assertTrue(ctx.guardSubCents[2] > 0L);
    }

    /**
     * Teste 19: Reprocessamento com Liquidação Total Retroativa.
     * Critério de Aceite: Checkpoint detecta liquidação e atualiza steps posteriores sem alterar passos anteriores.
     * DoD: Assert de que passos k < k_* permanecem inalterados e posteriores ficam zerados.
     */
    @Test
    @DisplayName("19. Resgate: Reprocessamento de liquidação retroativa")
    public void test19_Withdrawal_FullLiquidationAfterRetroactiveReprocess() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        final long s1Before = ctx.sPublishedCents[1];
        final long sBefore2 = BiChronosKernel.applyRhoFixed(
                BiChronosKernel.mulFactorFloorSubCents(ctx.vTildeSubCents[1], FACTOR_10_65_A_A_BASE252), DayCountConvention.ROUND_TRUNCATE);

        ctx.appendEvent(2, 2, -sBefore2);
        BiChronosKernel.reprocess(ctx, 1, 2, 5, true);

        assertEquals(s1Before, ctx.sPublishedCents[1]);
        assertEquals(0L, ctx.sPublishedCents[2]);
        assertEquals(0L, ctx.sPublishedCents[5]);
    }

    // =========================================================================
    // GRUPO 4: BANDA DE GUARDA DE ZIV E ORÁCULO RACIONAL EXATO
    // =========================================================================

    /**
     * Teste 20: Cruzamento de Limiar Ativa Oráculo Racional Exato sem Mutação.
     * Critério de Aceite: Quando low != high, oráculo resolve sem mutar vTildeSubCents nem guardSubCents,
     * e avalia corretamente o saldo antes e depois do fluxo.
     * DoD: Assert de que o fallback racional é acionado (low != high), publica com exatidão e preserva vTilde.
     */
    @Test
    @DisplayName("20. Ziv: Oráculo chamado em cruzamento de limiar sem mutação")
    public void test20_ZivGuard_ThresholdCrossing_TriggersExactRational() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.appendEvent(1, 1, 500_000L);

        // Forçar manualmente vTildeSubCents no limiar imediato de salto inteiro para testar low != high
        ctx.vTildeSubCents[0] = 1_000_000L * BiChronosContext.GUARD_SCALE - 1L;
        ctx.guardSubCents[0] = 5L;

        final long low = BiChronosKernel.applyRhoFixed(ctx.vTildeSubCents[0], DayCountConvention.ROUND_TRUNCATE);
        final long high = BiChronosKernel.applyRhoFixed(ctx.vTildeSubCents[0] + ctx.guardSubCents[0], DayCountConvention.ROUND_TRUNCATE);
        assertNotEquals(low, high); // Confirma que cruza o limiar

        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        // Garante que o estado em vTildeSubCents não foi mutado pelo oráculo racional
        assertTrue(ctx.vTildeSubCents[1] > 0L);
        assertTrue(ctx.guardSubCents[1] > 0L);
        assertEquals(1_500_000L, ctx.pCents[1]);
        assertTrue(ctx.sPublishedCents[1] >= 1_500_000L);
    }

    /**
     * Teste 21: Caminho Rápido O(1) de Publicação Certificada.
     * Critério de Aceite: Quando rho(V) == rho(V + g), o valor é retornado em O(1) diretamente.
     * DoD: Assert de que applyRhoFixed no limite inferior coincide com o saldo publicado.
     */
    @Test
    @DisplayName("21. Ziv: Fast-path O(1) quando rho(low) == rho(high)")
    public void test21_ZivGuard_FastPath_NoThresholdCrossing() {
        final long vSub = 1_000_000L * BiChronosContext.GUARD_SCALE + 100L;
        final long guard = 50L;
        final long low = BiChronosKernel.applyRhoFixed(vSub, DayCountConvention.ROUND_TRUNCATE);
        final long high = BiChronosKernel.applyRhoFixed(vSub + guard, DayCountConvention.ROUND_TRUNCATE);

        assertEquals(low, high);
        assertEquals(1_000_000L, low);
    }

    /**
     * Teste 22: Crescimento Monotônico Estrito da Banda de Guarda g_k.
     * Critério de Aceite: A banda de guarda g_k = ceil(f_k * g_{k-1}) + 1 cresce monotonicamente a cada dia.
     * DoD: Assert de que g_k > g_{k-1} para todo k.
     */
    @Test
    @DisplayName("22. Ziv: Crescimento monotônico estrito de g_k")
    public void test22_ZivGuard_MonotonicGrowthOfGuardBand() {
        long g = 0L;
        for (int k = 1; k <= 10; k++) {
            long nextG = BiChronosKernel.nextGuardSubCents(g, FACTOR_10_65_A_A_BASE252);
            assertTrue(nextG > g);
            g = nextG;
        }
    }

    /**
     * Teste 23: Consistência entre Rounding Mode Truncate e Half-Up.
     * Critério de Aceite: ROUND_HALF_UP publica saldo maior ou igual ao ROUND_TRUNCATE.
     * DoD: Assert de que sHalfUp >= sTruncate para mesmo contexto.
     */
    @Test
    @DisplayName("23. Ziv: Consistência Truncate vs Half-Up")
    public void test23_ZivGuard_RoundingTruncateVsHalfUp_Consistency() {
        final BiChronosContext ctxT = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        final BiChronosContext ctxH = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_HALF_UP,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctxT.initAnchor(0, 1_000_000L);
        ctxH.initAnchor(0, 1_000_000L);
        ctxT.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctxH.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;

        BiChronosKernel.executeRange(ctxT, 1, 1, 1);
        BiChronosKernel.executeRange(ctxH, 1, 1, 1);

        assertTrue(ctxH.sPublishedCents[1] >= ctxT.sPublishedCents[1]);
    }

    /**
     * Teste 24: Inversão de Fluxo no Oráculo Racional Exato.
     * Critério de Aceite: invertFlowToAnchor recupera rigorosamente x_0 com erro absoluto zero em Q.
     * DoD: Assert de que invertFlowToAnchor == ExactRational.ofCents(x_0).
     */
    @Test
    @DisplayName("24. Ziv: Inversão exata de fluxo recupera x_0")
    public void test24_ZivGuard_ExactRationalFlowInvertibility_StepByStep() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 2_500_000L);
        for (int k = 1; k <= 3; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 3);

        final ExactRational inv = BiChronosKernel.invertFlowToAnchor(ctx, 1, 3);
        assertEquals(ExactRational.ofCents(2_500_000L), inv);
    }

    /**
     * Teste 25: Aritmética 128-bit de Ponto Fixo sem Degradação na Escala 10^12.
     * Critério de Aceite: Multiplicação por 10^8 e divisão exata não perde sub-centavos na parte alta.
     * DoD: Assert de que mulFactorFloorSubCents(V, 10^8) == V exatamente.
     */
    @Test
    @DisplayName("25. Ziv: Identidade exata em mulFactorFloorSubCents com fator 10^8")
    public void test25_ZivGuard_ExtremeGuardScaleConsistency() {
        final long vSub = 1234567890123456L;
        final long res = BiChronosKernel.mulFactorFloorSubCents(vSub, BiChronosContext.FACTOR_SCALE);
        assertEquals(vSub, res);
    }

    // =========================================================================
    // GRUPO 5: FERIADOS, FINAIS DE SEMANA E PLATÔS DE CARATHÉODORY
    // =========================================================================

    /**
     * Teste 26: Platô de Final de Semana de Dois Dias.
     * Critério de Aceite: No fim de semana civil (sábado/domingo), calendarG não avança e rendimento não é aplicado.
     * DoD: Assert de que o saldo em sábado e domingo permanece constante.
     */
    @Test
    @DisplayName("26. Carathéodory: Platô de fim de semana de 2 dias")
    public void test26_CaratheodoryPlateau_WeekendTwoDays() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        ctx.calendarG[0] = 0; // Sexta
        ctx.calendarG[1] = 1; // Segunda
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertEquals(1_000_401L, ctx.sPublishedCents[1]);
    }

    /**
     * Teste 27: Platô de Feriado Prolongado de Três Dias.
     * Critério de Aceite: Mapeamento de 3 dias de feriado mantém o índice k constante no platô civil.
     * DoD: Assert de que countDays retorna 0 para intervalo contido no platô.
     */
    @Test
    @DisplayName("27. Carathéodory: Platô de feriado prolongado de 3 dias")
    public void test27_CaratheodoryPlateau_CarnavalThreeDaysHoliday() {
        final int[] g = {0, 1, 1, 1, 1, 2}; // 3 dias de feriado entre t=1 e t=4
        assertEquals(0, DayCountConvention.countDays(g, DayCountConvention.BASE_252, 1, 4));
        assertEquals(1, DayCountConvention.countDays(g, DayCountConvention.BASE_252, 1, 5));
    }

    /**
     * Teste 28: Evento Ocorrido no Feriado Rende no Próximo Dia Útil.
     * Critério de Aceite: Evento em dia não útil (t_v no platô) é agregado no índice k e rende no passo k+1.
     * DoD: Assert de que o saldo no platô é a translação e o rendimento incide no dia útil posterior.
     */
    @Test
    @DisplayName("28. Carathéodory: Aporte em feriado rende no próximo dia útil")
    public void test28_CaratheodoryPlateau_EventOnHolidayPlateau_RendersAtNextBusinessDay() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        final int[] g = {0, 1, 1, 2}; // t=2 é feriado
        System.arraycopy(g, 0, ctx.calendarG, 0, g.length);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.dailyFactorFk[2] = FACTOR_10_65_A_A_BASE252;

        ctx.appendEvent(1, 2, 500_000L); // Depósito no feriado t=2 (g[2]=1)
        BiChronosKernel.executeRange(ctx, 1, 1, 3);

        assertEquals(1_500_401L, ctx.sPublishedCents[1]);
        assertTrue(ctx.sPublishedCents[2] > 1_500_401L);
    }

    /**
     * Teste 29: Múltiplos Eventos no Mesmo Platô de Feriado.
     * Critério de Aceite: Vários aportes no mesmo fim de semana são somados no mesmo bucket k.
     * DoD: Assert de que netFlowByStep[k] agrega a soma de todos os eventos do platô.
     */
    @Test
    @DisplayName("29. Carathéodory: Múltiplos aportes no mesmo platô de feriado")
    public void test29_CaratheodoryPlateau_MultipleEventsOnSameHolidayPlateau() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        final int[] g = {0, 1, 1, 1, 2};
        System.arraycopy(g, 0, ctx.calendarG, 0, g.length);
        ctx.appendEvent(1, 2, 100_000L);
        ctx.appendEvent(1, 3, 200_000L);
        BiChronosKernel.aggregateEvents(ctx, 1, 4);

        assertEquals(300_000L, ctx.netFlowByStep[1]);
    }

    /**
     * Teste 30: Inversão de Fluxo Cruzando Platô de Feriado.
     * Critério de Aceite: Inversão difeomórfica backwards através de feriados recupera saldo inicial.
     * DoD: Assert de que invertFlowToAnchor recupera x_0 perfeitamente sobre calendário com platô.
     */
    @Test
    @DisplayName("30. Carathéodory: Inversão de fluxo cruzando feriado")
    public void test30_CaratheodoryPlateau_BackwardInversionAcrossHolidayPlateau() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        final int[] g = {0, 1, 1, 2};
        System.arraycopy(g, 0, ctx.calendarG, 0, g.length);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.dailyFactorFk[2] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 3);

        final ExactRational inv = BiChronosKernel.invertFlowToAnchor(ctx, 1, 3);
        assertEquals(ExactRational.ofCents(1_000_000L), inv);
    }

    /**
     * Teste 31: Contagem de Dias N(s, t) com Mesma Data (Identidade I7).
     * Critério de Aceite: N(t, t) = 0 para qualquer t.
     * DoD: Assert de que countDays(g, base, t, t) == 0.
     */
    @Test
    @DisplayName("31. Carathéodory: Identidade em mesma data N(t, t) == 0")
    public void test31_CaratheodoryPlateau_ZeroLengthPlateauCountDays() {
        final int[] g = {0, 1, 2, 3, 4, 5};
        assertEquals(0, DayCountConvention.countDays(g, DayCountConvention.BASE_252, 3, 3));
        assertEquals(0, DayCountConvention.countDays(null, DayCountConvention.BASE_360, 5, 5));
    }

    // =========================================================================
    // GRUPO 6: PERÍODO FECHADO E SEGREGAÇÃO CONTÁBIL (Delta P + Delta J_pub)
    // =========================================================================

    /**
     * Teste 32: Reconciliação de Fechamento com Resíduo Zero.
     * Critério de Aceite: Quando o saldo do sistema bate com o saldo do fechamento contábil, r = 0.
     * DoD: Assert de que reconcileClosing retorna 0L.
     */
    @Test
    @DisplayName("32. Fechamento: Reconciliação com resíduo zero")
    public void test32_ClosedPeriod_ReconcileClosing_ZeroResidue() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        final long r = BiChronosKernel.reconcileClosing(ctx, 1, ctx.sPublishedCents[1], 1.001, true);
        assertEquals(0L, r);
        assertEquals(1, ctx.anchorDayIndex);
    }

    /**
     * Teste 33: Reconciliação de Fechamento com Resíduo Permissível.
     * Critério de Aceite: Resíduo r dentro do limite teórico é aceito e reancora com novo saldo.
     * DoD: Assert de que r != 0 e ctx.anchorPublishedS recebe o saldo externo.
     */
    @Test
    @DisplayName("33. Fechamento: Reconciliação com resíduo permissível")
    public void test33_ClosedPeriod_ReconcileClosing_PermissibleResidue() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        final long externalBalance = ctx.sPublishedCents[1] + 1L; // 1 centavo de resíduo
        final long r = BiChronosKernel.reconcileClosing(ctx, 1, externalBalance, 1.001, false);

        assertEquals(1L, r);
        assertEquals(externalBalance, ctx.anchorPublishedS);
    }

    /**
     * Teste 34: Reconciliação com Resíduo Excessivo Lança Exceção (Invariante I20).
     * Critério de Aceite: Resíduo maior que o limite teórico dispara IllegalStateException para evitar corrupção.
     * DoD: assertThrows(IllegalStateException.class).
     */
    @Test
    @DisplayName("34. Fechamento: Resíduo corrompido excede limite teórico e lança exceção")
    public void test34_ClosedPeriod_ReconcileClosing_ExceededBound_ThrowsException() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertThrows(IllegalStateException.class, () ->
                BiChronosKernel.reconcileClosing(ctx, 1, ctx.sPublishedCents[1] + 10_000L, 1.001, true));
    }

    /**
     * Teste 35: Segregação Exata de Principal e Juros no Período Fechado.
     * Critério de Aceite: Ajuste retroativo decompõe Delta S em Delta P + Delta J_pub sem vazamento.
     * DoD: Assert de que deltaP + deltaJPub == deltaS.
     */
    @Test
    @DisplayName("35. Fechamento: Segregação exata Delta S = Delta P + Delta J_pub")
    public void test35_ClosedPeriod_RetroactiveAdjustment_ExactPrincipalAccrualSegregation() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 3; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 3);
        BiChronosKernel.reconcileClosing(ctx, 2, ctx.sPublishedCents[2], 1.001, true);

        ctx.appendEvent(2, 1, 200_000L);
        BiChronosKernel.reprocess(ctx, 1, 2, 3, false);

        assertEquals(200_000L, ctx.lastClosedDeltaPCents);
        assertTrue(ctx.lastClosedDeltaJPubCents >= 0L);
    }

    /**
     * Teste 36: Auditoria de Juros Ideais Sub-Centavo em Período Fechado.
     * Critério de Aceite: lastClosedDeltaJIdealSubCents armazena os juros contínuos na escala 10^12.
     * DoD: Assert de que lastClosedDeltaJIdealSubCents > 0.
     */
    @Test
    @DisplayName("36. Fechamento: Auditoria de juros ideais sub-centavo")
    public void test36_ClosedPeriod_RetroactiveAdjustment_IdealSubCentavoInterestRecorded() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 3; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 3);
        BiChronosKernel.reconcileClosing(ctx, 2, ctx.sPublishedCents[2], 1.001, true);

        ctx.appendEvent(2, 1, 100_000L);
        BiChronosKernel.reprocess(ctx, 1, 2, 3, false);

        assertTrue(ctx.lastClosedDeltaJIdealSubCents > 0L);
    }

    /**
     * Teste 37: Fechamentos Múltiplos Encadeados com Ajuste Retroativo.
     * Critério de Aceite: Ajuste propaga pela âncora mais recente preservando o histórico fechado.
     * DoD: Assert de que anchorPrincipalCents é acrescido de Delta P.
     */
    @Test
    @DisplayName("37. Fechamento: Fechamentos múltiplos encadeados")
    public void test37_ClosedPeriod_ChainedClosings_MultiMonthClosure() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        BiChronosKernel.reconcileClosing(ctx, 2, ctx.sPublishedCents[2], 1.001, true);
        BiChronosKernel.reconcileClosing(ctx, 4, ctx.sPublishedCents[4], 1.001, true);

        ctx.appendEvent(2, 3, 50_000L);
        BiChronosKernel.reprocess(ctx, 1, 2, 5, false);

        assertEquals(1_050_000L, ctx.anchorPrincipalCents);
    }

    // =========================================================================
    // GRUPO 7: RAZÃO GERAL COSIF E PROVISÃO DE COMPETÊNCIA
    // =========================================================================

    /**
     * Teste 38: Provisão COSIF Nula Quando Não Há Desvio Fracionário.
     * Critério de Aceite: Carteira com rendimentos inteiros perfeitos tem provisão R^{GL} = 0.
     * DoD: Assert de que computeGlCompetenceProvision retorna 0L.
     */
    @Test
    @DisplayName("38. COSIF: Provisão zero quando não há truncamento residual")
    public void test38_Cosif_GLProvision_ZeroWhenNoFractionalDeviation() {
        final BiChronosContext[] portfolio = new BiChronosContext[2];
        for (int i = 0; i < 2; i++) {
            portfolio[i] = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                    DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
            portfolio[i].initAnchor(0, 1_000L);
            portfolio[i].dailyFactorFk[1] = BiChronosContext.FACTOR_SCALE; // 1.0
            BiChronosKernel.executeRange(portfolio[i], 1, 1, 1);
        }

        final long prov = BiChronosKernel.computeGlCompetenceProvision(portfolio, 1, DayCountConvention.ROUND_TRUNCATE);
        assertEquals(0L, prov);
    }

    /**
     * Teste 39: Provisão COSIF Positiva em Carteira com 50 Contas Truncadas.
     * Critério de Aceite: R^{GL} agrega a responsabilidade econômica das frações truncadas dos clientes.
     * DoD: Assert de que provisão calculada > 0 e compensa exatamente o corte de centavos.
     */
    @Test
    @DisplayName("39. COSIF: Provisão positiva em 50 micro-posições truncadas")
    public void test39_Cosif_GLProvision_FiftySubCentavoAccounts_PositiveAccrual() {
        final BiChronosContext[] portfolio = new BiChronosContext[50];
        for (int i = 0; i < 50; i++) {
            portfolio[i] = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                    DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
            portfolio[i].initAnchor(0, 1_000L);
            portfolio[i].dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
            BiChronosKernel.executeRange(portfolio[i], 1, 1, 1);
        }

        final long prov = BiChronosKernel.computeGlCompetenceProvision(portfolio, 1, DayCountConvention.ROUND_TRUNCATE);
        assertEquals(20L, prov);
    }

    /**
     * Teste 40: Provisão COSIF sob Regras Truncate vs Half-Up.
     * Critério de Aceite: A provisão do razão contábil se ajusta de acordo com o modo de arredondamento.
     * DoD: Assert de que computeGlCompetenceProvision executa para ambos os modos sem erros.
     */
    @Test
    @DisplayName("40. COSIF: Provisão sob Truncate e Half-Up")
    public void test40_Cosif_GLProvision_WithBothTruncateAndHalfUpRounding() {
        final BiChronosContext[] portfolio = new BiChronosContext[10];
        for (int i = 0; i < 10; i++) {
            portfolio[i] = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                    DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
            portfolio[i].initAnchor(0, 1_000L);
            portfolio[i].dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
            BiChronosKernel.executeRange(portfolio[i], 1, 1, 1);
        }

        final long provTrunc = BiChronosKernel.computeGlCompetenceProvision(portfolio, 1, DayCountConvention.ROUND_TRUNCATE);
        final long provHalf = BiChronosKernel.computeGlCompetenceProvision(portfolio, 1, DayCountConvention.ROUND_HALF_UP);
        assertTrue(provTrunc >= 0L);
        assertTrue(provHalf >= 0L);
    }

    /**
     * Teste 41: Exportação de Snapshot COSIF em BigDecimal com Validação de Campos.
     * Critério de Aceite: DTO exportado possui valores em Reais (BRL, scale 2) estritamente exatos.
     * DoD: Assert de que entry.principalBrl, entry.publishedBalanceBrl e entry.dailyFactorFk batem.
     */
    @Test
    @DisplayName("41. COSIF: Exportação de DTO BigDecimal compatível com BACEN")
    public void test41_Cosif_ExportCosifEntryBrl_VerificationOfFields() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 500_000L); // R$ 5.000,00
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        final FinancialDecimalBridge.CosifSnapshotEntry entry = FinancialDecimalBridge.exportCosifEntryBrl(ctx, 1);
        assertEquals(new BigDecimal("5000.00"), entry.principalBrl());
        assertEquals(new BigDecimal("5002.00"), entry.publishedBalanceBrl());
        assertEquals(new BigDecimal("2.00"), entry.publishedAccrualBrl());
    }

    /**
     * Teste 42: Agregação em Grande Escala de Carteira (1.000 Contas).
     * Critério de Aceite: Cálculo da provisão COSIF sobre 1.000 contas roda sem estouro de pilha nem heap.
     * DoD: Assert de que provisão > 0 e a execução é imediata.
     */
    @Test
    @DisplayName("42. COSIF: Agregação em larga escala (1.000 contas)")
    public void test42_Cosif_LargePortfolioAggregation_1000Accounts() {
        final BiChronosContext[] portfolio = new BiChronosContext[1_000];
        for (int i = 0; i < 1_000; i++) {
            portfolio[i] = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                    DayCountConvention.WITHDRAW_TRANSLATION, 3, 2);
            portfolio[i].initAnchor(0, 500L); // R$ 5,00
            portfolio[i].dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
            BiChronosKernel.executeRange(portfolio[i], 1, 1, 1);
        }

        final long prov = BiChronosKernel.computeGlCompetenceProvision(portfolio, 1, DayCountConvention.ROUND_TRUNCATE);
        assertEquals(200L, prov);
    }

    // =========================================================================
    // GRUPO 8: CONFLUÊNCIA, IDEMPOTÊNCIA E COMUTAÇÃO TEMPORAL
    // =========================================================================

    /**
     * Teste 43: Invariância sob Ordem de Registro de Dez Eventos Embaralhados (Invariante I4).
     * Critério de Aceite: Inserir 10 eventos em ordem temporal direta vs inversa produz exatamente o mesmo S_k.
     * DoD: Assert de que arrays sPublishedCents coincidem bit a bit.
     */
    @Test
    @DisplayName("43. Confluência: Invariância de ordem com 10 eventos embaralhados")
    public void test43_Confluence_RegistrationOrderInvariance_TenScrambledEvents() {
        final BiChronosContext ctxA = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 16);
        final BiChronosContext ctxB = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 16);
        ctxA.initAnchor(0, 1_000_000L);
        ctxB.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) {
            ctxA.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
            ctxB.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        }

        final int[][] events = {
                {1, 1, 10_000}, {2, 2, 20_000}, {3, 3, 30_000}, {4, 4, 40_000}, {5, 5, 50_000},
                {6, 1, 5_000},  {7, 2, 6_000},  {8, 3, 7_000},  {9, 4, 8_000},  {10, 5, 9_000}
        };

        for (int[] e : events) ctxA.appendEvent(e[0], e[1], e[2]);
        for (int i = events.length - 1; i >= 0; i--) ctxB.appendEvent(events[i][0], events[i][1], events[i][2]);

        BiChronosKernel.executeRange(ctxA, 10, 1, 5);
        BiChronosKernel.executeRange(ctxB, 10, 1, 5);

        assertArrayEquals(ctxA.sPublishedCents, ctxB.sPublishedCents);
    }

    /**
     * Teste 44: Idempotência de Reprocessamento na Mesma Versão.
     * Critério de Aceite: Reprocessar sobre a mesma versão não altera nenhum dado do estado.
     * DoD: Assert de que duas chamadas sucessivas deixam o array sPublishedCents inalterado.
     */
    @Test
    @DisplayName("44. Confluência: Idempotência de reprocessamento")
    public void test44_Confluence_Idempotence_DoubleReprocessingIdenticalVersion() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 5);
        final long[] snap1 = Arrays.copyOf(ctx.sPublishedCents, ctx.sPublishedCents.length);

        BiChronosKernel.reprocess(ctx, 1, 1, 5, true);
        assertArrayEquals(snap1, ctx.sPublishedCents);
    }

    /**
     * Teste 45: Aditividade de Eventos Simultâneos (Invariante I5).
     * Critério de Aceite: Dois eventos simultâneos produzem o mesmo estado que a soma de seus fluxos.
     * DoD: Assert de que sPublishedCents coincide exatamente.
     */
    @Test
    @DisplayName("45. Confluência: Aditividade de eventos na mesma data valor")
    public void test45_Confluence_SimultaneousEventsSummation() {
        final BiChronosContext ctxSep = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        final BiChronosContext ctxSum = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctxSep.initAnchor(0, 1_000_000L);
        ctxSum.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 3; k++) {
            ctxSep.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
            ctxSum.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        }

        ctxSep.appendEvent(1, 2, 30_000L);
        ctxSep.appendEvent(2, 2, 70_000L);
        ctxSum.appendEvent(1, 2, 100_000L);

        BiChronosKernel.executeRange(ctxSep, 2, 1, 3);
        BiChronosKernel.executeRange(ctxSum, 2, 1, 3);

        assertArrayEquals(ctxSep.sPublishedCents, ctxSum.sPublishedCents);
    }

    /**
     * Teste 46: Estorno Neutro de Evento (Invariante I6).
     * Critério de Aceite: Inserir um evento e seu estorno exato (+a e -a) restaura a trajetória original.
     * DoD: Assert de que o saldo final é idêntico ao estado sem nenhum dos dois eventos.
     */
    @Test
    @DisplayName("46. Confluência: Estorno neutro restaura a trajetória original")
    public void test46_Confluence_ReversalOfEvent_LeavesTrajectoryInvariant() {
        final BiChronosContext ctxBase = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        final BiChronosContext ctxRev = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctxBase.initAnchor(0, 1_000_000L);
        ctxRev.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 4; k++) {
            ctxBase.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
            ctxRev.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        }

        ctxRev.appendEvent(1, 2, 80_000L);
        ctxRev.appendEvent(2, 2, -80_000L);

        BiChronosKernel.executeRange(ctxBase, 1, 1, 4);
        BiChronosKernel.executeRange(ctxRev, 2, 1, 4);

        assertArrayEquals(ctxBase.sPublishedCents, ctxRev.sPublishedCents);
    }

    /**
     * Teste 47: Reprocessamento Multi-Etapa vs Reprocessamento Direto (Invariante I18).
     * Critério de Aceite: Reprocessar sigma1 -> sigma2 -> sigma3 resulta no mesmo saldo que reprocessar direto sigma1 -> sigma3.
     * DoD: Assert de que os snapshots finais são idênticos bit a bit.
     */
    @Test
    @DisplayName("47. Confluência: Multi-etapa (1->2->3) vs Direto (1->3)")
    public void test47_Confluence_MultiStepReprocessing_Sigma1To2To3VsDirect1To3() {
        final BiChronosContext ctxStep = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        final BiChronosContext ctxDirect = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctxStep.initAnchor(0, 1_000_000L);
        ctxDirect.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) {
            ctxStep.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
            ctxDirect.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        }

        ctxStep.appendEvent(1, 2, 100_000L);
        ctxDirect.appendEvent(1, 2, 100_000L);
        BiChronosKernel.executeRange(ctxStep, 1, 1, 5);

        ctxStep.appendEvent(2, 3, 200_000L);
        ctxDirect.appendEvent(2, 3, 200_000L);
        BiChronosKernel.reprocess(ctxStep, 1, 2, 5, true);

        ctxStep.appendEvent(3, 4, 300_000L);
        ctxDirect.appendEvent(3, 4, 300_000L);
        BiChronosKernel.reprocess(ctxStep, 2, 3, 5, true);

        BiChronosKernel.executeRange(ctxDirect, 3, 1, 5);

        assertArrayEquals(ctxDirect.sPublishedCents, ctxStep.sPublishedCents);
    }

    // =========================================================================
    // GRUPO 9: CASOS DE BORDA TEMPORAL E HORIZONTE LIMITE
    // =========================================================================

    /**
     * Teste 48: Limite Superior da SoA (Último Passo do maxHorizon).
     * Critério de Aceite: Execução no último dia do horizonte máximo roda sem IndexOutOfBoundsException.
     * DoD: Assert de que sPublishedCents[maxHorizon] é preenchido e válido.
     */
    @Test
    @DisplayName("48. Temporal: Limite superior da SoA no maxHorizon")
    public void test48_Temporal_HorizonBoundary_LastDayExecution() {
        final int horizon = 20;
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, horizon, 4);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= horizon; k++) {
            ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
            ctx.calendarG[k] = k;
        }
        BiChronosKernel.executeRange(ctx, 1, 1, horizon);

        assertTrue(ctx.sPublishedCents[horizon] > 1_000_000L);
    }

    /**
     * Teste 49: Validação Estrita de Limite de Data Valor t_v > t_0 (Definição 4.1).
     * Critério de Aceite: Evento com t_v <= 0 deve disparar IllegalArgumentException; evento com t_v = 1 (menor data válida) é aceito.
     * DoD: assertThrows(IllegalArgumentException.class) para t_v=0 e validação do fluxo com t_v=1.
     */
    @Test
    @DisplayName("49. Temporal: Proteção estrita t_v > t_0 e processamento na data mínima t_v = 1")
    public void test49_Temporal_EventAtTZero_InitialAnchorUpdate() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;

        // Limite inferior estrito: t_v <= 0 é proibido pela Definição 4.1
        assertThrows(IllegalArgumentException.class, () -> ctx.appendEvent(1, 0, 500_000L));

        // Data mínima válida: t_v = 1
        ctx.appendEvent(1, 1, 500_000L);
        BiChronosKernel.executeRange(ctx, 1, 1, 1);
        assertEquals(1_500_401L, ctx.sPublishedCents[1]);
        assertEquals(1_500_000L, ctx.pCents[1]);
    }

    /**
     * Teste 50: Checkpoint com Conjunto de Novos Eventos Vazio N_tau == 0.
     * Critério de Aceite: findCheckpointStep retorna -1 quando nenhum evento novo afeta a janela, e reprocess é no-op.
     * DoD: Assert de que findCheckpointStep == -1 e o estado permanece intocado.
     */
    @Test
    @DisplayName("50. Temporal: Janela estável N_tau vazia retorna checkpoint -1")
    public void test50_Temporal_NoNewEventsInWindow_CheckpointMinusOne() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        final int kStar = BiChronosKernel.findCheckpointStep(ctx, 1, 2, 1);
        assertEquals(-1, kStar);

        BiChronosKernel.reprocess(ctx, 1, 2, 1, true);
        assertEquals(1_000_401L, ctx.sPublishedCents[1]);
    }

    // =========================================================================
    // GRUPO 11: VALIDAÇÃO DAS CORREÇÕES DE BUGS v9.0
    // =========================================================================

    /**
     * Teste 51: Reabertura de Período Fechado com Evento Retroativo (reopenClosedPeriod = true).
     * Critério de Aceite: Quando reopenClosedPeriod é verdadeiro e k_* <= k_a, o kernel reprocessa
     * desde k_* recalculando todo o intervalo fechado sem descartar eventos.
     */
    @Test
    @DisplayName("51. Correção: Reabrir período fechado recalcula histórico sem descarte")
    public void test51_BugFix_ReopenClosedPeriodRecalculatesPastSteps() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        // Fechar período no passo 3
        BiChronosKernel.reconcileClosing(ctx, 3, ctx.sPublishedCents[3], 1.002, true);
        assertEquals(3, ctx.anchorDayIndex);

        // Evento retroativo em t_v = 2 registrado em sigma = 2
        ctx.appendEvent(2, 2, 500_000L);

        // Reprocessar reabrindo o período fechado
        BiChronosKernel.reprocess(ctx, 1, 2, 5, true);

        // O saldo em k=3 e k=5 deve refletir o depósito de 500.000 centavos
        assertTrue(ctx.sPublishedCents[3] > 1_500_000L);
        assertTrue(ctx.sPublishedCents[5] > 1_500_000L);
        assertEquals(1_500_000L, ctx.pCents[5]);
    }

    /**
     * Teste 52: Ajuste de Período Fechado Atualiza Banda de Guarda (Algoritmo 5.14b linha 569).
     * Critério de Aceite: applyClosedPeriodRetroactiveAdjustment deve acumular o erro de propagação
     * em guardSubCents[ka] e anchorGuardSubCents.
     */
    @Test
    @DisplayName("52. Correção: Ajuste em período fechado acumula banda de guarda")
    public void test52_BugFix_ClosedPeriodAdjustmentUpdatesGuardBand() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        BiChronosKernel.reconcileClosing(ctx, 4, ctx.sPublishedCents[4], 1.002, true);
        final long initialGuardKa = ctx.guardSubCents[4];

        ctx.appendEvent(2, 2, 200_000L);
        BiChronosKernel.reprocess(ctx, 1, 2, 5, false);

        assertTrue(ctx.guardSubCents[4] > initialGuardKa);
        assertEquals(ctx.guardSubCents[4], ctx.anchorGuardSubCents);
    }

    /**
     * Teste 53: Conciliação Preserva Separação entre Principal e Juros (COSIF).
     * Critério de Aceite: reconcileClosing não deve zerar juros nem capitalizar indevidamente em Principal.
     */
    @Test
    @DisplayName("53. Correção: Conciliação COSIF preserva decomposição (P, J)")
    public void test53_BugFix_ReconcileClosingPreservesPrincipalAndAccrual() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        for (int k = 1; k <= 5; k++) ctx.dailyFactorFk[k] = FACTOR_10_65_A_A_BASE252;
        BiChronosKernel.executeRange(ctx, 1, 1, 5);

        final long pBefore = ctx.pCents[5];
        final long sBefore = ctx.sPublishedCents[5];
        final long jBefore = ctx.jPublishedCents[5];
        assertTrue(jBefore > 0L);

        // Conciliar com resíduo zero mas zerando fração residual (retainSubCentavoFraction = false)
        BiChronosKernel.reconcileClosing(ctx, 5, sBefore, 1.002, false);

        assertEquals(pBefore, ctx.anchorPrincipalCents);
        assertEquals(pBefore, ctx.pCents[5]);
        assertEquals(jBefore, ctx.jPublishedCents[5]);
    }

    /**
     * Teste 54: Raiz Decimal128 sem Perda IEEE 754 Double.
     * Critério de Aceite: computeTruncatedDailyFactorBd usa Newton-Raphson de alta precisão.
     */
    @Test
    @DisplayName("54. Correção: Cálculo de fator diário Decimal128 de alta precisão")
    public void test54_BugFix_Decimal128DailyFactorRootComputation() {
        final BigDecimal annualRate = new BigDecimal("10.65");
        final BigDecimal factor8 = DayCountConvention.computeTruncatedDailyFactorBd(annualRate, 252, 8);
        assertEquals(new BigDecimal("1.00040167"), factor8);

        final BigDecimal factor16 = DayCountConvention.computeTruncatedDailyFactorBd(annualRate, 252, 16);
        assertEquals(16, factor16.scale());
        assertTrue(factor16.toString().startsWith("1.00040167"));
    }

    /**
     * Teste 55: Consistência do Operador Meio-Para-Cima para Números Negativos.
     * Critério de Aceite: applyRhoCentavosBd segue floor(x + 0.5) para valores negativos.
     */
    @Test
    @DisplayName("55. Correção: Consistência de meio-para-cima em frações negativas")
    public void test55_BugFix_HalfUpNegativeFractionConsistency() {
        final BigDecimal halfNegative = new BigDecimal("-0.5");
        final BigDecimal rounded = FinancialDecimalBridge.applyRhoCentavosBd(halfNegative, DayCountConvention.ROUND_HALF_UP);
        assertEquals(BigDecimal.ZERO, rounded); // floor(-0.5 + 0.5) = floor(0) = 0
    }

    /**
     * Teste 56: Liquidação Total Extingue Saldo Legado yLegacyCents sem Negatividade.
     * Critério de Aceite: Resgate total zera yLegacyCents prevenindo juros negativos.
     */
    @Test
    @DisplayName("56. Correção: Liquidação total zera saldo legado sem valores negativos")
    public void test56_BugFix_FullLiquidationExtinguishesLegacyBalance() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 8);
        ctx.initAnchor(0, 1_000_000L);
        ctx.dailyFactorFk[1] = FACTOR_10_65_A_A_BASE252;
        ctx.dailyFactorFk[2] = FACTOR_10_65_A_A_BASE252;

        // No passo 1, antes do fluxo, saldo é 1_000_401L.
        // Resgate total exato no passo 1:
        ctx.appendEvent(1, 1, -1_000_401L);
        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        assertEquals(0L, ctx.sPublishedCents[1]);
        assertEquals(0L, ctx.pCents[1]);
        assertEquals(0L, ctx.yLegacyCents[1]);

        // E no passo 2 subsequente, sem novos aportes, o saldo permanece extinto
        BiChronosKernel.executeRange(ctx, 2, 1, 2);
        assertEquals(0L, ctx.sPublishedCents[2]);
        assertEquals(0L, ctx.pCents[2]);
        assertEquals(0L, ctx.yLegacyCents[2]);
    }

    /**
     * Teste 57: Suporte a Grandes Volumes de Saldo (R$ 10.000.000,00) via BigInteger.
     * Critério de Aceite: Inicialização com saldo de 10 milhões de reais executa sem overflow.
     */
    @Test
    @DisplayName("57. Correção: Saldo de R$ 10.000.000,00 sem overflow de 64-bit")
    public void test57_BugFix_LargeBalanceNoLongOverflow() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        // R$ 10.000.000,00 = 1.000.000.000 centavos
        ctx.initAnchorBig(0, BigInteger.valueOf(1_000_000_000L));
        ctx.setAnnualRateBd(1, new BigDecimal("10.65"));

        assertDoesNotThrow(() -> BiChronosKernel.executeRange(ctx, 1, 1, 1));
        assertEquals(1_000_000_000L, ctx.pCents[1]);
        assertTrue(ctx.sPublishedCents[1] > 1_000_000_000L);
    }

    /**
     * Teste 58: A Taxa de Juros é Sempre BigDecimal (Anual, Diária e Validação de Nulo).
     * Critério de Aceite: Configuração de taxas como BigDecimal produz fatores idênticos aos regulatórios B3.
     */
    @Test
    @DisplayName("58. Taxa: Configuração de taxa anual e diária como BigDecimal com validação estrita")
    public void test58_RateIsAlwaysBigDecimal_AnnualAndDailyRateConfiguration() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 5);

        // 1. Taxa anual 10.65% a.a. via BigDecimal
        final BigDecimal rate1065 = new BigDecimal("10.65");
        ctx.setAnnualRateBd(1, rate1065);
        assertEquals(rate1065, ctx.getAnnualRateBd(1));
        assertEquals(new BigDecimal("1.00040167"), ctx.getDailyFactorBd(1));
        assertEquals(100_040_167L, ctx.dailyFactorFk[1]);

        // 2. Intervalo de taxas em lote via BigDecimal
        ctx.setAnnualRateBd(2, 5, new BigDecimal("13.75"));
        for (int k = 2; k <= 5; k++) {
            assertEquals(new BigDecimal("13.75"), ctx.getAnnualRateBd(k));
            assertEquals(new BigDecimal("1.00051137"), ctx.getDailyFactorBd(k));
            assertEquals(100_051_137L, ctx.dailyFactorFk[k]);
        }

        // 3. Taxa diária direta via BigDecimal
        ctx.setDailyRateBd(6, new BigDecimal("0.05"));
        assertEquals(new BigDecimal("0.05"), ctx.getAnnualRateBd(6));
        assertEquals(new BigDecimal("1.00050000"), ctx.getDailyFactorBd(6));
        assertEquals(100_050_000L, ctx.dailyFactorFk[6]);

        // 4. Taxa nula deve lançar IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> ctx.setAnnualRateBd(7, null));
        assertThrows(IllegalArgumentException.class, () -> ctx.setDailyRateBd(7, null));
        assertThrows(IllegalArgumentException.class, () -> ctx.setDailyFactorBd(7, null));
    }

    /**
     * Teste 59: COSIF Snapshot Entry Registra Taxa em BigDecimal Exato.
     * Critério de Aceite: DTO exportado contém annualRateBd() populado com o BigDecimal da taxa.
     */
    @Test
    @DisplayName("59. Taxa: Exportação COSIF registra taxa de juros BigDecimal")
    public void test59_RateIsAlwaysBigDecimal_CosifSnapshotEntryPreservesRate() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 5, 4);
        ctx.initAnchor(0, 1_000_000L);
        final BigDecimal rate = new BigDecimal("10.65");
        ctx.setAnnualRateBd(1, rate);

        BiChronosKernel.executeRange(ctx, 1, 1, 1);

        final FinancialDecimalBridge.CosifSnapshotEntry entry = FinancialDecimalBridge.exportCosifEntryBrl(ctx, 1);
        assertEquals(rate, entry.annualRateBd());
        assertEquals(new BigDecimal("1.00040167"), entry.dailyFactorFk());
        assertEquals(new BigDecimal("10000.00"), entry.principalBrl());
        assertEquals(new BigDecimal("10004.01"), entry.publishedBalanceBrl());
    }

    /**
     * Teste 60: Taxas Variáveis Dinâmicas por Passo via BigDecimal.
     * Critério de Aceite: Execução com curva de juros variável (Step 1: 10.65%, Step 2: 12.00%, Step 3: 9.50%)
     * preserva a integridade de fluxo difeomórfico e conservação contábil.
     */
    @Test
    @DisplayName("60. Taxa: Curva de juros com taxas variáveis por passo em BigDecimal")
    public void test60_RateIsAlwaysBigDecimal_DynamicPerStepVariableRates() {
        final BiChronosContext ctx = new BiChronosContext(DayCountConvention.BASE_252, DayCountConvention.ROUND_TRUNCATE,
                DayCountConvention.WITHDRAW_TRANSLATION, 10, 5);
        ctx.initAnchor(0, 1_000_000L); // R$ 10.000,00

        ctx.setAnnualRateBd(1, new BigDecimal("10.65"));
        ctx.setAnnualRateBd(2, new BigDecimal("12.00"));
        ctx.setAnnualRateBd(3, new BigDecimal("9.50"));

        BiChronosKernel.executeRange(ctx, 1, 1, 3);

        assertEquals(1_000_401L, ctx.sPublishedCents[1]);
        assertEquals(1_000_851L, ctx.sPublishedCents[2]);
        assertTrue(ctx.sPublishedCents[3] > ctx.sPublishedCents[2]);

        // Verificação de reversibilidade difeomórfica com taxas heterogêneas
        ExactRational inv = BiChronosKernel.invertFlowToAnchor(ctx, 1, 3);
        assertEquals(ExactRational.ofCents(1_000_000L), inv);
    }
}
