# BiChronos Engine (v9.0) — Java 21 Module (`io.quimera.labs.bichronos`)

Kernel determinístico para reprocessamento retroativo de saldos (*snapshots*) por data valor sob fluxo difeomórfico de Carathéodory, expansão de Taylor espacial com resto de 2ª ordem nulo ($R_2 = 0$), decomposição patrimonial $(P_k, J_k)$, banda de guarda de Ziv ($q = 12$) e verificação dos 20 invariantes formais.

## Filosofia de Design (Estilo Linus / Kernel-Grade)
1. **Módulo JPMS & Estruturas de Dados Planas (SoA - *Structure of Arrays*):** Módulo nomeado `io.quimera.labs.bichronos`. Zero alocações na *heap* durante a propagação (`BiChronosContext`). Eventos bitemporais e séries temporais residem em vetores primitivos contíguos (`long[]`, `int[]`) amigáveis à cache L1/L2.
2. **Aritmética Inteira de 128 bits Emulada em Ponto Fixo ($q = 12$):** A evolução $\tilde{V}_k = \lfloor 10^q f_k \tilde{V}_{k-1} \rfloor / 10^q + a_k$ e a banda de guarda $g_k = \lceil f_k g_{k-1} \rceil + 10^{-q}$ operam sem ponto flutuante no laço crítico. O oráculo racional exato (`ExactRational`) atua exclusivamente quando $[\tilde{V}_k, \tilde{V}_k + g_k]$ cruza uma descontinuidade de $\rho$, sem jamais mutar $(\tilde{V}_k, g_k)$ (preservando o determinismo estrito dos Invariantes I2, I17 e I18).
3. **Expansão de Taylor Espacial com Resto Nulo ($R_2 = 0$, Teorema 4.5):**
   - Diferencial de 1ª ordem exato em $\mathbb{Q}$: $\Delta\Psi_\tau = \left(\frac{\partial\Psi_\tau}{\partial x_*}\right)\delta x_* + \sum_{e \in N_\tau} \left(\frac{\partial\Psi_\tau}{\partial a(e)}\right) a(e)$;
   - Resto de Taylor de 2ª ordem estritamente nulo sob o regime linear ($R_2 \equiv 0/1$).
4. **Coesão Contábil COSIF / BACEN:**
   - Decomposição patrimonial explícita entre Principal ($P_k$) e Juros Acumulados ($J_{k,\text{pub}} = S_k - P_k$);
   - Suporte às Modalidades A (translação inteira) e B (baixa proporcional *pro-rata* por fração $\alpha_k$), além de extinção de fração residual em liquidação total (Hipótese H3(iv));
   - Provisão de Frações Sub-Centavo de Competência no Razão Geral ($R_k^{\text{GL}} = \rho(\sum_i V_k^{(i)}) - \sum_i S_k^{(i)}$);
   - Segregação inteira exata em períodos fechados ($\Delta J_{\text{pub}}(a) = (S_{\sigma'}(a) - S_\sigma(a)) - \Delta P$).

## Uso como Dependência Maven
```xml
<dependency>
    <groupId>io.quimera.labs.bichronos</groupId>
    <artifactId>bichronos-kernel</artifactId>
    <version>9.0.0</version>
</dependency>
```

## Execução do Kernel

Para compilar e executar o kernel diretamente com Java 21:

```bash
# Via Maven
mvn compile exec:java

# Ou via Jar executável
mvn package
java --add-modules jdk.incubator.vector -jar target/bichronos-kernel-9.0.0.jar

# Executar suíte completa de testes de integração e invariantes formais
mvn test
```
