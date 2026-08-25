# LSFG Analysis Report

## App package / coexistência

- **applicationId antigo:** `com.winlator`
- **applicationId novo:** `com.winlator.lsfg`
- **namespace preservado:** `com.winlator`
- **packages Java/Kotlin preservados:** sim; nenhum package, import ou diretório de código foi renomeado.
- **nome visível:** `Winlator LSFG`, por meio do recurso `app_name_lsfg` usado no manifest.
- **authority alterada:** `com.winlator.FileProvider` foi substituída por `${applicationId}.FileProvider` no manifest. A chamada de `FileProvider.getUriForFile()` agora deriva a mesma authority de `activity.getPackageName()`, resultando em `com.winlator.lsfg.FileProvider`.
- **permissions customizadas:** nenhuma encontrada.
- **intent filters, services e receivers dependentes do package:** nenhum conflito encontrado. O filtro launcher permanece inalterado e não há services ou receivers declarados no manifest principal.

### Referências hardcoded encontradas

Foram encontradas somente cinco referências de identidade/caminho que precisavam acompanhar o novo `applicationId`:

1. Authority `com.winlator.FileProvider` no manifest.
2. Authority `com.winlator.FileProvider` em `FileUtils.java`.
3. `/data/data/com.winlator/storage` em `AppUtils.java`, atualizado para o diretório privado de `com.winlator.lsfg` (a classe `BuildConfig` não é gerada pela configuração atual do projeto).
4. Caminhos privados do socket X11 e do socket Vortek nos headers nativos, atualizados para `/data/data/com.winlator.lsfg/files/...`.
5. Caminho privado de cache nativo em `winlator.h`, atualizado para `/data/data/com.winlator.lsfg/cache`.

As demais ocorrências de `com.winlator` são o namespace, nomes de classes, packages/imports Java ou nomes de views customizadas em layouts. Elas foram intencionalmente preservadas e não definem a identidade instalada do APK.

### Separação de dados

Como o Android identifica e isola aplicativos pelo `applicationId`, `com.winlator` e `com.winlator.lsfg` recebem UIDs e diretórios privados distintos. Assim, `dataDir`, `filesDir`, `cacheDir`, SharedPreferences, containers, RootFS e a cópia privada de `Lossless.dll` ficam separados. A build LSFG não aponta para os dados privados de `com.winlator`.

### Escopo preservado

Nenhuma mudança foi feita em packages Java, JNI, bibliotecas, assets, RootFS, LSFG-VK, shaders, seleção de `Lossless.dll`, containers, XServer, DXVK, Turnip ou lógica de frame generation. Os três headers nativos foram alterados exclusivamente porque continham caminhos privados absolutos vinculados ao applicationId antigo.

### Arquivos modificados para coexistência

- `app/app/build.gradle`
- `app/app/src/main/AndroidManifest.xml`
- `app/app/src/main/res/values/strings.xml`
- `app/app/src/main/java/com/winlator/core/FileUtils.java`
- `app/app/src/main/java/com/winlator/core/AppUtils.java`
- `app/app/src/main/cpp/gladiorenderer/include/gladio.h`
- `app/app/src/main/cpp/vortekrenderer/include/vortek.h`
- `app/app/src/main/cpp/winlator/include/winlator.h`
- `LSFG_ANALYSIS_REPORT.md`

### Conclusão

O APK com `applicationId` `com.winlator.lsfg` pode coexistir com o Winlator oficial `com.winlator`. Uma assinatura debug/custom diferente é esperada e não requer a assinatura oficial, pois os IDs dos aplicativos são diferentes.

### Validação

- `git diff --check`: concluído sem erros.
- `assembleDebug`: concluído com sucesso (`BUILD SUCCESSFUL`).
- APK: `app/app/build/outputs/apk/debug/app-debug.apk`.
- Package confirmado no APK por `aapt dump badging`: `com.winlator.lsfg`.
- Nome visível confirmado no APK: `Winlator LSFG`.
- Authorities confirmadas no manifest compilado:
  - `com.winlator.lsfg.FileProvider`
  - `com.winlator.lsfg.androidx-startup` (provider incorporado pelo AndroidX)
- Nenhum provider do APK final conserva uma authority de `com.winlator` que conflite com o aplicativo oficial.
- Assinatura verificada por `apksigner`: certificado `C=US, O=Android, CN=Android Debug`, uma assinatura v2 válida. Não foi usada nem solicitada a assinatura oficial.
- SHA-256 do APK: `cc096d1304c6048c6baa58092742d1e4677b9b155a65a358790596b0abbc152e`.

## Package migration fix

> Esta seção substitui a auditoria preliminar acima. A inspeção binária dos arquivos `.tzst` revelou dependências que uma busca textual comum não mostrava.

- **applicationId final:** `com.winlator.lsfg`
- **namespace:** `com.winlator` (preservado)
- **ocorrências `com.winlator` encontradas no escopo do projeto:** 1.377. Destas, 1.339 são declarações/imports Java e 27 são nomes de views Java em XML; permanecem corretamente como namespace. As restantes abrangem `applicationId`, manifest/componentes e referências de build/runtime auditadas individualmente.
- **categoria 1 — namespace Java:** packages, imports, nomes de Activities e views `com.winlator.*` foram mantidos. Eles identificam classes, não a instalação Android.
- **categoria 2 — identidade/runtime:** `applicationId`, FileProvider, diretórios privados, RootFS, interpretadores/RPATHs guest, X11, Vortek, cache e symlink da unidade `Z:` precisavam acompanhar a instalação.
- **categoria 3 — externa/documentação:** referências históricas de projeto/nome não executadas no APK não foram renomeadas; scripts de build que produzem binários runtime passaram a exigir `WINLATOR_ROOTFS` para evitar regenerar artefatos presos ao package oficial.

### Causa raiz e ponto de bloqueio

A RootFS e os drivers guest empacotados ainda continham o prefixo absoluto `/data/data/com.winlator/files/rootfs` dentro de ELF, loader glibc, Wine, configurações, symlinks e clientes de socket. Ao instalar como `com.winlator.lsfg`, a RootFS era extraída corretamente em `/data/user/0/com.winlator.lsfg/files/rootfs`, mas Box64/Wine e os clientes Gladio/Vortek continuavam procurando bibliotecas e sockets no diretório privado do app oficial. O bloqueio ocorre na transição **Box64/Wine iniciado -> conexão guest com XServer**, antes da primeira janela/frame, podendo aparecer apenas como loading infinito.

Os cinco assets runtime corrigidos são:

- `rootfs.tzst`: 0 ocorrências restantes do caminho privado antigo (antes havia centenas de referências em 447 strings completas relevantes, incluindo loader/RPATH/configuração).
- `rootfs_patches.tzst`: módulo ALSA relocável.
- `graphics_driver/gladio-1.1.tzst`: cliente X11 relocável.
- `graphics_driver/vortek-2.1.tzst`: cliente/socket e ICD Vulkan relocáveis.
- `container_pattern.tzst`: symlink `Z:` relocável.

Para não aumentar strings dentro de ELF, o prefixo de RootFS foi substituído por `/proc/self/cwd//////////////////////`, de comprimento idêntico. As barras repetidas são semanticamente equivalentes a uma só. `ProcessHelper.exec` já lança Box64 com `cwd` igual à RootFS real; portanto `/proc/self/cwd` resolve dinamicamente para a RootFS de qualquer instalação, sem hardcode de `com.winlator.lsfg`. O launcher também exporta `WINLATOR_PACKAGE_NAME`, `WINLATOR_DATA_DIR` e `WINLATOR_ROOTFS` derivados de `Context`/`ApplicationInfo`.

### Manifest, providers e isolamento

- FileProvider: `${applicationId}.FileProvider`; o Java usa `activity.getPackageName()+".FileProvider"`.
- Manifest compilado: package `com.winlator.lsfg`.
- Authorities compiladas: `com.winlator.lsfg.FileProvider` e `com.winlator.lsfg.androidx-startup`.
- Não há services, receivers, permissões customizadas, process names ou task affinities fixos que colidam com `com.winlator`.
- SharedPreferences, containers, banco/configuração, `filesDir`, `cacheDir` e RootFS continuam sob o UID/dataDir próprio de `com.winlator.lsfg`; não há tentativa intencional de ler dados do app oficial.

### Sockets/IPC e tracing

X11 (`tmp/.X11-unix/X0`), Vortek (`tmp/.vortek/V0`), SysV SHM, ALSA, Pulse e VirGL são construídos no host a partir de `RootFS.getRootDir()` e `UnixSocketConfig`. Os clientes guest X11/Vortek agora resolvem a mesma RootFS relocável. Foram adicionados logs `START_CONTAINER_1`, `ROOTFS_READY`/`ROOTFS_INVALID`, `START_CONTAINER_2`, `XSERVER_STARTED`, `BOX64_STARTING`, `BOX64_STARTED`, `WINE_STARTED`, `GUEST_EXIT` e `FIRST_FRAME_RECEIVED`, incluindo packageName, dataDir, filesDir, rootfsDir, cacheDir, nativeLibraryDir, HOME, PATH, LD_LIBRARY_PATH, TMPDIR, DISPLAY e sockets.

### LSFG e arquivos alterados pela migração

O core `liblsfg-vk`, shaders, MAILBOX, sincronização, Flow Scale, Performance Mode, multiplier e geração de frames não foram modificados por esta correção. `Lossless.dll` e `conf.toml` continuam derivados de `getFilesDir()`/`RootFS`; `liblsfg-vk.so` e o manifest Vulkan são instalados pela `RootFS` real.

Arquivos adicionais da correção de package: os cinco `.tzst` listados acima; `GuestProgramLauncherComponent.java`; `XServerDisplayActivity.java`; fontes/builds `gladio/*`, `vortek/*` e `android_alsa/*`. As mudanças anteriores de `build.gradle`, manifest, FileProvider e headers JNI permanecem.

### Resultado de build e limites do teste

- `git diff --check`: passou sem erros.
- `assembleDebug`: **BUILD SUCCESSFUL** (34 tasks; avisos existentes de Java 8/AGP/compileSdk).
- APK: `app/app/build/outputs/apk/debug/app-debug.apk` (SHA-256 `837a28ae19e9dc21e91348be670c2b123aa9c0a4e0d0df9163dab3187553de24`).
- `aapt dump badging`: package `com.winlator.lsfg`, versionCode 32, versionName 11.2.
- Todos os cinco assets auditados têm zero ocorrência de `/data/data/com.winlator` após a correção.
- Não havia dispositivo Android/ADB disponível neste ambiente. Assim, instalação simultânea, chegada ao desktop, execução de jogo e ativação LSFG não foram alegadas como testes físicos; o APK, manifest, assets e fluxo estático foram validados, e os logs adicionados permitem confirmar cada marco no dispositivo.

## Part 2 - Adreno A6xx compatibility

### Baseline funcional da Parte 2

- **CONFIRMADO:** a build limpa foi validada no Moto G34/Adreno 619 pelo usuário: Tomb Raider 2013 inicia sem o overhead antigo e 2x, 3x e 4x funcionam sem logs pesados.
- **CONFIRMADO:** o baseline parte de `HEAD e5c7fa8da722d32f56b8b4f5a06ecdc327f015df`, branch `feature/lsfg-vk`, mais seis mudanças locais que removem a instrumentação e recompilam a layer. Antes deste relatório, o diff tinha 106 inserções, 410 remoções e SHA-256 `04f8292bea7b109e7c1cad591c1bd4f7b2851e4e28ec9c1b75059972d4378412`.
- **CONFIRMADO:** `liblsfg-vk.so` validada: SHA-256 `d85894399380f7e72a074f0ccd480b37ebfc43b7170d517947d2c54c142f5b7a`; APK validado: SHA-256 `b793ea379fe1a0fd78f21a36cbb5ea9c53e161ae4d7c9bb1d45afd1dffa67ed7`.
- **CONFIRMADO:** referência local recuperável criada em `/tmp/lsfg-part2-stable-baseline-20260825.tar.gz`, SHA-256 `d00178e058f14194494f05d973d63be6242b63b45ce4ccf277133810a3fdfc90`. Ela contém os arquivos locais do baseline e o relatório anterior à Parte 2. A remoção de `LSFGDiagnostic.java` faz parte do baseline e está registrada no status.
- **CONFIRMADO:** nenhum commit e nenhum push foram feitos.

Escopo desta rodada: análise estática do source que gerou a layer validada. Nenhum shader, dispatch, barrier, semaphore, present, configuração ou runtime foi alterado.

## Frame generation pipeline map

1. **Entrada e cópia na layer — CONFIRMADO.** `vkQueuePresentKHR` recebe o frame real. A layer espera apenas a fence do ciclo anterior, copia a imagem do swapchain para uma das duas source images alternadas e sinaliza um semaphore binário exportável como `SYNC_FD`.
2. **Pre-pass comum — CONFIRMADO.** O backend importa o `SYNC_FD`, grava mipmaps, sete pares Alpha0/Alpha1 e Beta0/Beta1. Esse trabalho é executado uma vez por par de frames reais, independentemente de 2x/3x/4x.
3. **Passes por frame intermediário — CONFIRMADO.** Cada destino possui recursos e sequência próprios: sete Gamma0/Gamma1, três Delta0/Delta1 e um Generate. O timestamp do destino é fixado no constant buffer como `(índice + 1) / (quantidade + 1)`.
4. **Saída — CONFIRMADO.** Somente a última submissão do backend sinaliza o semaphore exportável. A layer importa o `SYNC_FD`, copia cada destino para uma imagem adquirida do swapchain, apresenta todos os intermediários e depois o frame real.
5. **Persistência — CONFIRMADO.** Pipelines, shaders, imagens temporárias, buffers constantes, descriptor pool/sets, samplers, command buffers e semaphores são criados no contexto da swapchain e reutilizados. Não existe criação de pipeline, descriptor ou imagem por frame no hot path normal.

Contagem estática aproximada de dispatches por frame real, sem interpretar o custo interno de cada shader:

| Modo | Destinos gerados | Pre-pass | Main passes | Total aproximado |
|---|---:|---:|---:|---:|
| 2x | 1 | 34 | 66 | 100 |
| 3x | 2 | 34 | 132 | 166 |
| 4x | 3 | 34 | 198 | 232 |

- **CONFIRMADO:** o número de main passes cresce linearmente com `multiplier - 1`.
- **PROVÁVEL:** em A6xx menor, 3x/4x pressionam principalmente compute, bandwidth e imagens temporárias, não alocação CPU por frame.
- **NÃO CONFIRMADO:** qual shader ou estágio domina o tempo GPU. CPU submit time não responde a essa pergunta.

## Optical flow cost analysis

- **CONFIRMADO:** Mipmaps cria sete níveis R8. Alpha/Beta constroem a representação de movimento comum; Gamma/Delta refinam dados separadamente para cada timestamp intermediário; Generate produz a imagem final em resolução de saída.
- **CONFIRMADO:** Generate sempre despacha sobre a resolução de saída arredondada em grupos de 16 (`ceil(width/16)`, `ceil(height/16)`), portanto seu custo por destino não diminui com `flow_scale`.
- **CONFIRMADO:** Alpha/Gamma/Delta trabalham sobre uma hierarquia derivada de `flowExtent`; há muitas imagens R8/RG/RGBA temporárias e resultados FP16 em partes finais. Isso indica pressão relevante de bandwidth e armazenamento em tile/cache.
- **CONFIRMADO:** Performance Mode seleciona shaders com menos sampled/storage images e reduz a multiplicidade de várias imagens temporárias de 2 para 1. A quantidade de dispatches permanece essencialmente igual, mas cada dispatch pode mover/processar menos canais.
- **PROVÁVEL:** A6xx com menor bandwidth/cache e menor margem térmica é mais sensível ao custo combinado do pre-pass e das 66 etapas adicionais por intermediário.
- **NÃO CONFIRMADO:** saturação real de ALU, bandwidth, cache ou ocupação no Adreno 619. Isso precisa de medição GPU específica e não pode ser inferido de submitted FPS.

## Flow Scale semantics

- **CORREÇÃO CONFIRMADA NA RODADA 2:** a análise inicial desta seção havia parado uma etapa cedo demais. O Winlator escreve o valor sem transformação no TOML, mas a layer passa `1.0 / profile.flow_scale` ao backend. O backend divide a resolução por esse inverso. Portanto o resultado efetivo é aproximadamente `sourceExtent × flow_scale`; valores menores reduzem a dimensão interna.
- **CONFIRMADO:** o constant buffer recebe `1.0 / flow_scale` como `resolutionInvScale`.
- **CONFIRMADO:** Generate continua em resolução de saída e não usa `flowExtent` para definir seu dispatch.

Para entrada 1280x720, depois da conversão float e truncamento:

| Flow Scale | `flowExtent` aproximado | Pixels relativos à entrada |
|---:|---:|---:|
| 0.20 | 256x144 | 0.04x |
| 0.26 | 332x187 | 0.067x |
| 0.35 | 447x251 | 0.122x |
| 0.50 | 640x360 | 0.25x |
| 0.65 | 831x467 | 0.421x |
| 0.80 | 1024x576 | 0.64x |

- **OBSERVAÇÃO DO USUÁRIO / A CONFIRMAR:** `0.20` pareceu elevar o submitted FPS e permitir aproximadamente 100 FPS em 4x.
- **PROVÁVEL:** o ganho é compatível com a redução de pixels do optical-flow path, mas ainda precisa de A/B na mesma cena, multiplier e FPS-base.
- **NÃO CONFIRMADO:** o efeito visual e o ganho real no GPU time em cada valor.

## Low input FPS artifact analysis

- **CONFIRMADO:** 10 FPS deixa aproximadamente 100 ms entre frames reais; 15 FPS, 66,7 ms; 30 FPS, 33,3 ms. Em 4x, três timestamps precisam ser reconstruídos dentro desse intervalo.
- **PROVÁVEL:** intervalos grandes aumentam deslocamento, occlusion/disocclusion e mudança de câmera entre as duas fontes, reduzindo a informação disponível ao optical flow. Isso explica geometric distortion, ghosting e bordas instáveis sem exigir corrupção de memória.
- **PROVÁVEL:** irregularidade do input muda continuamente a distância temporal representada por timestamps uniformes. Mesmo com a razão matemática correta de frames, o movimento percebido pode oscilar.
- **PROVÁVEL:** 4x expõe mais vezes uma estimativa imperfeita e inclui timestamps mais próximos dos extremos, tornando erros visuais mais presentes que em 2x.
- **NÃO CONFIRMADO:** um limiar universal de FPS para A6xx ou para cada multiplier. Conteúdo, câmera, transparências, UI e comportamento por jogo continuam sendo variáveis independentes.

Classificação prática dos sintomas:

- deformação de geometria/bordas e disocclusion: **PROVÁVEL optical flow + distância temporal**;
- UI/HUD deformado: **PROVÁVEL ausência de motion/depth especializado para UI no algoritmo**;
- suavidade irregular sem imagem corrompida: **HIPÓTESE pacing/backpressure**;
- blocos coloridos/flicker severo: **não observado no baseline; tratar como regressão de sync/resource, não como qualidade normal**.

## GPU headroom hypothesis

- **CONFIRMADO:** jogo e backend LSFG submetem trabalho à mesma GPU física. Reduzir gráficos pode aumentar FPS-base e simultaneamente liberar compute/bandwidth.
- **PROVÁVEL:** pouca margem GPU aumenta latência de conclusão, backpressure e irregularidade dos presents, especialmente em 3x/4x.
- **PROVÁVEL:** contenção GPU pode explicar stutter ou tremor temporal. Com sincronização correta, ela não deveria alterar deterministicamente a geometria produzida pelo shader; deformação que acompanha FPS-base baixo continua apontando primeiro para a qualidade temporal/conteúdo.
- **NÃO CONFIRMADO:** que GPU contention causou os artefatos do ETS2. O teste atual mudou FPS, carga e possivelmente conteúdo/pós-processamento ao mesmo tempo.

Experimento controlado proposto, sem limiter na layer: usar o limitador do próprio jogo, se já disponível, para manter a mesma resolução, rota/câmera e aproximadamente 25 FPS em dois presets. Alterar somente uma opção predominantemente GPU (por exemplo, sombras ou escala de render, observando que escala também pode mudar o conteúdo) e repetir captura de vídeo. O teste só sustenta headroom se input FPS/frametime permanecer equivalente. Sem contador GPU confiável e sem controlar conteúdo, o resultado continuará **NÃO CONFIRMADO**.

## 2x / 3x / 4x cost model

### 2x

- **CONFIRMADO:** um destino, aproximadamente 100 dispatches totais e um fluxo completo Gamma/Delta/Generate.
- **PROVÁVEL:** melhor candidato de compatibilidade A6xx porque amortiza o pre-pass com apenas um main pass.

### 3x

- **CONFIRMADO:** dois destinos, aproximadamente 166 dispatches; duplica recursos Gamma/Delta/Generate e cópias/presents de saída, mas não duplica Alpha/Beta.
- **PROVÁVEL:** exige mais headroom e input mais regular; o ganho visual pode continuar bom quando o frame real chega perto de 30 FPS.

### 4x

- **CONFIRMADO:** três destinos, aproximadamente 232 dispatches, três acquires, três cópias intermediárias e quatro presents totais por frame real.
- **PROVÁVEL:** maior pressão de compute, bandwidth, swapchain e backpressure. Não foi encontrada alocação pesada de recursos Vulkan por frame que explique sozinha o custo.
- **NÃO CONFIRMADO:** saturação de semáforos ou command buffers. Eles são reutilizados com fences e estado por imagem; não há evidência de erro no baseline funcional.

## A6xx Vulkan capability strategy

Estado atual:

- **CONFIRMADO:** há detecção de `shaderFloat16`; FP16 só seleciona shaders low-precision quando suportado e permitido.
- **CONFIRMADO:** são exigidos Vulkan 1.2, timeline semaphore e extensões FD de external memory/semaphore. Há validação de export/import para imagens OPAQUE_FD e semáforos SYNC_FD.
- **CONFIRMADO:** MAILBOX só é escolhido quando anunciado pela superfície; FIFO permanece fallback.
- **CONFIRMADO:** a fila backend é escolhida por `VK_QUEUE_COMPUTE_BIT`, sem hardcode de modelo Adreno.
- **PROVÁVEL:** a capability strategy ainda é incompleta: não valida previamente limites de dimensão para o `flowExtent`, features dos formatos temporários, descriptor limits, workgroup limits e orçamento de memória antes de criar todo o contexto.

Estratégia segura futura:

1. Consultar propriedades/features uma vez na criação do backend, nunca por frame.
2. Validar `flowExtent` contra `maxImageDimension2D` e cada formato/usage necessário.
3. Validar FP16, timeline e external handles exatamente como já feito; manter fallback FP32 quando FP16 não existir.
4. Comparar descriptor requirements calculados com os limites do dispositivo antes da criação do pool.
5. Usar subgroup/synchronization2 somente se um caminho futuro realmente os exigir; não ativá-los apenas por serem disponíveis.
6. Basear qualquer fallback em capability/erro explícito, nunca em `Adreno 619` ou nome de jogo.

## Low-risk optimization candidates

Ordem sugerida, ainda sem implementação:

1. **Validação de Flow Scale/dimensões — baixo risco, alta prioridade.** Calcular dimensões e memória aproximada na criação do contexto, rejeitando apenas configurações que excedam capabilities reais. Não muda shaders nem sync.
2. **A/B controlado de `flow_scale` — zero mudança funcional.** Mesma cena, preset, multiplier e Performance Mode; comparar 0.20/0.35/0.50/0.65/0.80. Primeiro confirmar a semântica observada no aparelho.
3. **Instrumentação agregada temporária de pacing — baixo risco se necessária.** Somente contadores monotônicos em memória e uma linha por segundo com input FPS/intervalo, multiplier, flow scale, submitted FPS e resolução; sem DXVK debug, GPU queries, arquivo persistente ou flush por frame. Remover após o teste.
4. **Eliminar pequenos vetores temporários CPU — baixo risco, baixo impacto provável.** Os vetores locais de wait/signal semaphores são reconstruídos por destino. Podem virar armazenamento fixo/reutilizável, mas isso provavelmente não melhora qualidade GPU e deve vir depois das medições.
5. **Regravação de command buffers — risco médio.** Eles são reutilizados como objetos, porém gravados novamente a cada frame. Pré-gravar variantes de paridade poderia reduzir CPU, mas exige provar que descriptors, barriers e semaphores permanecem válidos. Não implementar antes de medir CPU como gargalo.
6. **Auditoria de barriers por recurso — risco alto no baseline atual.** `ManagedShader` insere barrier compute→compute antes de cada dispatch, com masks genéricas. Pode haver redundância, mas remover/agrupar sem uma prova de producer/consumer por imagem ameaça diretamente as correções de glitches. Não recomendado nesta rodada.
7. **Pacing explícito — risco médio/alto.** O código submete e chama `QueuePresentKHR` para os intermediários em sequência; não há distribuição temporal explícita. Isso torna burst/pacing uma hipótese plausível, sobretudo com MAILBOX. Qualquer alteração deve ser estudada separadamente da qualidade óptica e sem sleeps artificiais.

### Plano da próxima rodada

1. Validar no aparelho a curva real de Flow Scale com A/B curto e controlado, começando em 2x.
2. Se a diferença temporal não puder ser observada pelo vídeo/HUD existente, adicionar apenas a métrica agregada mínima descrita acima e removê-la depois.
3. Separar ETS2 em dois testes: input temporal semelhante com carga diferente e carga semelhante com input diferente, classificando como **NÃO CONFIRMADO** quando o controle não for suficiente.
4. Só então escolher uma única mudança pequena: validação por capability ou redução de overhead CPU comprovado.
5. Regressão obrigatória após qualquer mudança: Tomb Raider 2x/3x/4x; depois ETS2 e NFS. Nenhuma mudança automática de multiplier, Flow Scale, MAILBOX ou shader.

## Part 2 Round 2 - Flow Scale validation

### Caminho completo do valor

1. **CONFIRMADO:** o controle Android aceita de 0.20 a 1.00 em passos de 0.01. Os seis valores pedidos já são selecionáveis sem alterar UI.
2. **CONFIRMADO:** `applyLSFGVKConfig()` verifica finitude, aplica clamp 0.20–1.00 e grava duas casas decimais em `conf.toml`.
3. **CONFIRMADO:** o parser TOML lê diretamente `flow_scale` como `float`, valida novamente 0.20–1.00 e não o sobrescreve.
4. **CONFIRMADO:** ao criar a swapchain ativa, a layer chama o backend com `flow = 1.0F / profile.flow_scale`.
5. **CONFIRMADO:** `createCtx()` calcula cada eixo como `static_cast<uint32_t>(float(sourceAxis) / flow)`. Para valores positivos, isso trunca em direção a zero. Combinando as duas etapas, o resultado é aproximadamente `floor(sourceAxis × flow_scale)`, sujeito ao arredondamento FP32.
6. **CONFIRMADO:** o mesmo inverso é gravado em `ConstantBuffer.resolutionInvScale`; não foi encontrada outra inversão, clamp ou substituição.
7. **CONFIRMADO:** `flowExtent` alimenta as imagens de sete níveis, as dimensões Alpha/Beta/Gamma/Delta e seus `vkCmdDispatch`. Generate usa `sourceExtent` e permanece full-resolution.
8. **CONFIRMADO:** os tamanhos dos dispatches são arredondados para cima por divisões inteiras: Mipmaps em blocos lógicos de 64; maior parte dos passes em 8; o último Beta1 em 32; Generate em 16. O local size real está embutido nos SPIR-V proprietários e não foi alterado.
9. **CONFIRMADO:** mudar a configuração por hot reload solicita recriação de uma swapchain/context; não existe polling ou recálculo de Flow Scale por frame.

Diagnóstico mínimo adicionado para esta build A/B: uma única linha `lsfg-vk: context ...` por criação do contexto, contendo resolução, multiplier, `flow_scale`, `flow_extent`, workgroups de Mipmaps e Performance Mode. Não há log por frame, arquivo em Documents, DXVK debug ou loader debug.

## Effective optical flow dimensions

Notação das tabelas por nível:

- `M`: dimensão da imagem R8 do nível, calculada com shift inteiro (`flowExtent >> nível`).
- `Q`: dimensão intermediária equivalente a `ceil(M/4)`.
- `A16`: dispatch de Alpha0 nas duas primeiras etapas, `ceil(M/16)`.
- `A32`: dispatch da terceira Alpha0, Alpha1 e dos Gamma/Delta daquele nível, `ceil(M/32)`.
- Mipmaps faz um único dispatch `ceil(flowExtent/64)` que produz os sete níveis.

### Entrada 1280x720

| Scale | Flow extent | Pixels | Mipmap dispatch |
|---:|---:|---:|---:|
| 0.20 | 256x144 | 36,864 | 4x3 |
| 0.26 | 332x187 | 62,084 | 6x3 |
| 0.35 | 447x251 | 112,197 | 7x4 |
| 0.50 | 640x360 | 230,400 | 10x6 |
| 0.65 | 831x467 | 388,077 | 13x8 |
| 0.80 | 1024x576 | 589,824 | 16x9 |

| Scale | M L0→L6 | Q L0→L6 |
|---:|---|---|
| 0.20 | 256x144; 128x72; 64x36; 32x18; 16x9; 8x4; 4x2 | 64x36; 32x18; 16x9; 8x5; 4x3; 2x1; 1x1 |
| 0.26 | 332x187; 166x93; 83x46; 41x23; 20x11; 10x5; 5x2 | 83x47; 42x24; 21x12; 11x6; 5x3; 3x2; 2x1 |
| 0.35 | 447x251; 223x125; 111x62; 55x31; 27x15; 13x7; 6x3 | 112x63; 56x32; 28x16; 14x8; 7x4; 4x2; 2x1 |
| 0.50 | 640x360; 320x180; 160x90; 80x45; 40x22; 20x11; 10x5 | 160x90; 80x45; 40x23; 20x12; 10x6; 5x3; 3x2 |
| 0.65 | 831x467; 415x233; 207x116; 103x58; 51x29; 25x14; 12x7 | 208x117; 104x59; 52x29; 26x15; 13x8; 7x4; 3x2 |
| 0.80 | 1024x576; 512x288; 256x144; 128x72; 64x36; 32x18; 16x9 | 256x144; 128x72; 64x36; 32x18; 16x9; 8x5; 4x3 |

| Scale | A16 workgroups L0→L6 | A32 workgroups L0→L6 |
|---:|---|---|
| 0.20 | 16x9; 8x5; 4x3; 2x2; 1x1; 1x1; 1x1 | 8x5; 4x3; 2x2; 1x1; 1x1; 1x1; 1x1 |
| 0.26 | 21x12; 11x6; 6x3; 3x2; 2x1; 1x1; 1x1 | 11x6; 6x3; 3x2; 2x1; 1x1; 1x1; 1x1 |
| 0.35 | 28x16; 14x8; 7x4; 4x2; 2x1; 1x1; 1x1 | 14x8; 7x4; 4x2; 2x1; 1x1; 1x1; 1x1 |
| 0.50 | 40x23; 20x12; 10x6; 5x3; 3x2; 2x1; 1x1 | 20x12; 10x6; 5x3; 3x2; 2x1; 1x1; 1x1 |
| 0.65 | 52x30; 26x15; 13x8; 7x4; 4x2; 2x1; 1x1 | 26x15; 13x8; 7x4; 4x2; 2x1; 1x1; 1x1 |
| 0.80 | 64x36; 32x18; 16x9; 8x5; 4x3; 2x2; 1x1 | 32x18; 16x9; 8x5; 4x3; 2x2; 1x1; 1x1 |

### Entrada 960x540

| Scale | Flow extent | Pixels | Mipmap dispatch |
|---:|---:|---:|---:|
| 0.20 | 192x108 | 20,736 | 3x2 |
| 0.26 | 249x140 | 34,860 | 4x3 |
| 0.35 | 335x188 | 62,980 | 6x3 |
| 0.50 | 480x270 | 129,600 | 8x5 |
| 0.65 | 623x350 | 218,050 | 10x6 |
| 0.80 | 768x432 | 331,776 | 12x7 |

| Scale | M L0→L6 | Q L0→L6 |
|---:|---|---|
| 0.20 | 192x108; 96x54; 48x27; 24x13; 12x6; 6x3; 3x1 | 48x27; 24x14; 12x7; 6x4; 3x2; 2x1; 1x1 |
| 0.26 | 249x140; 124x70; 62x35; 31x17; 15x8; 7x4; 3x2 | 63x35; 31x18; 16x9; 8x5; 4x2; 2x1; 1x1 |
| 0.35 | 335x188; 167x94; 83x47; 41x23; 20x11; 10x5; 5x2 | 84x47; 42x24; 21x12; 11x6; 5x3; 3x2; 2x1 |
| 0.50 | 480x270; 240x135; 120x67; 60x33; 30x16; 15x8; 7x4 | 120x68; 60x34; 30x17; 15x9; 8x4; 4x2; 2x1 |
| 0.65 | 623x350; 311x175; 155x87; 77x43; 38x21; 19x10; 9x5 | 156x88; 78x44; 39x22; 20x11; 10x6; 5x3; 3x2 |
| 0.80 | 768x432; 384x216; 192x108; 96x54; 48x27; 24x13; 12x6 | 192x108; 96x54; 48x27; 24x14; 12x7; 6x4; 3x2 |

| Scale | A16 workgroups L0→L6 | A32 workgroups L0→L6 |
|---:|---|---|
| 0.20 | 12x7; 6x4; 3x2; 2x1; 1x1; 1x1; 1x1 | 6x4; 3x2; 2x1; 1x1; 1x1; 1x1; 1x1 |
| 0.26 | 16x9; 8x5; 4x3; 2x2; 1x1; 1x1; 1x1 | 8x5; 4x3; 2x2; 1x1; 1x1; 1x1; 1x1 |
| 0.35 | 21x12; 11x6; 6x3; 3x2; 2x1; 1x1; 1x1 | 11x6; 6x3; 3x2; 2x1; 1x1; 1x1; 1x1 |
| 0.50 | 30x17; 15x9; 8x5; 4x3; 2x1; 1x1; 1x1 | 15x9; 8x5; 4x3; 2x2; 1x1; 1x1; 1x1 |
| 0.65 | 39x22; 20x11; 10x6; 5x3; 3x2; 2x1; 1x1 | 20x11; 10x6; 5x3; 3x2; 2x1; 1x1; 1x1 |
| 0.80 | 48x27; 24x14; 12x7; 6x4; 3x2; 2x1; 1x1 | 24x14; 12x7; 6x4; 3x2; 2x1; 1x1; 1x1 |

- **CONFIRMADO:** de 0.20 para 0.80, os pixels de `flowExtent` crescem 16 vezes. Isso oferece uma explicação técnica direta para menor custo em 0.20.
- **CONFIRMADO:** nenhum valor testado produz dimensão maior que a entrada; a hipótese anterior de 6400x3600 estava errada porque não incluiu a inversão feita na layer.
- **PROVÁVEL:** escalas baixas reduzem compute, bandwidth e memória temporária, mas também removem detalhe espacial do campo de movimento.
- **HIPÓTESE:** dimensões próximas mas em lados diferentes de um limite de 8/32/64 podem ter custo discretamente diferente devido ao workgroup extra. Isso não demonstra diferença relevante de occupancy sem perfil GPU.

## Dispatch cost breakdown

| Categoria | Dispatches comuns | Por intermediário | Depende de Flow Scale? |
|---|---:|---:|---|
| Mipmaps/preprocess | 1 | 0 | Sim; dispatch `ceil(flowExtent/64)` |
| Alpha0 | 21 | 0 | Sim; 3 por cada um dos 7 níveis |
| Alpha1 | 7 | 0 | Sim; 1 por nível |
| Beta0 | 1 | 0 | Sim; nível base quarter-resolution |
| Beta1 | 4 | 0 | Sim; três em `ceil(M0/32)` e um em `ceil(M0/128)` |
| Gamma0 | 0 | 7 | Sim; 1 por nível |
| Gamma1 | 0 | 28 | Sim; 4 por nível |
| Delta0 | 0 | 6 | Sim; 2 nos níveis 2, 1 e 0 |
| Delta1 | 0 | 24 | Sim; 8 nos níveis 2, 1 e 0 |
| Generate | 0 | 1 | Não; `ceil(source/16)` |
| **Total** | **34** | **66** | — |

- **CONFIRMADO:** 2x = 34 + 66 = 100; 3x = 34 + 132 = 166; 4x = 34 + 198 = 232.
- **CONFIRMADO:** Flow Scale altera o tamanho dos dispatches de todos os 33 passes comuns depois de Mipmaps e de 65 dos 66 passes por intermediário. Ele não altera a quantidade de dispatches.
- **CONFIRMADO:** fora de compute há 1 blit real→source por frame real, `multiplier-1` blits destination→swapchain, `multiplier-1` acquires adicionais e `multiplier` presents.
- **CONFIRMADO:** Performance Mode reduz recursos/canais de vários shaders, mas não a contagem acima.
- **NÃO CONFIRMADO:** custo percentual de cada categoria na GPU. Só timestamps GPU por blocos poderiam medir isso, e eles não foram adicionados nesta rodada.

## Temporal input model

Os offsets são medidos depois do frame real A e antes do frame real B. Eles não representam garantia de exibição física nesse instante; são os timestamps usados para interpolação.

| FPS-base | Intervalo A→B | 2x: intermediário | 3x: intermediários | 4x: intermediários |
|---:|---:|---:|---:|---:|
| 10 | 100.0 ms | 50.0 | 33.3, 66.7 | 25.0, 50.0, 75.0 |
| 15 | 66.7 ms | 33.3 | 22.2, 44.4 | 16.7, 33.3, 50.0 |
| 20 | 50.0 ms | 25.0 | 16.7, 33.3 | 12.5, 25.0, 37.5 |
| 25 | 40.0 ms | 20.0 | 13.3, 26.7 | 10.0, 20.0, 30.0 |
| 30 | 33.3 ms | 16.7 | 11.1, 22.2 | 8.3, 16.7, 25.0 |
| 35 | 28.6 ms | 14.3 | 9.5, 19.0 | 7.1, 14.3, 21.4 |

- **CONFIRMADO:** o algoritmo usa dois frames reais e timestamps uniformemente espaçados; multiplier maior não cria informação temporal adicional.
- **PROVÁVEL:** em 8–17 FPS, câmera e objetos percorrem distâncias maiores entre A/B. Optical flow precisa inferir trajetórias maiores com mais áreas ocultadas/reveladas.
- **PROVÁVEL:** edge distortion e disocclusion crescem porque pixels presentes em apenas um dos frames não têm correspondência confiável.
- **PROVÁVEL:** 4x mostra três versões da mesma estimativa, tornando o erro mais frequente e visível; isso não significa que o scheduling esteja matematicamente errado.
- **NÃO CONFIRMADO:** que irregularidade específica do ETS2, e não somente o intervalo médio, domina o resultado.

## GPU headroom separation strategy

- **CONFIRMADO:** reduzir o preset do ETS2 alterou simultaneamente FPS-base, carga GPU e conteúdo renderizado. O teste anterior não isola causalidade.
- **PROVÁVEL:** baixo headroom produz atraso, backpressure, frames descartados no MAILBOX ou apresentação em burst; isso aparece mais como stutter/tremor temporal do que como geometria deterministicamente deformada.
- **PROVÁVEL:** FPS-base baixo piora diretamente a informação disponível ao optical flow, afetando ghosting, bordas e disocclusion mesmo quando existe headroom.

Metodologia prática, sem limiter na layer:

1. Fixar 2x, resolução, Flow Scale, Performance Mode, rota, câmera e clima.
2. Usar o limitador do próprio ETS2 apenas se ele já estiver disponível e puder manter aproximadamente 25 FPS nos dois cenários.
3. Cenário A: elevar uma opção predominantemente GPU até ficar próximo do limite de 25 FPS, sem mudar resolução interna.
4. Cenário B: reduzir essa mesma opção, mantendo o mesmo cap de 25 FPS.
5. Fazer três passagens idênticas e gravar vídeo externo/ADB apenas se isso não alterar materialmente a carga.
6. Se A tiver mais stutter mas deformações equivalentes, favorece contention/pacing. Se deformações mudarem com input realmente equivalente, headroom ou conteúdo do efeito permanece candidato.

**NÃO CONFIRMADO:** esse teste consegue controlar perfeitamente a GPU no Moto G34. Sombras, reflexos e vegetação também alteram o conteúdo entregue ao optical flow. Sem GPU counters confiáveis, a conclusão deve continuar limitada.

## A6xx Flow Scale implications

- **CONFIRMADO:** valores maiores elevam quadraticamente a quantidade aproximada de pixels do flow path; dobrar Scale tende a quadruplicar pixels, embora workgroups e níveis introduzam degraus.
- **CONFIRMADO:** valores menores reduzem custo potencial, mas Generate full-resolution e as cópias permanecem fixos; o ganho total não escala na mesma proporção.
- **PROVÁVEL:** A6xx com menos bandwidth/cache ganha mais margem com Scale baixo, especialmente em 3x/4x, mas pode perder precisão em movimento fino, bordas e objetos pequenos.
- **PROVÁVEL:** Scale alto pode aumentar register pressure indireta, cache misses e tráfego de storage images; o source não permite quantificar occupancy sem ferramentas do driver.
- **CONFIRMADO:** FP16 já é capability-gated. Dimensões máximas, storage format features, descriptor limits e memória ainda devem ser detectados por capability se futuramente forem usados para validar configurações.
- **NÃO CONFIRMADO:** um ponto ideal comum a toda família A6xx. Não há base para preset por nome de GPU.
- **HIPÓTESE:** 0.20–0.35 pode favorecer desempenho em A6xx fraca, 0.50–0.65 equilíbrio e 0.80 detalhe; essas faixas são apenas roteiro de teste, não recomendação final.

## Artifact classification

| Sintoma | Causa mais provável | Classificação |
|---|---|---|
| Erro de movimento | Vetor de optical flow incorreto por deslocamento grande/ambíguo | **PROVÁVEL** |
| Ghosting | Correspondência temporal incorreta, transparência ou mistura de A/B | **PROVÁVEL** |
| Tremor geométrico | Vetor muda entre pares reais; input irregular agrava | **PROVÁVEL** |
| Tremor de cadência | Burst, backpressure, MAILBOX/drop ou GPU saturation | **HIPÓTESE** |
| Edge distortion | Campo de movimento com baixa resolução ou grande deslocamento | **PROVÁVEL** |
| Disocclusion | Região existe em apenas A ou B e precisa ser reconstruída | **PROVÁVEL limitação temporal/algorítmica** |
| UI/HUD distortion | UI é tratada como conteúdo móvel sem máscara especializada | **PROVÁVEL optical flow** |
| Stutter/pacing | Intervalos reais irregulares, GPU contention ou presents agrupados | **PROVÁVEL multifatorial** |
| Flicker/blocos coloridos | Hazard de layout, sync, lifetime ou imagem reutilizada | **NÃO observado no baseline; regressão se reaparecer** |

GPU saturation pode amplificar tremor de cadência e stutter, mas não é explicação suficiente para ghosting ou deformação estável. Sincronização só volta a ser suspeita se houver corrupção, conteúdo de frame errado ou hazards explícitos.

## Optimization risk ranking

### BAIXO RISCO

1. Remover alocações CPU dos pequenos vetores wait/signal no hot path usando armazenamento fixo, preservando exatamente ordem e handles.
2. Validar dimensões e formatos uma vez por contexto com capabilities reais; melhora falha segura, não qualidade direta.
3. A/B de Flow Scale pela UI existente; nenhuma mudança funcional.
4. Manter somente o log de criação de contexto desta build de teste e removê-lo após a validação.

### MÉDIO RISCO

1. Pré-gravar command buffers de variantes pares/ímpares; descriptors são estáticos, mas barriers e semaphore submit precisam continuar corretos.
2. Reduzir cópias/resolve entre backend e swapchain; envolve external image layouts e sincronização crítica.
3. Alterar pacing/scheduling de presents; pode melhorar cadência, mas interage com MAILBOX e backpressure.
4. Ajustar automaticamente flow resolution por capability/carga; muda qualidade e política do usuário.

### ALTO RISCO

1. Remover/agrupar barriers sem producer/consumer proof por recurso.
2. Alterar semáforos, fences, external handles ou lifetime das imagens.
3. Eliminar dispatches considerados redundantes sem conhecer contratos dos shaders proprietários.
4. Alterar shaders optical-flow/Generate, workgroup local size ou SPIR-V.
5. Mudar 2x/3x/4x scheduling ou política MAILBOX.

## Recommended first optimization

**Candidata escolhida para a próxima rodada: substituir os vetores temporários de wait/signal semaphores no caminho de cópia por armazenamento fixo, mantendo as mesmas contagens, handles e ordem.**

- **CONFIRMADO:** hoje são construídos dois `std::vector<VkSemaphore>` para cada destino gerado em cada frame real.
- **PROVÁVEL:** remover essas pequenas alocações reduz overhead e variabilidade CPU, com benefício geral e reversão simples.
- **NÃO CONFIRMADO:** benefício mensurável no Adreno 619; não deve ser vendido como solução para artefatos ou GPU saturation.
- Segurança: não toca shader, dispatch, barrier, semaphore lifetime, MAILBOX ou timestamps; o A/B deve comparar somente CPU/pacing percebido e regressão funcional.

Antes dessa otimização, executar o A/B de Flow Scale em 2x com esta build: 0.20, 0.26, 0.35, 0.50, 0.65 e 0.80; mesma cena, preset, Performance Mode e duração. Registrar qualidade de bordas/câmera/UI, FPS-base e submitted FPS. Parar o teste se reaparecer flicker, blocos ou crash.

## Part 2 Round 3 - Implemented optimization

### Resultado do teste informado

- **CONFIRMADO:** no ETS2, aproximadamente 12–19 FPS reais produzem cerca de 30 FPS em 2x, 40–45 em 3x e 50–55 em 4x. A multiplicação está operacional.
- **CONFIRMADO:** a deformação/tremor aparece durante movimento de câmera já em 2x e cresce em 3x/4x.
- **CONFIRMADO:** reduzir Flow Scale de 0.80 para 0.20 trouxe apenas aproximadamente 1–2% de melhora visual. Flow Scale controla custo/resolução espacial, mas não resolve a grande separação temporal dos frames reais.

### Única mudança funcional desta rodada

Foi criado `CommandBuffer::submitBinary()`, um caminho limitado a no máximo dois semáforos binários de espera e dois de sinalização, usando `std::array`/`std::span`. Somente a submissão de cópia destination→swapchain da layer foi migrada.

- **CONFIRMADO:** a lista de waits permanece, na mesma ordem: semaphore de acquire; no primeiro destino, output-ready do backend.
- **CONFIRMADO:** a lista de signals permanece, na mesma ordem: generated-ready da imagem; no último destino, original-ready.
- **CONFIRMADO:** stage mask continua `VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT`, valores timeline continuam zero para semáforos binários e a fence continua somente na última cópia.
- **CONFIRMADO:** nenhuma chamada de present, acquire, barrier, shader, descriptor, external handle ou lifetime foi alterada.
- **PROVÁVEL:** são eliminadas múltiplas alocações/cópias heap por intermediário, reduzindo overhead e variabilidade CPU no hot path.
- **NÃO CONFIRMADO:** ganho mensurável de FPS ou redução visível de tremor no Adreno 619. A mudança não pretende corrigir ghosting óptico.

Arquivos funcionais afetados no source reconstruído:

- `lsfg-vk-common/include/lsfg-vk-common/vulkan/command_buffer.hpp`;
- `lsfg-vk-common/src/vulkan/command_buffer.cpp`;
- `lsfg-vk-layer/src/swapchain.cpp`.

No projeto, essas alterações são reproduzidas por `tools/lsfg-vk-glibc/compatibility.patch` e pela nova biblioteca em assets.

## Root cause evidence

### Causa mais bem sustentada para deformações

- **CONFIRMADO:** 12–19 FPS correspondem a intervalos reais de aproximadamente 83–53 ms.
- **CONFIRMADO:** os shaders recebem somente os dois frames reais alternados e um timestamp fracionário; multiplier maior não fornece amostra real adicional.
- **PROVÁVEL:** a principal causa de ghosting, edge distortion e disocclusion no ETS2 é a baixa qualidade temporal da entrada: deslocamento grande de câmera/objetos e regiões sem correspondência entre A e B.
- **CONFIRMADO:** reduzir Flow Scale não diminui o intervalo temporal. Uma melhoria visual mínima de 1–2% é consistente com essa separação de responsabilidades.

### Causa de tremor/microstutter

- **CONFIRMADO:** o código não associa desired-present-time aos intermediários e não distribui temporalmente as chamadas `QueuePresentKHR`.
- **PROVÁVEL:** rajada de presents, MAILBOX dropping/backpressure e contention GPU podem agravar tremor de cadência além do erro óptico inevitável.
- **NÃO CONFIRMADO:** quanto do tremor percebido é pacing em vez de campo de movimento incorreto. Submitted FPS não mede cadence física.

Não foi implementada uma correção de pacing porque sleep/limiter foram proibidos e a implementação atual não negocia uma extensão confiável de display timing. Introduzir política temporal nova seria risco médio/alto para o baseline.

## Pacing analysis

### Frações de interpolação

`getDefaultConstantBuffer(index, total, ...)` grava `timestamp = (index + 1) / (total + 1)`. `total` é exatamente a quantidade de destination images (`multiplier - 1`), e cada Generate usa o constant buffer do mesmo índice do destino.

| Modo | Destinos | Frações que chegam ao Generate | Estado |
|---|---:|---|---|
| 2x | 1 | 1/2 = 0.5 | **CONFIRMADO** |
| 3x | 2 | 1/3, 2/3 | **CONFIRMADO** |
| 4x | 3 | 1/4, 2/4, 3/4 | **CONFIRMADO** |

### Ordem GPU e presents

1. A layer copia o frame real para a source image e sinaliza source-ready.
2. O backend submete o pre-pass.
3. O backend submete todos os main passes em ordem na mesma queue.
4. Somente o último main pass sinaliza output-ready.
5. Portanto a layer só começa a copiar/apresentar depois que todos os intermediários estão prontos.
6. A layer itera `i=0..N-1`, faz acquire, submete a cópia e chama `QueuePresentKHR` para cada intermediário.
7. Depois apresenta o frame real.

- **CONFIRMADO:** ordem lógica = frações crescentes e depois frame real.
- **CONFIRMADO:** Generate não é intercalado com present; todos os Generates são enfileirados antes do primeiro present intermediário.
- **PROVÁVEL:** as chamadas de present formam uma rajada CPU curta. A conclusão das cópias ainda depende da GPU/WSI, então o espaçamento físico não pode ser inferido apenas da sequência de chamadas.
- **HIPÓTESE:** sinalizar cada destino separadamente poderia permitir pipeline mais cedo, mas alteraria semáforos e scheduling crítico; não é baixo risco e não foi feito.

## Temporary allocation audit

### Por contexto/swapchain — fora do hot path

- **CONFIRMADO:** source/destination/temporary images, memory, pipelines, shader modules, descriptor pool/sets, constant buffers, samplers, command buffers, fences e semáforos são criados uma vez por contexto.
- **CONFIRMADO:** hot reload que muda profile recria uma swapchain/context; não recria esses objetos por frame.

### Por frame real

- **CONFIRMADO:** command buffers existentes são resetados/regravados; não são realocados.
- **CONFIRMADO:** descriptors são reutilizados e não recebem `vkUpdateDescriptorSets` por frame.
- **CONFIRMADO:** não há `new/delete`, criação de imagem, buffer, semaphore ou pipeline no caminho normal de present.
- **CONFIRMADO:** export/import de SYNC_FD ocorre por frame e faz parte da sincronização glibc estável; não foi tocado.
- **CONFIRMADO:** o submit genérico ainda cria vetores auxiliares de values/stages em outros pontos. Eles não foram alterados para limitar o escopo desta rodada.

### Por frame gerado — antes da mudança

- **CONFIRMADO:** a layer construía `waitSemaphores` e `signalSemaphores` locais como `std::vector`.
- **CONFIRMADO:** `CommandBuffer::submit()` recebia ambos por valor e criava ainda vetores de wait values, signal values e stage masks.
- **PROVÁVEL:** dependendo da implementação STL/capacity e do segundo semaphore, isso causava várias alocações e cópias heap para cada intermediário: uma vez em 2x, duas em 3x e três em 4x por frame real.

### Depois da mudança

- **CONFIRMADO:** o caminho migrado usa cinco arrays pequenos na stack: waits, signals, wait values, signal values e stages.
- **CONFIRMADO:** não há heap allocation dentro de `submitBinary()`.
- **CONFIRMADO:** o limite de dois é validado antes do submit; os únicos callers atuais passam uma ou duas entradas.

## A6xx benefit rationale

- **PROVÁVEL:** reduzir trabalho CPU e variação de alocador diminui pequenos gaps entre submits/presents, especialmente em 3x/4x.
- **PROVÁVEL:** o benefício é geral e não depende do nome Adreno; A6xx mais fraca pode se beneficiar quando CPU/driver overhead acompanha forte carga GPU.
- **CONFIRMADO:** a mudança não reduz os 100/166/232 dispatches, bandwidth de storage images ou custo do Generate.
- **NÃO CONFIRMADO:** melhora de qualidade geométrica. Essa qualidade continua dominada por input temporal e optical flow.
- **HIPÓTESE:** cadence CPU ligeiramente mais consistente pode reduzir uma pequena parte do microstutter, mas não corrige o burst estrutural de presents.

## Risk assessment

- Risco geral: **BAIXO**.
- **CONFIRMADO:** handles, contagens, ordem, stage masks, timeline values, fence e queue são equivalentes ao caminho anterior.
- **CONFIRMADO:** arrays permanecem vivos até `QueueSubmit` retornar; Vulkan consome as estruturas durante a chamada.
- **CONFIRMADO:** nenhum semaphore foi criado, destruído ou reutilizado de maneira diferente.
- **PROVÁVEL:** maior risco é uma divergência futura se algum caller tentar passar mais de dois semáforos; a função falha explicitamente e hoje só é usada no caminho comprovadamente limitado.
- Reversão: remover `submitBinary()` e restaurar a chamada anterior com vetores.

## Regression checklist

- **CONFIRMADO estaticamente:** 2x mantém uma destination image e uma fração 0.5.
- **CONFIRMADO estaticamente:** 3x mantém duas destination images e frações 1/3, 2/3.
- **CONFIRMADO estaticamente:** 4x mantém três destination images e frações 1/4, 2/4, 3/4.
- **CONFIRMADO estaticamente:** MAILBOX/FIFO fallback não foi modificado.
- **CONFIRMADO estaticamente:** barriers, image states, acquire/reuse checks, binary SYNC_FD, external memory e fences permanecem.
- **CONFIRMADO estaticamente:** Lossless.dll e recursos/shaders proprietários não foram alterados.
- **PENDENTE NO APARELHO:** Tomb Raider abre rápido e completa menu/cutscene.
- **PENDENTE NO APARELHO:** Tomb Raider 2x/3x/4x sem flicker, blocos ou crash.
- **PENDENTE NO APARELHO:** ETS2 compara tremor/pacing com a mesma configuração da baseline.
- **PENDENTE NO APARELHO:** NFS não apresenta regressão.

## Part 2 Round 4 - Low FPS quality

### Evidência do aparelho

- **CONFIRMADO:** Tomb Raider preservou inicialização rápida, ausência de corrupção e funcionamento de 2x/3x/4x. Em regiões favoráveis, 4x atingiu aproximadamente 90–100 submitted FPS; em regiões pesadas, 67–75.
- **CONFIRMADO:** ETS2 em configuração pesada, perto de 20 FPS reais, continua com forte deformação/tremor que cresce em 3x/4x.
- **CONFIRMADO:** ao reduzir gráficos, FPS-base e estabilidade melhoram, os artefatos fortes quase desaparecem e 4x chega perto de 100 submitted FPS.
- **CONFIRMADO:** permanece tremor muito leve durante movimento de câmera mesmo no regime melhor.
- **PROVÁVEL:** existem dois componentes: erro óptico dominando o regime de baixo FPS e cadence/display timing contribuindo para o resíduo.
- **NÃO CONFIRMADO:** participação quantitativa de GPU contention em cada componente.

### Modelo temporal revisado

- **CONFIRMADO:** 2x usa 0.5; 3x usa 1/3 e 2/3; 4x usa 1/4, 2/4 e 3/4 por meio de `(index + 1) / (total + 1)`.
- **CONFIRMADO:** a fração chega ao constant buffer específico do Generate do mesmo destination index.
- **CONFIRMADO:** generated frames nunca são copiados de volta para as duas source images. `sourceImages[fidx % 2]` recebe somente o próximo frame real do swapchain.
- **CONFIRMADO:** não há seleção de source stale além do par alternado anterior/atual, nem feedback de frame interpolado.

Em 20 FPS, A→B dura aproximadamente 50 ms e as posições ideais seriam 25 ms em 2x; 16,7/33,3 em 3x; 12,5/25/37,5 em 4x. Porém B só está disponível quando esses instantes ideais entre A e B já passaram.

- **CONFIRMADO:** interpolation position descreve conteúdo entre A/B; não é um deadline WSI.
- **PROVÁVEL:** para exibir essas posições uniformemente após B chegar seria necessário introduzir aproximadamente um frame real de atraso ou uma política preditiva. Isso aumenta latência e muda scheduling, portanto não é uma correção pequena.

## Residual camera jitter

- **PROVÁVEL:** movimento de câmera apenas torna divergências temporais e vetores ópticos mais visíveis; não há evidência de falha específica de touch/mouse no caminho LSFG.
- **PROVÁVEL:** com FPS-base saudável, o campo óptico melhora, deixando mais perceptível a irregularidade entre presents efetivamente escolhidos pelo WSI/display.
- **HIPÓTESE:** alguns intermediários podem ser substituídos no MAILBOX enquanto outros chegam a vblanks diferentes, produzindo passos angulares desiguais.
- **HIPÓTESE:** contention entre render gráfico e dezenas/centenas de compute dispatches desloca completion times mesmo quando o FPS médio parece bom.
- **NÃO CONFIRMADO:** o tremor residual é exclusivamente pacing. Erro óptico fino em câmera, bordas e objetos pequenos continua possível.

## Interpolation position vs display timing

O backend primeiro enfileira pre-pass e todos os main passes. Só o último main pass sinaliza `outputReadySemaphore`. A layer então enfileira as cópias/presents dos destinos em ordem e finalmente o frame real.

- **CONFIRMADO:** ordem de conteúdo: intermediários crescentes, depois real.
- **CONFIRMADO:** todos os intermediários já foram produzidos antes de a layer poder iniciar o primeiro present.
- **CONFIRMADO:** não existe timestamp alvo em `VkPresentInfoKHR`, relógio de refresh ou deadline por fraction.
- **CONFIRMADO:** chamadas próximas de `QueuePresentKHR` não garantem scanout próximo nem uniforme; os semáforos protegem disponibilidade, não cadence.
- **PROVÁVEL:** submitted FPS mede chamadas/produção, não frames fisicamente exibidos.

O backend não mantém timestamps TA/TB confiáveis após a remoção da telemetria. Mesmo que medisse os arrivals CPU, os deadlines `TA + fraction × (TB-TA)` já estariam no passado quando B se tornasse conhecido. Uma política real precisaria definir atraso, previsão e comportamento em frametime irregular.

## Present burst/cadence

- **CONFIRMADO:** o código não executa `Generate→present→Generate→present`. Ele executa todos os Generates primeiro e só então as cópias/presents em loop.
- **CONFIRMADO:** no lado CPU, os `QueuePresentKHR` intermediários são chamados sequencialmente sem espera temporal explícita.
- **PROVÁVEL:** isso constitui burst de submissão de present. GPU queue order, semáforos, acquire e WSI podem espaçar completion, mas não segundo as fractions ideais.
- **NÃO CONFIRMADO:** distribuição física exata em Turnip 24.1.0. Não foi adicionada telemetria por frame nem GPU timestamp.

Alterar para sinalização/FD individual por destino poderia liberar cada cópia mais cedo, mas mudaria external semaphore lifetime e scheduling que eliminaram corrupção. Classificação: **ALTO RISCO nesta baseline**.

## MAILBOX and refresh interaction

- **CONFIRMADO conceitualmente:** MAILBOX mantém uma imagem pronta mais recente; uma imagem pendente ainda não exibida pode ser substituída por outra mais nova.
- **PROVÁVEL:** 100 submitted FPS em 90 Hz exige descartar/substituir aproximadamente 10 frames por segundo em média; em 60 Hz, aproximadamente 40. A fase entre bursts e vblank determina quais fractions sobrevivem.
- **PROVÁVEL:** descartes não uniformes podem transformar passos ideais de câmera em sequência irregular e produzir tremor leve.
- **CONFIRMADO:** a política atual só escolhe MAILBOX quando a superfície anuncia suporte; FIFO permanece fallback.
- **CONFIRMADO:** esta rodada não remove nem força outro present mode.
- **NÃO CONFIRMADO:** taxa real do painel durante cada teste e quantos intermediários foram efetivamente exibidos.

Em FIFO, backpressure pode espaçar mais os presents, mas também aumentar fila/latência. Em IMMEDIATE, tearing e cadence dependem ainda mais do compositor/display. Trocar modo sem A/B controlado misturaria pacing, latência e frame dropping.

### Extensões de present auditadas

- **CONFIRMADO:** os headers contêm `VK_KHR_present_id`, `VK_KHR_present_wait` e `VK_GOOGLE_display_timing`.
- **CONFIRMADO:** a layer atual não negocia essas extensões, não habilita suas features, não carrega `vkWaitForPresentKHR`/funções GOOGLE e não cria `VkPresentIdKHR`/`VkPresentTimesInfoGOOGLE`.
- **CONFIRMADO:** `present_id` identifica um present; sozinho não define horário de exibição.
- **PROVÁVEL:** `present_wait` introduziria espera/latência e exigiria IDs, suporte de feature e interação cuidadosa com MAILBOX.
- **HIPÓTESE:** `VK_GOOGLE_display_timing` seria o mecanismo mais próximo de desired-present-time, se anunciado e corretamente suportado pelo stack, mas exigiria clock model, pNext composition e política de atraso.
- **NÃO CONFIRMADO:** suporte efetivo dessas extensões no Turnip/DXVK/container testado. Presença no header não prova suporte runtime.

Conclusão: não há base suficiente para habilitar qualquer extensão nesta rodada sem capability detection e um desenho explícito de latência.

## A6xx pacing implications

- **PROVÁVEL:** A6xx menor compartilha bandwidth/compute com o jogo; main passes longos ou variáveis alteram quando o burst fica disponível.
- **PROVÁVEL:** 4x agrava pressão com três grupos Gamma/Delta/Generate e três cópias de saída, além de exceder mais facilmente 60/90 Hz.
- **CONFIRMADO:** reduzir Flow Scale diminui o flow path, mas Generate e cópias full-resolution continuam; por isso não elimina cadence residual.
- **NÃO CONFIRMADO:** se queue compute dedicada existe fisicamente. Uma queue family com flag compute não garante engine independente.
- **NÃO CONFIRMADO:** limite de bandwidth, occupancy ou cache específico de cada A6xx; não há hardcode por GPU.

## Low-FPS optical-flow limitations

- **CONFIRMADO:** os inputs são sempre dois frames reais consecutivos; não há duplicate source, generated feedback ou frame skipping criado pela layer.
- **PROVÁVEL:** em ~20 FPS, 50 ms permitem grande rotação de câmera e disocclusion; em 12 FPS, ~83 ms agravam fortemente a falta de correspondência.
- **PROVÁVEL:** multiplier maior não piora o vetor-base necessariamente, mas exibe mais amostras derivadas dele e aumenta custo/contenda.
- **PROVÁVEL:** o desaparecimento dos artefatos fortes com FPS-base melhor sustenta limitação temporal como causa primária das deformações.
- **NÃO CONFIRMADO:** melhoria robusta sem alterar shaders. History extrapolation, depth/motion vectors ou masks exigiriam informação que o pipeline atual não recebe.

## Implemented improvement

Pacing explícito não foi modificado. A única melhoria funcional da Rodada 4 elimina as alocações heap restantes na submissão de cada main pass do backend:

- novo `CommandBuffer::submitTimelineWait()` usa arrays fixos para exatamente um wait timeline e zero/um signal binário;
- cada main pass mantém o mesmo `prepassSemaphore`, valor `idx-1`, stage TOP_OF_PIPE, output-ready apenas no último pass e fence apenas no último pass;
- nenhum dispatch, barrier, command buffer, shader, present ou external FD mudou.

Classificação:

- **CONFIRMADO:** o vetor `signalSemaphores` e os vetores internos de semaphore values/stages deixaram de ser alocados no main-pass hot path.
- **PROVÁVEL:** menor overhead/variabilidade CPU antes dos submits beneficia 2x/3x/4x em qualquer GPU/driver, inclusive A6xx.
- **HIPÓTESE:** pode reduzir pequena parcela de microstutter de submit; não corrige o burst estrutural nem deformação óptica.
- **NÃO CONFIRMADO:** ganho perceptível no aparelho.
- Risco: **BAIXO**, pois a estrutura `VkTimelineSemaphoreSubmitInfo`, contagens, valores, handles, stage e fence são equivalentes e os arrays vivem até `QueueSubmit` retornar.

## Regression checklist

- **CONFIRMADO estaticamente:** fractions 2x/3x/4x inalteradas.
- **CONFIRMADO estaticamente:** generated frames não alimentam o próximo optical flow.
- **CONFIRMADO estaticamente:** ordem intermediários→real inalterada.
- **CONFIRMADO estaticamente:** MAILBOX/FIFO fallback inalterado.
- **CONFIRMADO estaticamente:** fences, timeline semaphore, SYNC_FD, external images, image reuse e barriers preservados.
- **CONFIRMADO estaticamente:** shaders/Lossless.dll, Wine, Box64, DXVK, Turnip e rootfs não foram alterados.
- **PENDENTE NO APARELHO:** Tomb Raider abre rápido e mantém 2x/3x/4x sem corrupção/crash.
- **PENDENTE NO APARELHO:** ETS2 alto mantém multiplicação e compara artefatos/tremor.
- **PENDENTE NO APARELHO:** ETS2 baixo compara especificamente o tremor residual em câmera.
- **PENDENTE NO APARELHO:** NFS não piora.

## Part 4 Round 4 - uiThreshold 0.45 Experiment

## Baseline

- **CONFIRMADO:** o baseline do host preenchia `ConstantBuffer::uiThreshold` com `0.50F`.
- **CONFIRMADO:** a Rodada 3 demonstrou que somente os quatro shaders Beta finais leem esse campo no offset 32 do mesmo UBO; Gamma, Delta e Generate não o leem diretamente.
- **CONFIRMADO:** nenhuma DLL, módulo SPIR-V ou disassembly local foi incorporado ao source, asset ou APK.

## Functional change

- **ÚNICA variável funcional alterada:** `uiThreshold = 0.50F` para `uiThreshold = 0.45F` em `lsfg-vk-backend/src/helpers/utils.cpp`.
- A alteração reproduzível foi registrada em `tools/lsfg-vk-glibc/compatibility.patch`; o patch foi reaplicado com sucesso sobre um checkout limpo do source upstream e a biblioteca AArch64 foi reconstruída do zero.
- **CONFIRMADO:** UBO layout, offsets, bindings, shaders, Performance Mode, Flow Scale, fractions, scheduling, sincronização, MAILBOX/FIFO e resource lifetime não foram alterados nesta rodada.

## Shader-path propagation

- **CONFIRMADO:** `getDefaultConstantBuffer()` continua preenchendo o mesmo campo `float` no offset 32; somente seu literal mudou para `0.45F`.
- **CONFIRMADO:** buffers globais e buffers por destination recebem a estrutura pelo mesmo caminho anterior.
- **CONFIRMADO:** o valor alcança as mesmas variantes Beta finais Quality/Performance e FP16/FP32 (resources 328, 351, 377 e 400), sem mudança de descriptor ou packing.

## Expected mask behavior

- **CONFIRMADO pela inspeção SPIR-V anterior:** Beta calcula aproximadamente `step(uiThreshold, sigmoid(score)) * sigmoid(score)` e produz a máscara/pirâmide R8 usada para atenuar motion em Gamma/Delta.
- **CONFIRMADO:** reduzir o limite para `0.45` inclui também scores no intervalo `[0.45, 0.50)`, tornando a máscara moderadamente mais abrangente.
- **PROVÁVEL:** mais regiões classificadas pela máscara terão warping reduzido; o efeito visual exato continua pendente de teste físico.

## Expected visual improvement

- **PROVÁVEL:** menor edge warping, deformação e tremor em regiões difíceis durante movimento de câmera, sobretudo no ETS2 a aproximadamente 20–22 FPS-base.
- **HIPÓTESE:** a melhora poderá ser mais fácil de isolar em 2x, pois há somente um frame intermediário e menos influência de cadence/refresh.
- **NÃO CONFIRMADO:** magnitude da melhora e comportamento em 3x/4x até o teste no aparelho.

## Possible artifacts

- **RISCO BAIXO A MÉDIO:** falsos positivos da máscara podem proteger partes da cena que deveriam mover, criando regiões mais estáticas, ghosting diferente ou redução local do movimento interpolado.
- O valor foi reduzido apenas de `0.50` para `0.45`; nenhuma compensação adicional foi adicionada para não contaminar o A/B.

## Performance impact

- **CONFIRMADO estaticamente:** nenhum pass, dispatch, image, descriptor, copy ou optical-flow adicional foi introduzido.
- **PROVÁVEL:** custo computacional praticamente equivalente ao baseline; a distribuição de pixels que passam pela máscara muda, mas a estrutura do shader e o número de invocações permanecem iguais.
- **NÃO CONFIRMADO:** qualquer variação de FPS até benchmark físico; nenhum ganho foi presumido.

## Regression risk

- **CONFIRMADO estaticamente:** 2x/3x/4x mantêm fractions `0.5`; `1/3, 2/3`; `1/4, 2/4, 3/4`.
- **CONFIRMADO estaticamente:** sincronização da Parte 2/Parte 3 Rodada 1, semáforos, SYNC_FDs, fences, command buffers, images, external handles e present modes permanecem intactos.
- **CONFIRMADO estaticamente:** Lossless.dll e os shaders proprietários permanecem inalterados e externos.
- **PENDENTE NO APARELHO:** Tomb Raider inicia e mantém 2x/3x/4x sem regressão visual ou de estabilidade.

## Next-step decision matrix

- **A — melhora clara, sem efeito colateral:** considerar um A/B separado com `0.40`.
- **B — melhora pequena, sem regressão:** `0.40` pode ser avaliado com cautela em outra build de variável única.
- **C — regiões estáticas, ghosting ou piora:** reverter imediatamente para `0.50`.
- **D — nenhuma diferença perceptível:** não continuar reduzindo cegamente; reavaliar a influência real da máscara.

## Part 4 Round 5 - uiThreshold Revert and Performance Mode OFF Freeze

## Physical regression at uiThreshold 0.45

- **CONFIRMADO FISICAMENTE:** `uiThreshold=0.45` não melhorou o ETS2, aumentou stutter e elevou ligeiramente a distorção, sobretudo em 4x; 2x/3x continuaram com tremor.
- **DECISÃO:** o experimento da Rodada 4 foi rejeitado. Nenhum valor abaixo de `0.50` será testado nesta rodada.

## Baseline restoration 0.50

- **CONFIRMADO:** `getDefaultConstantBuffer()` voltou a preencher `uiThreshold=0.50F`.
- **CONFIRMADO:** o hunk experimental de threshold foi removido do patch de compatibilidade; não restou lógica dinâmica, clamp, UI ou override por multiplier/GPU/jogo.
- **CONFIRMADO:** a rebuild produziu novamente o mesmo SHA256 da biblioteca baseline anterior ao experimento: `7c58d7a40cd8b64510614e6906c142858883e91522b9f4397394211f7350e81c`.

## Performance Mode ON path

- **CONFIRMADO:** `ctx.perf=true` seleciona o shader registry `performance` e define `m=1` em Alpha0, Alpha1, Gamma1, Delta0 e Delta1.
- **CONFIRMADO:** o número lógico de dispatches permanece 34 no pre-pass mais 66 por destination; Performance reduz largura de recursos/descriptors e usa módulos proprietários menores, não remove a cadeia Gamma/Delta/Generate.
- **CONFIRMADO:** esse caminho permanece inalterado nesta rodada e é o baseline fisicamente funcional.

## Performance Mode OFF path

- **CONFIRMADO:** `ctx.perf=false` seleciona o registry `quality` e define `m=2` nos mesmos cinco componentes.
- **CONFIRMADO:** Mipmaps, Beta e Generate continuam presentes e com a mesma organização de submits; o caminho OFF não possui fence, semaphore ou timeline próprios diferentes de ON.
- **CONFIRMADO FISICAMENTE:** no aparelho, OFF congela jogo/Winlator em vez de apenas reduzir FPS.
- **NÃO CONFIRMADO:** se o congelamento termina em timeout de fence, device lost, watchdog/reset do Turnip ou outro erro; o teste disponível não trouxe `VkResult`/device-fault suficiente para distinguir essas causas.

## m=1 vs m=2 resource model

- **CONFIRMADO:** `m=2` dobra canais temporários em Alpha0/Alpha1, Gamma1 e Delta1 e adiciona uma segunda saída em parte de Delta0. Isso fornece mais largura/refinement ao modelo Quality.
- **CONFIRMADO:** os outputs finais consumidos por Generate continuam um Gamma RGBA16F, Delta0 RGBA16F e Delta1 RGBA16F; `m=2` altera a computação upstream, não a interface final de Generate.
- **CONFIRMADO:** com entrada 1280×720 e Flow Scale 0.20 (`flowExtent≈256×144`), a memória de pixels adicional estimada de `m=2` é aproximadamente 0,23 MiB em 2x, 0,28 MiB em 3x e 0,32 MiB em 4x, antes de alinhamento de allocations do driver.
- **PROVÁVEL:** em Flow Scale maior, o custo de bandwidth/compute cresce quadraticamente com o extent; a memória adicional isolada, especialmente em 0.20, não sustenta por si só uma conclusão de OOM.

## OFF freeze root cause

- **NÃO CONFIRMADO:** não foi encontrado deadlock host-side concreto, descriptor ausente, índice fora do range ou recurso m=2 não criado.
- **PROVÁVEL:** o caminho proprietário Quality exerce pressão substancialmente maior de compute/bandwidth e pode provocar GPU timeout/hang no stack atual; isso é compatível com OFF congelar, mas não pode ser classificado como causa confirmada sem erro runtime do aparelho.
- **DECISÃO DE SEGURANÇA:** nenhuma correção especulativa foi aplicada. Em particular, OFF não foi redirecionado silenciosamente para os shaders ON.

## Descriptor audit

- **CONFIRMADO:** `calculateDescriptorPoolLimits(count, perf)` possui tabelas separadas Quality/Performance; Quality reserva mais sampled/storage descriptors.
- **CONFIRMADO:** builders de Alpha/Gamma/Delta usam vetores de tamanho `m` ou `2*m`, e os shaders Quality refletidos declaram os bindings adicionais correspondentes.
- **CONFIRMADO:** todos os descriptor sets são construídos depois que as images m=2 existem e vivem durante todo o contexto.
- **NÃO ENCONTRADO:** descriptor não inicializado, binding errado, array curto ou pool dimensionado pela tabela Performance no modo OFF.

## Command buffer audit

- **CONFIRMADO:** as seis variantes pré-gravadas cobrem o MMC(2,3) dos índices temporais usados por Gamma0, Delta0 e Generate; `m` não muda esse período.
- **CONFIRMADO:** cada variante é gravada depois da criação dos descriptors e resources específicos do contexto. ON e OFF gravam a mesma ordem de passes, cada um com seus próprios shaders/descriptors.
- **CONFIRMADO:** a fence por frame impede resubmissão/reuse antes da conclusão. Resize, Flow Scale, Performance Mode ou multiplier recriam o contexto e seus command buffers.
- **NÃO ENCONTRADO:** command buffer OFF apontando para descriptors m=1 ou recurso de geração anterior.

## Synchronization audit

- **CONFIRMADO:** ON/OFF compartilham source-ready binary semaphore, prepass timeline semaphore, output-ready semaphores, render fence e os mesmos timeline values.
- **CONFIRMADO:** pre-pass sinaliza `idx`; cada main pass espera exatamente `idx`; somente o último main pass sinaliza a fence global, preservando ownership/lifetime.
- **NÃO ENCONTRADO:** signal ausente, wait value divergente, fence exclusiva do caminho Quality ou reuse antecipado causado por `m=2`.

## Bannerlator comparison

- **CONFIRMADO:** o asset Bannerlator local não é o mesmo backend glibc: expõe duas implementações nativas `LSFG_3_1` e `LSFG_3_1P`, usa AHardwareBuffer e possui arquitetura/binário distintos.
- **CONFIRMADO:** ele demonstra que um caminho Quality pode ser lento sem congelar, mas não fornece equivalência source-to-source para copiar sincronização/descriptors ao fork atual.
- **DESCONHECIDO:** qual diferença interna específica do backend Bannerlator evita o freeze; seu source LSFG correspondente não está presente na referência local.

## Confidence/refinement implications

- **PROVÁVEL:** os canais adicionais de `m=2` aumentam capacidade de refinement upstream antes dos campos finais Gamma/Delta e dos logits usados por Generate.
- **CONFIRMADO:** a interface final softmax/mistura de Generate não muda entre os modos.
- **NÃO CONFIRMADO:** se Quality reduz artefatos low-FPS no A6xx; estabilidade deve ser resolvida antes de qualquer comparação visual válida.

## Implemented correction

- **IMPLEMENTADO:** reversão única do experimento `uiThreshold 0.45 → 0.50` e rebuild limpa da biblioteca baseline.
- **NÃO IMPLEMENTADO:** correção do Performance Mode OFF, porque nenhuma causa host-side concreta e segura foi demonstrada.
- O caminho ON, shaders, descriptors, synchronization, Flow Scale, fractions, MAILBOX/FIFO e runtime externo permaneceram intactos.

## Expected performance cost

- A reversão do threshold não adiciona passes nem muda dispatch count; custo esperado equivale ao baseline 0.50.
- OFF continua esperado como mais pesado que ON. No estado atual, porém, deve ser considerado **não estável/não corrigido**, não simplesmente “mais lento”.

## Regression risk

- **BAIXO:** a nova biblioteca é byte-a-byte identificada pelo SHA256 da baseline 0.50 anterior à Rodada 4.
- **PENDENTE:** teste rápido Tomb Raider com Performance Mode ON para confirmar regressão zero no APK restaurado.
- **NÃO RECOMENDADO COMO TESTE DE QUALIDADE:** Performance Mode OFF permanece sem correção; um novo teste só seria diagnóstico do freeze conhecido, não validação de melhoria.

## Part 4 Round 2 - Gamma Delta Semantics

- **CONFIRMADO:** não existe `Lossless.dll` nem SPIR-V extraído em `/workspaces`, `/home/codespace` ou `/tmp`; nenhuma ferramenta SPIR-V está instalada no PATH. Logo, esta rodada não pode observar `OpImageRead`, swizzles, comparações ou aritmética interna.
- **CONFIRMADO:** o máximo demonstrável localmente é o dataflow Vulkan completo: bindings, ordem temporal, pirâmide, formats, UBOs, variantes e consumidores.
- **CONFIRMADO:** Gamma forma um campo coarse-to-fine de sete níveis; Delta adiciona dois campos em somente três níveis finos; Generate é o único consumidor final dos três campos.
- **PROVÁVEL:** Gamma é o campo primário de reconstrução/motion e os dois Delta são refinamentos complementares. Isso deriva da topologia e não identifica canais.
- **DESCONHECIDO:** semântica individual de R/G/B/A, confidence, validity e occlusion.
- **DECISÃO:** nenhuma alteração runtime é justificável sem os SPIR-V reais.

## Gamma channel analysis

Dataflow por destination/fraction:

1. `Gamma0[0]` recebe dois grupos temporais de `Alpha1`, black como prior e o UBO da fraction; escreve três `RGBA8`.
2. `Gamma1[0]` refina essas três imagens e combina black + `Beta1[5]`; escreve `Gamma RGBA16F` no nível mais grosseiro.
3. Nos seis níveis seguintes, `Gamma0` recebe o Gamma RGBA16F anterior como prior; `Gamma1` combina esse prior com o nível Beta correspondente.
4. `Gamma1[6]`, em aproximadamente `flowExtent/4`, é amostrado por Delta e Generate.

- **CONFIRMADO:** Gamma depende de informação temporal Alpha, da pirâmide Beta, da fraction e do resultado coarse anterior.
- **CONFIRMADO:** Gamma é recalculado para cada intermediate, portanto não é somente optical flow independente da fraction.
- **PROVÁVEL:** RGBA16F contém dados de warp/reconstruction já posicionados para a fraction atual.
- **HIPÓTESE:** dois componentes podem representar deslocamento e os restantes peso/validity ou uma segunda direção.
- **DESCONHECIDO:** qualquer mapeamento `R=x`, `G=y`, `B/A=confidence`; o host nunca aplica swizzle ou acessa canais isolados.

## Delta channel analysis

Dataflow por destination/fraction:

1. Delta começa somente quando Gamma já alcançou os três níveis finos.
2. `Delta0` branch A recebe dois grupos Alpha temporais, prior Delta/black e produz três `RGBA8`.
3. `Delta1` branch A refina essas imagens, combina prior Delta/black + Beta e produz um `RGBA16F`.
4. `Delta0` branch B recebe os mesmos grupos Alpha, Gamma do nível imediatamente anterior e prior Delta; produz `m` imagens `RGBA8`.
5. `Delta1` branch B refina esse conjunto, combina prior Delta/black e produz o segundo `RGBA16F`.
6. Ambos são propagados coarse-to-fine e os dois campos finais entram em Generate.

- **CONFIRMADO:** o ramo B está explicitamente acoplado a Gamma; o ramo A também usa Gamma na construção passada como `additionalInput1`.
- **CONFIRMADO:** Delta não substitui Gamma; os três campos são necessários simultaneamente para Generate.
- **PROVÁVEL:** Delta corrige ou complementa o campo primário Gamma nas escalas onde precisão espacial é mais relevante.
- **HIPÓTESE:** os dois Delta podem representar forward/backward reconstruction ou dois tipos de validade/occlusion.
- **DESCONHECIDO:** semântica de canais e range; RGBA16F demonstra capacidade, não significado.

## Generate consumption

- **CONFIRMADO:** binding sampled 32 recebe o frame real temporalmente anterior e binding 33 o atual. A seleção alterna pelo `fidx%2` para preservar essa ordem.
- **CONFIRMADO:** bindings sampled 34, 35 e 36 recebem respectivamente Gamma final, Delta field 0 e Delta field 1.
- **CONFIRMADO:** binding storage 48 é a destination full-resolution; samplers 16/17 são border-black e edge-clamp; UBO 0 é o buffer da fraction.
- **CONFIRMADO:** Generate sempre recebe os cinco sampled inputs; o host não possui caminho que omita condicionalmente Gamma ou Delta.
- **CONFIRMADO:** não há blit/upscale intermediário; Generate amostra campos aproximadamente `flowExtent/4` enquanto despacha sobre `sourceExtent/16` workgroups.
- **DESCONHECIDO:** leitura condicional, swizzles, uso como UV, comparação com threshold e fórmula de blend estão dentro do SPIR-V ausente.
- **PROVÁVEL:** acesso simultâneo a previous/current e três campos permite warp e escolha/blend entre fontes, mas a política exata não é observável.

## uiThreshold data flow

- **CONFIRMADO:** `uiThreshold` ocupa bytes 32–35 do UBO de 48 bytes e é inicializado como `float 0.5` tanto no buffer global quanto em todos os buffers por intermediate.
- **CONFIRMADO:** o buffer é copiado uma vez para memória Vulkan na criação do contexto; não existe stale update porque o valor é imutável durante o contexto.
- **CONFIRMADO:** UBO 0 é vinculado em Mipmaps, Beta final, Gamma0, Gamma1 final, ambos Delta0, ambos Delta1 finais e Generate.
- **CONFIRMADO:** Alpha e refinamentos internos sem UBO não recebem o campo diretamente.
- **CONFIRMADO:** multiplier e Performance Mode não mudam offset, tipo ou valor; somente `timestamp` difere entre buffers por destination.
- **DESCONHECIDO:** quais módulos realmente carregam offset 32 e qual operação executam. Um binding compartilhado não prova uso.
- **PROVÁVEL:** o nome `uiThreshold` e a presença no Generate são compatíveis com proteção/classificação de UI, mas não constituem prova.
- **HIPÓTESE:** comparação com 0.5 pode selecionar tratamento de HUD ou uma máscara escalar produzida no pipeline.
- **DECISÃO:** não alterar 0.5 até uma disassembly demonstrar load + consumidores desse membro.

## Confidence evidence

- **CONFIRMADO:** não existe binding, imagem ou host parameter explicitamente nomeado confidence.
- **CONFIRMADO:** Gamma e cada Delta têm quatro canais FP16 e podem transportar dados escalares adicionais além de um vetor 2D.
- **CONFIRMADO:** Generate recebe todos os campos e ambos os frames, arquitetura compatível com peso/validity/fallback.
- **HIPÓTESE:** B/A de algum campo pode conter confidence ou blend weight.
- **DESCONHECIDO:** existência, canal, range 0..1 ou comparação com `uiThreshold`.
- **NÃO USAR COMO PROVA:** formato RGBA, black prior e nome Delta isoladamente.

## Validity/occlusion evidence

- **CONFIRMADO:** há dois source frames, dois samplers com políticas diferentes, três campos refinados e propagação coarse-to-fine.
- **CONFIRMADO:** regiões fora da imagem podem retornar border-black; edge-clamp está disponível em passes finais.
- **PROVÁVEL:** essa interface é suficiente para tratar correspondências inválidas ou regiões recém-reveladas.
- **HIPÓTESE:** os dois Delta representam informação direcional ou masks complementares para disocclusion.
- **DESCONHECIDO:** forward/backward consistency, hole filling, select de nearest real frame e existência de mask explícita.
- **CONFIRMADO:** não há fallback ou regra de disocclusion implementada pelo host.

## Motion direction

- **CONFIRMADO:** a layer/backend mantém previous e current reais em ordem temporal; Generate recebe previous primeiro e current depois.
- **CONFIRMADO:** Alpha/Beta consomem históricos circulares reais; generated frames nunca entram nessa cadeia.
- **PROVÁVEL:** a existência de dois source frames ordenados e dois Delta finais suporta processamento bidirecional.
- **HIPÓTESE:** Delta0 e Delta1 correspondem a previous→current e current→previous.
- **DESCONHECIDO:** direção concreta de Gamma e de cada Delta sem observar coordenadas/aritmética do shader.

## Motion range and scaling

- **CONFIRMADO:** `resolutionInvScale=1/flow_scale` chega aos passes com UBO e informa a relação entre source e flow resolution.
- **CONFIRMADO:** `timestamp` está no range aberto 0..1 e representa somente a posição fracionária.
- **CONFIRMADO:** o host não fornece sourceExtent, flowExtent, FPS ou frame delta diretamente no UBO; dimensões também estão implicitamente disponíveis aos shaders por operações de imagem.
- **CONFIRMADO:** campos finais são RGBA16F signed-capable, sem clamp host-side.
- **PROVÁVEL:** `resolutionInvScale` converte deslocamentos entre a grade reduzida e a resolução final.
- **DESCONHECIDO:** pixel units versus normalized UV, range efetivo, saturação, clamp, search radius e precisão de motion grande.
- **PROVÁVEL:** Flow Scale 0.20 reduz precisão espacial do campo antes de Generate, mesmo que RGBA16F preserve range numérico.

## Performance Mode quality implications

- **CONFIRMADO:** Performance usa módulos distintos e reduz `m=2` para `m=1` em Alpha/Gamma/Delta.
- **CONFIRMADO:** preserva três outputs Gamma0, um Gamma final, três outputs Delta0-A e dois Delta finais; portanto a interface final de Generate não muda.
- **CONFIRMADO:** não remove níveis, dispatches ou campos finais, mas reduz pela metade várias features/temporárias internas e bindings.
- **PROVÁVEL:** descarta capacidade de representação/refinement interno que pode ajudar casos ambíguos, enquanto reduz compute/bandwidth.
- **PROVÁVEL:** Performance OFF pode melhorar algum detalhe de motion/disocclusion, mas pode também baixar FPS-base e piorar a distância temporal.
- **DESCONHECIDO:** se a informação removida é confidence, filtros, feature channels ou redundância do modelo.
- **CANDIDATO SUSTENTADO:** A/B ON/OFF em 2x é mais seguro que alterar `uiThreshold`, pois ambos são caminhos oficiais e a mudança isola custo versus qualidade.

## Host-side control surface

| Controle | Influência confirmada | Limite semântico |
|---|---|---|
| Flow Scale | muda `flowExtent` de toda a cadeia até Gamma/Delta e `resolutionInvScale` | trade-off espacial confirmado; não adapta tempo |
| Fraction/timestamp | seleciona posição lógica por destination e chega a Gamma/Delta/Generate | não representa frame duration real |
| Performance Mode | seleciona família proprietária e `m=1` versus `m=2` | efeito interno por canal desconhecido |
| `uiThreshold` | float 0.5 presente nos UBOs | não é configurável e semântica não provada |
| `resolutionInvScale` | recíproco de Flow Scale | conversão exata dentro do shader desconhecida |
| FP16 permission | seleciona módulos FP16 quando suportados | performance/precisão; não é quality knob low-FPS comprovado |

- **CONFIRMADO:** não existe host control para confidence, occlusion, motion range, temporal delta, search radius ou hole filling.

## Safe experiment candidates

1. **BAIXO RISCO:** Performance ON versus OFF em 2x, mesma cena ETS2, mesmo Flow Scale e gráficos; comparar tremor/edge warping e FPS-base.
2. **BAIXO RISCO:** Flow Scale 0.20 versus valor maior com Performance fixo; avaliar precisão de bordas separadamente do FPS.
3. **ANÁLISE SEM RUNTIME:** fornecer localmente a DLL usada para extrair/disassemblar somente os IDs 256, 257, 258, 262, 266 e 274, cobrindo Generate e finais Gamma/Delta.
4. **NÃO SEGURO AINDA:** qualquer A/B de `uiThreshold`; falta prova de leitura e significado.

## Host-side bugs

- **CONFIRMADO:** offsets C++ são coerentes: `resolutionInvScale=24`, `timestamp=28`, `uiThreshold=32`, tamanho total 48 bytes.
- **CONFIRMADO:** todos são enviados como `float`; não foi encontrada conversão int/float incorreta.
- **CONFIRMADO:** binding UBO 0, ordem sampled 32+ e storage 48+ são consistentes entre registry e descriptor builders.
- **CONFIRMADO:** buffers por destination não ficam stale: fraction, Flow Scale e modo são imutáveis por contexto e hot reload recria o contexto.
- **CONFIRMADO:** Quality/Performance selecionam contagens e módulos correspondentes; Generate comum recebe a mesma interface final.
- **CONFIRMADO:** nenhum bug host-side real foi encontrado nesta rodada.

## Risk assessment

- **CONFIRMADO:** somente documentação foi alterada; runtime, SPIR-V, assets e configuração permanecem intactos.
- **BAIXO RISCO futuro:** teste dos dois Performance Modes já suportados.
- **MÉDIO RISCO:** interpretar campos por captura visual sem disassembly pode confundir correlação com semântica.
- **ALTO RISCO:** alterar `uiThreshold`, channel mapping ou descriptors antes de observar as instruções do SPIR-V.
- **DESCONHECIDO:** quanto da perda low-FPS vem de motion estimation versus confidence/disocclusion interna.
- **DECISÃO FINAL:** não gerar APK; a próxima rodada útil deve ser reflexão dos módulos reais ou A/B controlado Performance ON/OFF, sem mudança de default.

## Part 4 Round 3 - Local Lossless SPIR-V Analysis

- **CONFIRMADO:** a DLL local legítima foi encontrada fora do repositório e analisada sem mover, modificar, versionar ou embutir o arquivo.
- **CONFIRMADO:** o parser PE existente extraiu recursos somente para `/tmp/lsfg-part4-round3/`.
- **CONFIRMADO:** os 98 blobs SPIR-V extraídos passaram em `spirv-val`; 96 deles correspondem às variantes realmente endereçadas pelo registry atual.
- **CONFIRMADO:** `spirv-dis` e `spirv-cross` permitiram reconstruir bindings, UBOs, canais e fórmula final de Generate.
- **CONFIRMADO:** nenhuma alteração runtime foi feita.

## DLL/resource inventory

- Caminho local: `/workspaces/Winlator/Lossless.dll`
- Tamanho: `7,521,280` bytes
- SHA256: `626b196d799606cd4250b7b29e04228692ab70cf56a5d1bbb56d748c8219f0eb`
- Recursos SPIR-V encontrados: 98 (`303–400`)
- Recursos usados pelo registry: 96; FP16 `304–351`, FP32 `353–400`
- Recursos vizinhos não selecionados pelo registry: 303 e 352
- **CONFIRMADO:** nenhum arquivo extraído foi criado sob `/workspaces/Winlator/temp`.

## Shader inventory

Mapeamento real: `resource = 49 + logical_id + (Performance ? 23 : 0) + (FP32 ? 49 : 0)`.

| Função | FP16 Quality | FP16 Performance | FP32 Quality | FP32 Performance |
|---|---:|---:|---:|---:|
| Mipmaps 255 | 304 | comum | 353 | comum |
| Generate 256 | 305 | comum | 354 | comum |
| Gamma/Delta base 257–258 | 306–307 | 329–330 | 355–356 | 378–379 |
| Gamma final 262 | 311 | 334 | 360 | 383 |
| Delta0 final 266 | 315 | 338 | 364 | 387 |
| Delta1 final 274 | 323 | 346 | 372 | 395 |
| Beta mask final 279 | 328 | 351 | 377 | 400 |

Módulos críticos analisados:

| ID | Função | Bytes | SHA256 |
|---:|---|---:|---|
| 305 | Generate comum | 4,308 | `a48155860e7c23420dbf36f3d4253cf2db3650dd75b6b3859db0a13a7e3a9b43` |
| 311 | Gamma final FP16 Quality | 17,140 | `e8999d908172cfe77e0e17e7865d0d8d980fbee5789879916c023f85e4837e12` |
| 315 | Delta0 final FP16 Quality | 17,236 | `be6b7f1a71fca77cd0c1d3602b6c6ded1d51241ce3f98b02ca4165f77da2f0cb` |
| 323 | Delta1 final FP16 Quality | 11,980 | `bf1dadc03d5738858e21453f2b8f1e8dab3bfcce03f5da3076502b3e312c6864` |
| 328 | Beta mask FP16 Quality | 10,068 | `a7a224d75f053120d7d16e1d85d466e097b18f2afa663360c0a444c889185d23` |
| 334 | Gamma final FP16 Performance | 10,364 | `fa2cf6acedae5345c4a2961cf4e75c6ccb108aaf1ffc71fefa240c4a18a780ad` |
| 338 | Delta0 final FP16 Performance | 10,300 | `3e565a0f50dd869eb0c528942d16e7c7c2651b85efa7dea4225a6e7b02849a0c` |
| 346 | Delta1 final FP16 Performance | 7,176 | `b8b082ff8617ac477afbdeb1b4e59770d3265eba3a83a2b897c5a8e8ec628dc4` |
| 351 | Beta mask FP16 Performance | 10,084 | `e8b47a97d479241ff909c3e0f843d045b88bff5dafb76dc081e5edaf9e02f448` |

- **CONFIRMADO:** Generate FP16/FP32 é byte-a-byte idêntico; não contém arithmetic FP16.
- **CONFIRMADO:** todos são compute shaders; workgroup Generate é 16×16 e os finais Gamma/Delta são 8×8.

## uiThreshold semantics

- **CONFIRMADO:** `UiThreshold` existe somente nos quatro módulos Beta finais 328/351/377/400, no offset 32 do UBO.
- **CONFIRMADO:** Gamma, Delta e Generate refletem UBO de somente 32 bytes, terminando em `Timestamp`; eles não leem `UiThreshold` diretamente.
- **CONFIRMADO:** Beta produz um score aprendido, aplica sigmoid, depois `step(UiThreshold, score) * score`.
- **CONFIRMADO:** valores abaixo do threshold viram zero; valores acima permanecem com magnitude contínua entre threshold e 1.
- **CONFIRMADO:** o resultado forma seis mips `R8`, propagados por médias 2×2.
- **CONFIRMADO:** Gamma/Delta amostram essa pirâmide e multiplicam seus vetores por `1-mask`, inclusive em posições warped e no centro.
- **PROVÁVEL:** é uma máscara de proteção de UI/HUD ou regiões que o modelo decidiu não mover; o nome refletido `UiThreshold` e a supressão de motion sustentam essa interpretação.
- **CONFIRMADO:** baixar o threshold ativa a máscara em mais pixels e suprime mais movimento; subir faz o oposto.
- **RISCO:** baixar demais pode reduzir edge warping, mas também classificar cena como UI e produzir regiões estáticas/ghosting.

## Gamma channel semantics

- **CONFIRMADO:** Gamma final é um campo de motion bidirecional, não cor.
- **CONFIRMADO:** `R,G` (`xy`) são o displacement usado para amostrar Previous; `B,A` (`zw`) são o displacement usado para amostrar Current.
- **CONFIRMADO:** Generate escala `xy` por `2*Timestamp` e `zw` por `2*(1-Timestamp)`.
- **CONFIRMADO:** Gamma é refinado coarse-to-fine; quando existe prior, o shader soma `prior*2` a `xy` e combina `prior.zw` com o negativo da correção aprendida.
- **CONFIRMADO:** a mask Beta atenua `xy` e `zw` nas coordenadas warped e novamente no centro.
- **PROVÁVEL:** Gamma é a primeira hipótese bidirecional de motion para o pixel.
- **DESCONHECIDO:** direção física/sign convention do vetor original antes da fórmula de backward sampling; a função observável é a coordenada de amostragem.

## Delta0 channel semantics

- **CONFIRMADO:** Delta0 final possui exatamente a mesma organização de canais que Gamma: `xy` displacement para Previous e `zw` para Current.
- **CONFIRMADO:** é calculado por uma rede distinta, usa Gamma/prior no ramo upstream e sofre a mesma atenuação pela mask Beta.
- **CONFIRMADO:** Generate trata Delta0 como uma segunda hipótese de motion bidirecional, criando mais duas posições warped.
- **PROVÁVEL:** Delta0 é um candidato alternativo/refinado destinado a regiões onde uma única hipótese é ambígua.
- **DESCONHECIDO:** se a especialização é explicitamente occlusion, boundary ou outro padrão aprendido.

## Delta1 channel semantics

- **CONFIRMADO:** Delta1 não é vetor de motion.
- **CONFIRMADO:** seus quatro canais são logits de mistura correspondentes a: Gamma→Previous, Gamma→Current, Delta0→Previous e Delta0→Current.
- **CONFIRMADO:** Generate amostra cada canal na posição warped correspondente e aplica softmax `exp(logit)/sum(exp(logits))`.
- **CONFIRMADO:** esses quatro pesos controlam a contribuição relativa das quatro amostras reconstruídas.
- **PROVÁVEL:** Delta1 é confidence/selection aprendida por hipótese e direção.
- **CONFIRMADO:** os valores armazenados não precisam estar em 0..1; a normalização ocorre somente no Generate.

## Generate reconstruction path

Para `t=Timestamp`, `s=ResolutionInvScale`, Gamma `G` e Delta0 `D`:

1. `G` e `D` são multiplicados por `s` para converter da grade de flow à escala source.
2. São criadas quatro coordenadas: `G.xy*2t`, `G.zw*2(1-t)`, `D.xy*2t`, `D.zw*2(1-t)`.
3. Delta1 fornece um logit em cada coordenada correspondente.
4. Softmax converte os quatro logits em pesos.
5. Duas amostras vêm de Previous e duas de Current.
6. Previous recebe ainda o fator temporal `(1-t)` e Current recebe `t`; o resultado é renormalizado com epsilon `1e-8`.

- **CONFIRMADO:** há warp bidirecional, duas hipóteses de motion e mistura learned-confidence.
- **CONFIRMADO:** não há branch de fallback explícito para copiar um frame real; fallback ocorre apenas pela seleção/ponderação das quatro amostras.
- **CONFIRMADO:** a reconstrução full-resolution usa campos de menor resolução via sampling.

## Confidence evidence

- **CONFIRMADO:** Delta1 é confidence relativa em forma de quatro logits de mistura.
- **CONFIRMADO:** confidence é espacial e amostrada nas próprias posições warped.
- **CONFIRMADO:** não existe threshold host-side aplicado a esses logits; a escolha é softmax contínua.
- **PROVÁVEL:** baixa separação entre logits representa ambiguidade; um logit dominante seleciona uma hipótese/direção.
- **DESCONHECIDO:** calibração probabilística dos logits; softmax não garante confidence estatística real.

## Occlusion/disocclusion evidence

- **CONFIRMADO:** o algoritmo possui mecanismos compatíveis com disocclusion: dois sentidos temporais, duas hipóteses de motion e pesos espaciais por hipótese.
- **CONFIRMADO:** a máscara Beta pode zerar motion em regiões classificadas e sua pirâmide influencia Gamma/Delta.
- **PROVÁVEL:** Delta1 escolhe entre Previous/Current e entre Gamma/Delta0 em oclusões/bordas.
- **DESCONHECIDO:** não foi encontrada uma variável explicitamente nomeada occlusion nem um consistency check geométrico clássico.
- **CONFIRMADO:** não existe hole-fill explícito ou nearest-frame branch no Generate; o tratamento é aprendido/ponderado.

## Motion representation

- **CONFIRMADO:** Gamma/Delta0 armazenam dois vetores 2D signed em RGBA16F.
- **CONFIRMADO:** no Generate eles são convertidos em offsets de pixel source por `ResolutionInvScale`, somados ao centro do pixel e só então normalizados pelo tamanho source.
- **CONFIRMADO:** a representação observável é displacement em unidades da grade de flow, não UV normalizado.
- **CONFIRMADO:** `Timestamp` dimensiona o deslocamento para a posição intermediária.
- **CONFIRMADO:** o shader não recebe delta temporal real; 16 ms e 50 ms são indistinguíveis exceto pela magnitude/conteúdo inferido entre as imagens.

## Motion limits

- **CONFIRMADO:** Generate não aplica clamp, min/max ou magnitude limit aos vetores Gamma/Delta0.
- **CONFIRMADO:** Gamma/Delta atenuam motion pela mask Beta, mas isso é gating espacial, não clamp de magnitude.
- **CONFIRMADO:** out-of-range sampling depende do sampler border-black/edge behavior configurado pelo host.
- **PROVÁVEL:** motion muito grande pode sair do suporte útil, amostrar bordas e aumentar ambiguidades de softmax/reconstruction.
- **DESCONHECIDO:** limite aprendido/search range efetivo nas convoluções anteriores.

## Performance Mode algorithm differences

- **CONFIRMADO:** Generate é exatamente o mesmo módulo nos dois modos.
- **CONFIRMADO:** a representação final também é a mesma: Gamma/Delta0 continuam motion bidirecional e Delta1 continua quatro logits.
- **CONFIRMADO:** Performance usa redes com menos feature maps/inputs (`m=1` versus `m=2`) e pesos diferentes; não remove níveis nem outputs finais.
- **PROVÁVEL:** Quality dispõe de maior capacidade para resolver motion/occlusion ambíguos; Performance preserva a lógica, mas com modelo menor.
- **PROVÁVEL:** em low-FPS, Quality pode melhorar a seleção/campo, porém seu custo adicional pode reduzir FPS-base e anular o ganho.

## Host-side correctness revalidation

- **CONFIRMADO:** layout host `UiThreshold` no offset 32 coincide com a reflexão Beta.
- **CONFIRMADO:** descriptor range de 48 bytes cobre UBOs refletidos de 32 ou 36 bytes; range maior é válido e os bytes extras são ignorados.
- **CONFIRMADO:** bindings Previous/Current/Gamma/Delta0/Delta1 usados pelo Generate coincidem com a ordem do builder.
- **CONFIRMADO:** `ResolutionInvScale`, `Timestamp`, formats RGBA16F e extents correspondem às fórmulas observadas.
- **CONFIRMADO:** seleção Quality/Performance e FP16/FP32 resolve os recursos esperados.
- **CONFIRMADO:** nenhum bug host-side foi encontrado.

## Safe experiment candidates

1. **RECOMENDADO PARA RODADA 4 — risco controlado:** expor temporariamente um único valor de `UiThreshold` para A/B em 2x, mantendo todo o restante fixo. Comparar 0.50 com uma redução pequena, por exemplo 0.45, somente para verificar se maior proteção reduz edge warping/tremor ou cria regiões estáticas.
2. **BAIXO RISCO:** Performance ON/OFF em 2x com o threshold fixo em 0.50, separando capacidade do modelo de GPU feedback.
3. **BAIXO RISCO:** repetir cada caso em Flow Scale 0.20 e valor maior, pois a mask e os motion fields compartilham a resolução reduzida.
- **NÃO RECOMENDADO:** mudar pesos/logits, canais, softmax ou SPIR-V.

## Risk assessment

- **CONFIRMADO:** a DLL original permaneceu intacta; recursos e disassemblies existem somente em `/tmp`.
- **CONFIRMADO:** nada privado foi adicionado ao Git ou APK.
- **BAIXO risco desta rodada:** somente análise e relatório.
- **MÉDIO risco experimental:** threshold menor pode reduzir warp em regiões classificadas, mas aumentar ghosting/static patches por falso positivo de UI.
- **ALTO risco:** alterar shader, logits ou motion channels.
- **DECISÃO FINAL:** nenhuma mudança runtime; a Rodada 4 pode testar uma única variação pequena de `UiThreshold`, reversível e isolada.

## Part 3 Round 1 - Visual root cause

- **CONFIRMADO:** a Parte 2 foi validada fisicamente: Tomb Raider e NFS permanecem saudáveis; eficiência melhorou; ETS2 em FPS-base baixo preserva a distorção/tremor principal.
- **CONFIRMADO no source:** as fractions de interpolação estão corretas, mas posição de interpolação não define tempo de exibição.
- **CONFIRMADO no source:** antes desta rodada, somente o último main pass sinalizava `outputReadySemaphore`. A primeira cópia da layer aguardava esse sinal; logo todos os Generate de 3x/4x terminavam antes de qualquer intermediário avançar para copy/present.
- **PROVÁVEL:** esse gate final ampliava apresentação em lote e backpressure além da limitação natural do optical flow.

## Present pacing path

Caminho anterior em 4x:

1. backend agenda Generate 1, Generate 2 e Generate 3 na mesma queue;
2. somente Generate 3 sinaliza o SYNC_FD exportado;
3. a primeira cópia da layer espera esse sinal final;
4. depois disso as três cópias e os quatro presents são enfileirados em sequência.

- **CONFIRMADO:** não existe deadline ou intervalo lógico explícito entre os `QueuePresentKHR`.
- **CONFIRMADO:** o primeiro intermediate tinha dependência artificial do último Generate.
- **PROVÁVEL:** remover essa dependência permite que copy/present de cada intermediate progrida junto com a conclusão real do respectivo pass.

## MAILBOX replacement analysis

- **CONFIRMADO:** MAILBOX pode manter apenas a apresentação pendente mais recente; frames prontos antes do próximo refresh podem ser substituídos.
- **PROVÁVEL:** quando vários intermediários ficam prontos após o mesmo gate final, a chance de substituição em lote aumenta.
- **CONFIRMADO:** a correção selecionada não remove nem força MAILBOX e não muda o fallback FIFO.
- **NÃO CONFIRMADO:** quais intermediários o compositor/Turnip realmente exibe ou descarta sem timing capturado no aparelho.

## Refresh mismatch

- **CONFIRMADO matematicamente:** 20 FPS ×4 produz até 80 submits para 60/90 Hz; 27,5 FPS ×4 produz cerca de 110 submits e excede 90 Hz.
- **PROVÁVEL:** mismatch com refresh e MAILBOX pode gerar descarte temporalmente irregular.
- **NÃO CONFIRMADO:** `VK_KHR_present_id`, `VK_KHR_present_wait` ou `VK_GOOGLE_display_timing` não são negociados/usados pelo caminho atual. Suporte efetivamente exposto por WineVulkan/Turnip/WSI precisa ser consultado no aparelho antes de qualquer integração.

## Real-frame history

- **CONFIRMADO:** somente duas source images reais alternam por `fidx % 2`.
- **CONFIRMADO:** destination/generated images são separadas e nunca alimentam Mipmaps/Alpha/Beta do próximo frame.
- **CONFIRMADO:** o frame real atual é copiado e sinalizado por SYNC_FD antes do backend; a fence impede reutilização prematura do conjunto anterior.
- **CONFIRMADO:** image-state tracking continua protegendo acquire/present duplicado e stale swapchain image.

## Queue backpressure

- **CONFIRMADO:** `renderFence` serializa conjuntos reais na layer, `cmdbufFence` serializa o backend e `AcquireNextImageKHR(UINT64_MAX)` pode bloquear por disponibilidade WSI.
- **CONFIRMADO:** jogo, cópias e presents usam a queue interceptada; compute roda em queue/device do backend, sincronizado por SYNC_FD.
- **PROVÁVEL:** 3x/4x podem atrasar o próximo frame real via GPU contention, acquire e conclusão das cópias.
- **CONFIRMADO:** a correção não remove waits/fences; altera somente a granularidade do sinal backend→layer.

## Low-FPS feedback loop

- **PROVÁVEL:** custo do framegen reduz headroom, reduz FPS real, aumenta distância temporal e torna optical flow mais difícil; 3x/4x amplificam o ciclo.
- **CONFIRMADO:** o source permite esse backpressure, mas não contém GPU timestamps para separar custo compute, WSI e jogo.
- **NÃO CONFIRMADO:** peso relativo desse ciclo no ETS2 sem profiler GPU controlado.

## Selected functional fix

**Uma única correção funcional:** prontidão independente por destination/intermediate.

- backend mantém um semaphore SYNC_FD exportável por destination;
- cada main pass sinaliza seu próprio semaphore ao terminar;
- `scheduleFrames()` retorna um FD por intermediate;
- layer importa um semaphore por destination;
- cada cópia espera seu acquire e a prontidão do seu próprio Generate;
- fence final, ordem dos passes, shaders, descriptors, fractions, MAILBOX e presents permanecem iguais.

- **CONFIRMADO:** em 2x o comportamento é equivalente ao semaphore único anterior.
- **CONFIRMADO:** em 3x/4x o primeiro intermediate não depende mais artificialmente da conclusão do último Generate.
- **CONFIRMADO:** todos os SYNC_FDs continuam temporariamente importados e cada semaphore possui exatamente um signal/wait por frame real.

## Expected visual effect

- **PROVÁVEL:** reduzir rajada de intermediários e permitir pipeline mais progressivo entre backend, copy e present.
- **PROVÁVEL:** reduzir parte do tremor/cadence irregular e da latência do primeiro intermediate, especialmente em 3x/4x.
- **HIPÓTESE:** reduzir backpressure pode preservar um pouco mais de FPS-base em GPU pressionada.
- **NÃO CONFIRMADO:** não corrige erros ópticos inerentes a 50–67 ms entre frames reais; distorção de bordas/occlusion pode permanecer.

## Risk assessment

- Risco: **MÉDIO**, porque altera sincronização cross-device, embora preserve os mesmos primitives e ownership.
- **CONFIRMADO estaticamente:** número de semáforos passa de um por contexto para um por destination (1/2/3 em 2x/3x/4x); lifetime continua preso ao contexto/swapchain.
- **CONFIRMADO estaticamente:** último main pass ainda sinaliza `cmdbufFence`; última cópia ainda sinaliza `renderFence` e `originalReady`.
- **CONFIRMADO estaticamente:** nenhuma barrier, transition, shader, dispatch, image-state rule ou external-memory handle foi removido.
- Regressões prioritárias: corrupção/flicker, deadlock, semaphore import failure, ordem 2x/3x/4x, Tomb Raider e NFS.

## Part 3 Round 2 - Low FPS root cause

- **CONFIRMADO por teste:** a prontidão independente da Rodada 1 reduziu levemente o tremor sem regressão no Tomb Raider, mas 2x ainda treme com entrada estável de aproximadamente 20–22 FPS.
- **CONFIRMADO:** burst exclusivo de múltiplos intermediários não é a causa principal, porque o sintoma persiste com apenas um intermediário.
- **PROVÁVEL:** o componente dominante é a combinação de distância temporal de 45–50 ms, contenção GPU e serialização necessária para reutilizar o conjunto de recursos atual.
- **NÃO CONFIRMADO:** a contribuição individual de optical flow, GPU execution time e display cadence sem timestamps reais.

## Real frame history

- **CONFIRMADO:** a layer possui exatamente duas source images externas e copia cada novo frame real para `sourceImages[fidx % 2]`.
- **CONFIRMADO:** o backend usa o mesmo `fidx % 2` para Mipmaps e alterna os descriptor sets de Generate para manter a ordem do par real.
- **CONFIRMADO:** destination images são storage/output exclusivas; nenhuma delas é passada para Mipmaps como source do par seguinte.
- **CONFIRMADO:** generated frames nunca entram no optical flow seguinte.
- **CONFIRMADO:** depois do primeiro ciclo, o par usado é sempre o real imediatamente anterior e o real recém-copiado.

## Frame age and ordering

- **CONFIRMADO:** `fidx` da layer avança uma vez por present real interceptado e `fidx` do backend avança uma vez por `scheduleFrames`; os dois permanecem em lockstep no caminho de sucesso.
- **CONFIRMADO:** a fence da layer termina a última cópia antes de permitir reutilização; a fence do backend termina o último main pass antes do próximo schedule.
- **CONFIRMADO:** não existe caminho normal que pule silenciosamente `fidx` ou reutilize um source antigo enquanto o novo é tratado como atual.
- **CONFIRMADO / LIMITAÇÃO:** no primeiro schedule após criação, somente uma das duas source images recebeu conteúdo real; a outra foi apenas inicializada em layout GENERAL. O primeiro par pode conter history sem imagem real anterior.
- **PROVÁVEL:** essa limitação afeta somente o primeiro conjunto após ativação/recriação e não explica tremor contínuo durante gameplay.
- **DECISÃO:** não foi adicionada uma warm-up especial nesta rodada porque ela acrescentaria estado/sincronização e não atacaria o sintoma persistente observado.

## Swapchain index vs temporal order

- **CONFIRMADO:** `imageIdx` identifica somente a imagem WSI apresentada/adquirida e é usado para image-state tracking e cópia.
- **CONFIRMADO:** history temporal não usa `imageIdx`; usa source images próprias alternadas por `fidx`.
- **CONFIRMADO:** MAILBOX, acquire order ou reutilização de um índice de swapchain não reordena diretamente o par de source images.
- **CONFIRMADO:** guards `acquired`/`pendingPresent` detectam reutilização inválida da imagem WSI sem assumir que o índice representa tempo.

## Optical flow start timing

- **CONFIRMADO:** optical flow começa depois que a cópia do current real para a source image sinaliza `sourceReadySemaphore`.
- **CONFIRMADO:** o pre-pass Mipmaps/Alpha/Beta é o primeiro trabalho backend por par; não há sleep, filesystem polling ou processamento de generated frames antes dele.
- **CONFIRMADO:** `scheduleFrames` pode aguardar `cmdbufFence`, porém a fence da layer já exige a conclusão da última cópia dependente dos main passes anteriores; no fluxo normal essa espera backend tende a estar satisfeita.
- **PROVÁVEL:** o maior atraso anterior ao optical flow é a espera pela conclusão do conjunto anterior na layer, não setup CPU do backend.

## GPU queue contention

- **CONFIRMADO:** render do jogo, cópias LSFG e WSI usam o dispositivo/queue interceptados; o backend cria outro dispositivo Vulkan e uma compute-capable queue sobre a mesma GPU física.
- **CONFIRMADO:** devices/queues diferentes não significam execução física independente; jogo, optical flow, Generate e cópias competem pelos mesmos recursos A6xx.
- **PROVÁVEL:** compute e tráfego de storage images reduzem headroom do render, especialmente em A6xx menores.
- **NÃO CONFIRMADO:** grau real de overlap/preempção entre as queues no Turnip sem GPU timestamps/profiler.

## Blocking waits

- **CONFIRMADO:** `renderFence.wait(150 ms)` ocorre por frame real antes da nova cópia e pode bloquear CPU até a última cópia do conjunto anterior.
- **CONFIRMADO:** `cmdbufFence.wait()` ocorre por schedule backend antes de reutilizar pre-pass/main resources.
- **CONFIRMADO:** `AcquireNextImageKHR(UINT64_MAX)` pode bloquear por disponibilidade de imagem WSI para cada intermediate.
- **CONFIRMADO:** não há `QueueWaitIdle` no hot path.
- **CONFIRMADO:** `DeviceWaitIdle` aparece apenas em criação/initialization auxiliar, reload, close ou RenderDoc, não no present normal por frame.
- **DECISÃO:** nenhuma wait/fence foi removida; elas protegem reuse e correções anteriores de corrupção.

## Framegen feedback loop

- **CONFIRMADO estruturalmente:** o frame real N+1 não entra no LSFG até `renderFence` do conjunto N concluir a última cópia.
- **PROVÁVEL:** maior carga framegen prolonga essa fence/acquire, atrasa N+1, reduz FPS-base e aumenta o delta temporal fornecido ao próximo optical flow.
- **PROVÁVEL:** o ciclo cresce de 2x para 3x/4x, embora também exista em 2x.
- **NÃO CONFIRMADO:** quanto dos 20–22 FPS do ETS2 é perdido especificamente por esse ciclo versus render nativo do jogo.

## Stale intermediate analysis

- **CONFIRMADO:** a arquitetura não permite que um novo frame real seja processado enquanto copies do conjunto anterior permanecem pendentes; portanto não existe mistura silenciosa de history nova com intermediário antigo.
- **CONFIRMADO:** isso evita stale resource/use-after-free, mas cria backpressure rígido.
- **PROVÁVEL:** um intermediate pode perder valor temporal enquanto aguarda GPU/WSI, mesmo continuando correto para seu par de origem.
- **NÃO CONFIRMADO:** identificar e descartar esse intermediate exigiria um deadline/timestamp confiável que o backend atual não possui.

## Backpressure path

Fluxo confirmado:

`real N copy → optical-flow pre-pass → Generate(s) → destination copy/copies → renderFence → real N+1 copy`

- **CONFIRMADO:** N+1 espera a última destination copy de N, inclusive em 2x.
- **CONFIRMADO:** a Rodada 1 removeu a dependência entre Generate 1 e Generate final, mas não removeu a fence final necessária ao reuse do contexto.
- **PROVÁVEL:** eliminar essa espera corretamente requer pelo menos outro conjunto de source/destination/intermediate resources ou política explícita de cancelamento/drop.
- **ALTO RISCO:** remover a fence sem duplicar ownership reintroduziria exatamente reuse prematuro, flicker e corrupção já corrigidos.

## 2x low-FPS behavior

- **CONFIRMADO:** 20 FPS fornece pares separados por 50 ms; 22 FPS fornece aproximadamente 45,5 ms.
- **CONFIRMADO:** o único intermediate 2x representa 25 ms ou 22,7 ms após o real anterior.
- **PROVÁVEL:** câmera rápida, bordas e disocclusion em um delta real de 45–50 ms já são suficientes para erro visível, mesmo sem burst de múltiplos intermediários.
- **PROVÁVEL:** contenção/fence pode aumentar ainda mais a idade efetiva do par e a chegada tardia do generated frame.
- **CONFIRMADO:** o LSFG conhece somente ordem lógica e a fraction constante 0,5; não recebe timestamp real de captura, GPU completion ou display target.

## Selected functional correction

- **ANÁLISE CONCLUÍDA — nenhuma correção funcional implementada nesta rodada.**
- Não há bug persistente comprovado de history/order que permita correção pequena.
- A correção de backpressure sustentada exigiria multi-buffering de todo o contexto ou cancelamento temporal com timestamps; ambas ultrapassam o risco aceitável desta rodada.
- A Rodada 1 fisicamente validada foi preservada integralmente.

## Expected impact

- Nenhuma mudança de comportamento ou novo APK foi produzido.
- Próxima correção tecnicamente justificável: projetar um segundo resource set para permitir que o próximo real comece sem reutilizar recursos ainda pendentes, ou primeiro obter present/GPU timing confiável para uma política segura de stale intermediate.
- **HIPÓTESE:** multi-buffering pode reduzir o feedback loop, mas aumenta memória e queue depth e pode piorar latência se não houver controle temporal.

## Risk

- **BAIXO nesta rodada:** somente relatório foi alterado.
- **ALTO para remover waits:** risco direto de corrupção, semaphore misuse e image reuse prematuro.
- **MÉDIO/ALTO para multi-buffering:** duplica imagens/descriptors/command buffers e exige ownership rigoroso por geração.
- **MÉDIO/ALTO para drop/cancel:** precisa definir quando um intermediate é stale sem timestamps falsos e sem quebrar semáforos já submetidos.

## Part 3 Round 3 - Safe backpressure decoupling

- **CONFIRMADO:** a Rodada 1 permanece fisicamente validada e não foi modificada.
- **CONFIRMADO:** o backpressure atual protege recursos concretos ainda em uso, não apenas um contador global conservador.
- **CONFIRMADO:** double-buffering parcial da CPU não permitiria que trabalho GPU novo ultrapassasse submits antigos já ordenados na mesma queue da layer.
- **DECISÃO:** nenhuma mudança funcional foi mantida; o desacoplamento efetivo exigiria generations completas ou scheduler assíncrono, ambos fora do risco aceitável desta rodada.

## Blocking dependency root cause

O bloqueio principal está em `Swapchain::present()`:

- `renderFence.wait()` ocorre antes de regravar e resubmeter a cópia do próximo frame real;
- a fence é sinalizada somente pela última destination-copy do conjunto anterior;
- `Context::scheduleFrames()` também aguarda `cmdbufFence` antes de reutilizar recursos backend;
- `AcquireNextImageKHR(UINT64_MAX)` pode bloquear cada destination-copy por disponibilidade WSI.

- **CONFIRMADO:** em 2x, `real N+1` espera a única destination-copy de N.
- **CONFIRMADO:** em 3x/4x, espera a última destination-copy, mesmo com prontidão backend independente por intermediate da Rodada 1.
- **CONFIRMADO:** essa dependência evita regravação de command buffer em voo, resignal prematuro e overwrite de imagens ainda lidas.

## Protected resources

`renderFence` e `cmdbufFence` protegem conjuntamente:

- source image que será sobrescrita no próximo ciclo;
- destination images do backend ainda copiadas pela layer;
- render command buffer e command buffers de destination-copy, regravados por frame;
- source-ready, acquire, generated-ready e original-ready semaphores;
- descriptors pré-gravados que apontam para source/intermediate/destination images;
- imagens Mipmap/Alpha/Beta/Gamma/Delta compartilhadas pelo contexto;
- output-ready SYNC_FDs e seus payloads temporários;
- estado por imagem da swapchain e conclusão da última cópia.

- **CONFIRMADO:** remover somente a fence permitiria reutilizar pelo menos command buffers, semáforos e source/intermediate images antes da conclusão.
- **CONFIRMADO:** isso reabre os hazards que causavam flicker, blocos coloridos e corrupção.

## Real frame lifetime

- **CONFIRMADO:** cada Generate lê as duas source images reais, além das saídas do optical flow.
- **CONFIRMADO:** ambas precisam permanecer intactas até o último main pass que as referencia terminar.
- **CONFIRMADO:** o próximo real alterna para a source image mais antiga, mas essa mesma imagem ainda pode ser lida pelo conjunto anterior enquanto seus main passes não concluíram.
- **PROVÁVEL:** uma terceira source image seria o mínimo para aceitar outro real sem overwrite, mas isoladamente não resolve intermediates/descriptors compartilhados.

## Optical flow output lifetime

- **CONFIRMADO:** Mipmaps/Alpha/Beta são comuns ao par real; Gamma/Delta/Generate de cada destination consomem esses resultados.
- **CONFIRMADO:** resultados comuns precisam permanecer vivos até todos os Generate do par terminarem.
- **CONFIRMADO:** o mesmo conjunto Mipmap/Alpha/Beta é sobrescrito no pre-pass do par seguinte.
- **CONCLUSÃO:** sobrepor pares exige ao menos dois conjuntos completos de outputs comuns, descriptors e command buffers correspondentes.

## Intermediate lifetime

- **CONFIRMADO:** cada destination possui Gamma/Delta/Generate próprios, mas todos compartilham o pre-pass do contexto.
- **CONFIRMADO:** destination image precisa permanecer intacta até a destination-copy correspondente terminar.
- **CONFIRMADO:** na Rodada 1, cada copy espera somente seu Generate; isso reduz burst, mas não autoriza reciclar o conjunto antes da última copy.
- **CONFIRMADO:** QueuePresent pode permanecer pendente depois da copy, porém image-state/acquire controla a reutilização WSI separadamente.

## Multi-buffering feasibility

Double-buffering realmente sobreposto precisaria duplicar por generation:

- conjunto de source images suficiente para pares concorrentes;
- Mipmap, Alpha, Beta, Gamma e Delta temporários;
- destination images;
- descriptor sets e command buffers pré-gravados que referenciam essas imagens;
- pre-pass/output semaphores, sync FDs e completion fence;
- layer copy command buffers e semáforos que possam permanecer em voo.

- **CONFIRMADO:** shaders/pipelines imutáveis e samplers poderiam continuar compartilhados se o ownership fosse redesenhado.
- **CONFIRMADO:** selecionar generation apenas por `fidx % 2` seria inseguro; cada slot precisaria de fence/timeline completion comprovada antes de reuse.
- **CONFIRMADO:** resize/reload teria de aguardar e destruir todas as generations; hot reload atual só conhece um contexto por swapchain.
- **CLASSIFICAÇÃO:** tecnicamente possível, mas **MÉDIO/ALTO RISCO** e não mínimo.

## Memory cost on A6xx

- **CONFIRMADO:** um segundo contexto lógico duplicaria a maior parte das imagens temporárias e descriptors, não apenas alguns kilobytes de command storage.
- **PROVÁVEL:** o custo dominante seria storage-image memory e cache/bandwidth, especialmente Gamma/Delta em resoluções maiores e formatos de maior precisão.
- **PROVÁVEL:** em A6xx pequena, a memória adicional pode reduzir headroom e anular o ganho de backpressure.
- **NÃO CONFIRMADO:** tamanho físico total depende de extent, Flow Scale, Performance Mode, formato, alinhamento e alocação do driver; nenhum valor exato é afirmado sem consultar memory requirements de todas as imagens.

## Stale intermediate policy

- **CONFIRMADO:** o código atual é síncrono no intercept de present; quando o novo real entra em `present()`, os intermediários anteriores já foram submetidos.
- **CONFIRMADO:** Vulkan não oferece cancelamento seguro de command buffer já submetido.
- **CONFIRMADO:** não existem timestamps/deadlines reais para provar que um intermediate é stale.
- **CONCLUSÃO:** drop seguro só poderia ocorrer antes do submit, exigindo fila/scheduler assíncrono capaz de observar a chegada do real seguinte.
- **RISCO:** drop após acquire ou depois de consumir semáforo exigiria ainda liberar/presentar corretamente a imagem WSI e preservar todos os payloads; não é uma alteração pequena.

## Selected correction

- **ANÁLISE CONCLUÍDA — nenhuma correção funcional implementada.**
- Resultado válido **C**: a dependência é necessária para o ownership atual; removê-la com efeito real exige redesign de generations/scheduling.
- A sincronização por intermediate da Rodada 1, command-buffer reuse da Parte 2 e todas as correções de lifetime foram preservadas.
- Nenhum APK novo foi gerado.

## Synchronization proof

Para uma implementação futura ser segura, cada generation teria de provar:

1. source slot não está mais sendo lido pelo último Generate;
2. common optical-flow outputs não estão mais sendo lidos por nenhum main pass;
3. destination não está mais sendo copiada;
4. command buffers não estão pending/executable em uma geração reciclada;
5. binary semaphore payload foi consumido/exportado antes de novo signal;
6. swapchain recreation aguarda todas as generation fences;
7. descriptors e command buffers são destruídos somente depois das imagens referenciadas deixarem de estar em uso.

- **CONFIRMADO:** o design atual satisfaz essas condições por serialização.
- **NÃO CONFIRMADO:** não existe hoje uma estrutura per-generation que satisfaça as mesmas provas com overlap.

## Expected visual impact

- Nenhum efeito visual novo é esperado porque o runtime não mudou.
- **HIPÓTESE futura:** generations completas poderiam reduzir CPU blocking e permitir overlap entre devices/queues quando o driver tiver headroom.
- **LIMITAÇÃO:** submits de cópia já ordenados na mesma VkQueue não são ultrapassados apenas por multi-buffering; sem scheduler/reordenação, não há paralelismo mágico.
- **PROVÁVEL:** aumentar queue depth sem deadline pode piorar latência e stale intermediates, portanto multi-buffering sozinho não é solução suficiente.

## Regression risk

- **BAIXO nesta rodada:** somente documentação foi adicionada.
- **ALTO para remoção direta das fences:** overwrite, re-record em voo, semaphore reuse e corrupção.
- **MÉDIO/ALTO para double-buffer completo:** maior memória, descriptors/command buffers por generation e lifecycle complexo em reload/resize.
- **MÉDIO/ALTO para scheduler/drop:** necessidade de deadlines reais, tratamento de WSI adquirido e todos os caminhos de erro.
- **DECISÃO FINAL:** estabilidade das Partes 2 e 3 Rodada 1 tem prioridade sobre desacoplamento não demonstrado.

## Part 3 Round 4 - Present timing and WSI

- **CONFIRMADO:** a layer controla ordem de submissions, semáforos e chamadas `vkQueuePresentKHR`, mas não controla diretamente o vblank, SurfaceFlinger/compositor ou scanout físico.
- **CONFIRMADO:** não existe target presentation time no caminho atual.
- **PROVÁVEL:** parte do tremor residual é cadence quantizada pelo refresh, especialmente quando a taxa gerada não é divisor/múltiplo compatível do refresh.
- **DECISÃO:** nenhuma integração de timing foi implementada porque o WSI runtime efetivo não foi comprovado e a layer não possui timestamps-alvo confiáveis.

## Present path

Fluxo confirmado por intermediate:

`Generate backend → output-ready SYNC_FD → destination copy da layer → generatedReady semaphore → vkQueuePresentKHR → WSI/compositor → vblank/display`

- **CONFIRMADO:** 2x chama um present intermediate e depois o present real.
- **CONFIRMADO:** 3x chama os intermediates 1/3 e 2/3 nessa ordem, depois o real.
- **CONFIRMADO:** 4x chama 1/4, 2/4 e 3/4 nessa ordem, depois o real.
- **CONFIRMADO:** a Rodada 1 permite que cada copy espere seu Generate correspondente, mas todas as chamadas `QueuePresentKHR` são feitas sem deadline/target time.
- **CONFIRMADO:** o WSI só recebe ordem e wait semaphore; disponibilidade real do semaphore determina quando cada request pode progredir.

## MAILBOX semantics

- **CONFIRMADO pela especificação Vulkan:** MAILBOX usa uma fila interna de uma entrada; um novo request substitui o pending request quando a entrada está ocupada, e um request é consumido por vblank quando a fila não está vazia.
- **CONFIRMADO:** um intermediate substituído pode nunca chegar fisicamente ao display, embora seu present tenha sido submetido corretamente.
- **PROVÁVEL:** replacement pode ser não uniforme quando conclusão GPU/WSI e vblank mudam de fase.
- **CONFIRMADO:** submitted FPS acima do refresh torna replacement inevitável em regime sustentado; abaixo do refresh ainda pode haver replacement local se requests chegarem em rajadas.
- Fonte normativa: Khronos, `VkPresentModeKHR` e especificação Vulkan.

## FIFO fallback

- **CONFIRMADO pela especificação:** FIFO anexa requests a uma fila e remove um por vblank; é o único modo obrigatório.
- **CONFIRMADO:** FIFO evita replacement do MAILBOX, mas não cria target time por frame e pode aumentar queueing/latência quando produção excede refresh.
- **PROVÁVEL:** FIFO pode produzir cadence mais previsível à custa de backpressure e latência.
- **DECISÃO:** não houve troca de modo; MAILBOX suportado e fallback FIFO permanecem exatamente iguais.

## Refresh-rate mismatch

| Cenário | Submitted | 60 Hz | 90 Hz |
|---|---:|---|---|
| 20 FPS ×2 | 40 FPS | abaixo | abaixo |
| 20 FPS ×3 | 60 FPS | aproximadamente igual | abaixo |
| 20 FPS ×4 | 80 FPS | acima | abaixo |
| 25 FPS ×4 | 100 FPS | acima | acima |
| 27,5 FPS ×4 | 110 FPS | acima | acima |

- **CONFIRMADO:** overflow explica replacement forte em 4x/60 Hz e nos casos 100–110 FPS, mas não explica sozinho o tremor 2x a 40–45 FPS.
- **PROVÁVEL:** abaixo do refresh, o problema passa a ser fase/quantização desigual e arrival jitter, não saturação média.

## Fraction vs physical display time

- **CONFIRMADO:** shader fraction representa posição visual entre dois frames reais, não instante físico de scanout.
- **CONFIRMADO:** para 20 FPS, delta real é 50 ms; espaçamentos ideais são 25 ms (2x), 16,67 ms (3x) e 12,5 ms (4x).
- **CONFIRMADO:** períodos do display são 16,67 ms em 60 Hz, 11,11 ms em 90 Hz e 8,33 ms em 120 Hz.
- **CONFIRMADO:** 2x/20 FPS em 60 Hz não encaixa em ticks inteiros; 25 ms tende a quantizar em padrão alternado de aproximadamente 16,67/33,33 ms. Isso é uma forma de 3:2 cadence.
- **CONFIRMADO:** 3x/20 FPS encaixa matematicamente em 60 Hz, mas não em 90 Hz (16,67 ms = 1,5 ticks).
- **CONFIRMADO:** 4x/20 FPS pede 12,5 ms: excede 60 Hz e não encaixa exatamente em 90 Hz.
- **PROVÁVEL:** frames visualmente corretos podem parecer tremer quando sua duração física alterna entre diferentes quantidades de vblanks.
- **CONFIRMADO:** a layer não usa delta real; usa somente sequence e fraction fixa. Jitter 45/52/43/55 ms não altera a fraction nem fornece deadline ao WSI.

## Runtime present-timing extensions

- **CONFIRMADO no source local:** `VK_KHR_present_id`, `VK_KHR_present_wait` e `VK_GOOGLE_display_timing` não são negociados nem usados pelo lsfg-vk atual.
- **CONFIRMADO em documentação Mesa:** Turnip ganhou `VK_KHR_present_wait` no Mesa 23.0; isso indica implementação no driver, mas não prova exposição na combinação Turnip 24.1 + loader + WSI guest usada no aparelho.
- **CONFIRMADO pela especificação:** `present_wait` depende de `present_id` e espera conclusão de um present; ele ajuda a limitar outstanding presents, não agenda um desired time.
- **CONFIRMADO em documentação AOSP:** Android 10 implementa `VK_GOOGLE_display_timing` na implementação WSI de `libvulkan.so` para Vulkan; isso não prova que a superfície guest/glibc do Winlator passa por esse mesmo WSI Android exposto à layer.
- **NÃO CONFIRMADO:** lista runtime do Moto G34/Turnip/WineVulkan para a superfície efetiva. Headers e release notes não substituem `vkEnumerateDeviceExtensionProperties` no aparelho.
- **CONFIRMADO:** `VK_GOOGLE_display_timing` oferece refresh duration, desired present time e histórico de actual present, quando realmente suportado pela swapchain/WSI.
- **LIMITAÇÃO:** mesmo com a extensão, a layer precisaria construir targets monotônicos futuros; ela não possui timestamp de captura ou target original, e os intermediários só podem ser gerados após o frame real seguinte existir.

## Android WSI limitations

- **CONFIRMADO:** SurfaceFlinger/compositor decide composição e scanout; a implicit layer não controla diretamente o refresh físico.
- **CONFIRMADO:** wait semaphores expressam prontidão, não um horário de display.
- **PROVÁVEL:** o caminho guest Vulkan/Turnip do Winlator pode atravessar uma WSI diferente da API Android nativa documentada pelo AOSP.
- **NÃO CONFIRMADO:** suporte do compositor a desired timing através de toda a cadeia guest→loader→Turnip→Android sem enumeração e teste runtime mínimo.

## 2x cadence analysis

- **CONFIRMADO:** 20–22 FPS ×2 produz aproximadamente 40–44 FPS, abaixo de 60/90 Hz; overflow não é requisito para o tremor observado.
- **PROVÁVEL:** em 60 Hz, 40 FPS tende a alternar duração de um e dois refresh ticks; em 90 Hz, 40–44 FPS também não mapeia uniformemente para ticks.
- **PROVÁVEL:** jitter dos frames reais muda a fase de chegada e torna esse padrão ainda menos regular.
- **PROVÁVEL:** isso se soma ao erro de optical flow causado pelo intervalo real de 45–50 ms.

## 3x/4x refresh overflow

- **CONFIRMADO:** 3x/20 FPS casa com 60 Hz apenas no caso ideal e estável; qualquer custo/jitter quebra o alinhamento.
- **CONFIRMADO:** 4x/20 FPS produz 80 requests para 60 Hz, logo nem todos podem ser exibidos.
- **CONFIRMADO:** 4x a 100/110 submitted excede tanto 60 quanto 90 Hz; MAILBOX necessariamente substitui parte dos pending requests em regime sustentado.
- **PROVÁVEL:** a Rodada 1 reduz agrupamento antes do WSI, mas não evita replacement ou quantização pelo refresh.

## Selected correction

- **ANÁLISE CONCLUÍDA — nenhuma correção funcional implementada.**
- Resultado **C/D**: não foi comprovado um mecanismo runtime de scheduling aplicável e o sequencing atual é correto dentro das APIs usadas.
- Adicionar `present_wait` introduziria espera/backpressure, não target scheduling.
- Adicionar `desiredPresentTime` sem suporte runtime e sem relógio-alvo confiável seria um workaround especulativo e poderia aumentar latência.

## Capability fallback

- Uma implementação futura teria de enumerar extensão por dispositivo e confirmar suporte da superfície/WSI antes de encadear qualquer struct de timing.
- Fallback obrigatório seria o caminho atual sem alteração.
- **NÃO IMPLEMENTADO:** nenhuma feature foi habilitada por nome de GPU, Android ou jogo.

## Expected visual effect

- Nenhum efeito novo é esperado porque não houve alteração runtime nem APK novo.
- **PROVÁVEL:** o tremor residual 2x combina cadence de refresh não uniforme, arrival jitter e erro óptico de baixo FPS.
- **PROVÁVEL:** overflow/MAILBOX passa a ter peso maior em 4x quando submitted rate excede refresh.
- **NÃO CONFIRMADO:** proporção exata entre cadence física e erro de imagem sem actual-present timing do aparelho.

## Regression risk

- **BAIXO nesta rodada:** somente relatório.
- **MÉDIO/ALTO para timing especulativo:** pNext lifetime/compatibilidade, relógio errado, targets no passado e aumento de latência.
- **MÉDIO para present_wait:** risco de reintroduzir backpressure e bloquear o hot path, contrariando o objetivo.
- **DECISÃO FINAL:** preservar MAILBOX, sincronização da Rodada 1, fractions e comportamento validado.

## Part 3 Round 5 - Low FPS optical flow quality

- **CONFIRMADO:** o host expõe somente Flow Scale e Performance Mode como controles de qualidade do campo de movimento; FP16 é controle de precisão/performance documentado como sem impacto visual.
- **CONFIRMADO:** não existe parâmetro público de motion range, search radius, confidence, occlusion, hole filling ou fallback.
- **PROVÁVEL:** o tremor restante em 20–22 FPS combina grande deslocamento entre frames, baixa resolução de flow em 0.20 e limites internos do modelo proprietário.
- **DECISÃO:** nenhum parâmetro foi alterado sem semântica comprovada; nenhuma build foi produzida.

## Optical flow pipeline

Pipeline confirmado:

1. duas source images reais, full-resolution RGBA8 ou RGBA16F em HDR;
2. Mipmaps cria sete níveis R8 a partir de `flowExtent`;
3. Alpha0/Alpha1 executam preprocessamento multiescala e temporal;
4. Beta0/Beta1 produzem outputs comuns ao par real, incluindo seis níveis R8;
5. para cada fraction, Gamma0/Gamma1 e Delta0/Delta1 consomem o resultado comum e produzem campos/máscaras intermediários;
6. Generate lê previous/current full-resolution, um output Gamma RGBA16F, dois outputs Delta RGBA16F e grava a destination final.

- **CONFIRMADO:** Mipmaps/Alpha/Beta executam uma vez por par real.
- **CONFIRMADO:** Gamma/Delta/Generate executam separadamente por intermediate/fraction.
- **CONFIRMADO:** generated destination nunca volta ao começo do pipeline.

## Motion magnitude

- **CONFIRMADO no código host:** não existe clamp, normalization, radius ou limite explícito aplicado aos motion fields antes do shader.
- **CONFIRMADO:** o host fornece `resolutionInvScale`, fraction (`timestamp`) e `uiThreshold`; os campos `inputOffset`, `firstIter`, `firstIterS`, `advancedColorKind` e `hdrSupport` permanecem zero na configuração atual.
- **NÃO CONFIRMADO:** magnitude máxima representável, search range e saturação estão dentro dos shaders proprietários e não podem ser determinados pelo source C++ disponível.
- **NÃO CONFIRMADO:** alterar os campos atualmente zerados seria seguro; não há documentação/interface ativa que lhes atribua controle de qualidade.

## Temporal distance

| FPS real | Delta entre reais | Distância 2x até o intermediate |
|---:|---:|---:|
| 30 | 33,3 ms | 16,7 ms |
| 25 | 40,0 ms | 20,0 ms |
| 22 | 45,5 ms | 22,7 ms |
| 20 | 50,0 ms | 25,0 ms |
| 15 | 66,7 ms | 33,3 ms |

- **CONFIRMADO:** o shader recebe fraction normalizada, não o delta em milissegundos.
- **CONFIRMADO:** 0.5 significa metade do deslocamento estimado independentemente de o par representar 33 ou 67 ms.
- **PROVÁVEL:** o modelo foi treinado/otimizado para uma faixa prática de deslocamentos; movimento espacial maior pode exceder sua capacidade mesmo sem clamp host explícito.
- **NÃO CONFIRMADO:** faixa temporal assumida pelo modelo proprietário.

## Camera-motion behavior

- **PROVÁVEL:** rotação de câmera cria flow global de grande magnitude e move quase todas as bordas simultaneamente.
- **PROVÁVEL:** regiões entram e saem da tela, foreground/background cruzam bordas e disocclusions aumentam rapidamente com delta de 45–50 ms.
- **CONFIRMADO por observação:** ETS2 melhora muito quando FPS-base sobe, compatível com menor deslocamento espacial por par.
- **CONFIRMADO por contraste:** Tomb Raider/NFS mostram que A6xx e o port não falham universalmente; conteúdo, movimento e composição da cena importam.

## Disocclusion handling

- **CONFIRMADO na interface:** Gamma/Delta recebem vários sampled inputs, samplers com border black/white/edge e produzem três imagens RGBA16F finais para Generate.
- **NÃO CONFIRMADO:** quais dessas imagens codificam forward/backward flow, occlusion, confidence ou outro dado; nomes das classes não provam semântica interna do SPIR-V.
- **NÃO CONFIRMADO:** existência de hole filling, foreground/background separation ou blend fallback explícito dentro dos shaders.
- **CONFIRMADO:** não existe lógica C++ externa para detectar disocclusion ou substituir pixels ruins.

## Confidence/validity data

- **CONFIRMADO:** não há texture, binding ou parâmetro nomeado `confidence`, `validity`, `occlusion` ou `error` no source host.
- **CONFIRMADO:** `uiThreshold=0.5` é enviado ao constant buffer, mas o host não documenta seu uso e não o relaciona a confidence de movimento.
- **NÃO CONFIRMADO:** outputs Gamma/Delta podem conter máscaras internas, porém sua interpretação é proprietária.
- **DECISÃO:** `uiThreshold` não foi reaproveitado como quality/confidence knob sem evidência.

## Flow resolution impact

Para 1280×720:

- 0.20 → flowExtent aproximado 256×144;
- 0.50 → 640×360;
- 0.80 → 1024×576.

- **CONFIRMADO:** os sete Mipmaps R8 e todos os estágios Alpha/Beta/Gamma/Delta derivados usam flowExtent e seus níveis reduzidos.
- **CONFIRMADO:** em 0.20, os níveis R8 iniciam aproximadamente em 256×144, 128×72, 64×36, 32×18, 16×9, 8×4 e 4×2.
- **PROVÁVEL:** 0.20 reduz precisão espacial de bordas finas e objetos pequenos; upscale/Generate full-resolution não recupera detalhes ausentes do campo.
- **CONFIRMADO por teste:** apesar disso, 0.20 melhora muito headroom e mostrou pequena diferença visual relativa no ETS2, indicando que custo/temporal distance dominam no aparelho testado.

## Performance Mode behavior

- **CONFIRMADO:** Performance Mode seleciona variantes SPIR-V proprietárias diferentes para Alpha/Beta/Gamma/Delta usando o offset de recursos de performance.
- **CONFIRMADO:** reduz `m` de 2 para 1 em Alpha0, Alpha1, Gamma1, Delta0 e Delta1, diminuindo número de imagens/canais temporários.
- **CONFIRMADO:** mantém a estrutura e quantidade geral de passes, mas cada shader variante possui menos sampled/storage bindings em vários estágios.
- **CONFIRMADO:** Generate usa o mesmo shader ID base em quality e performance; Performance Mode altera a produção dos seus inputs, não o dispatch final.
- **PROVÁVEL:** desligar Performance Mode pode melhorar qualidade do campo/disocclusion, mas aumenta compute, bandwidth e memória e pode reduzir FPS-base, piorando o delta temporal em A6xx limitada.
- **DECISÃO:** não foi alterado automaticamente; o controle já existe e é hot-reloadable pelo usuário.

## Shader interface parameters

Constant buffer confirmado:

- `resolutionInvScale`: conversão/escala associada ao flow;
- `timestamp`: fraction por destination;
- `uiThreshold`: fixo em 0.5;
- demais campos: zero na construção padrão.

Descriptors confirmados do Generate:

- previous e current real full-resolution, alternados por `fidx`;
- output final Gamma;
- dois outputs finais Delta;
- destination storage full-resolution;
- samplers border-black e clamp-to-edge;
- constant buffer específico da fraction.

- **CONFIRMADO:** 2x/3x/4x reutilizam o flow comum, mas cada intermediate possui Gamma/Delta/Generate próprios e seu constant buffer com 1/2; 1/3,2/3; ou 1/4,2/4,3/4.

## Safe quality controls

Classificação dos parâmetros existentes:

- `flow_scale`: **CONFIRMADO ATIVO**, trade-off direto de resolução/custo;
- `performance_mode`: **CONFIRMADO ATIVO**, troca modelo quality/performance;
- `allow_fp16`: **CONFIRMADO ATIVO**, capability-gated, documentado sem mudança de qualidade;
- `multiplier`: **CONFIRMADO ATIVO**, quantidade/fractions;
- `pacing=none`: **CONFIRMADO ATIVO**, único modo implementado;
- `uiThreshold`: **EXISTE MAS NÃO É CONTROLE EXPOSTO**, sem semântica de confidence comprovada;
- campos restantes do constant buffer: **EXISTEM MAS NÃO SÃO CONFIGURADOS**;
- motion/confidence/disocclusion/filter quality extras: **NÃO APLICÁVEL no host atual**.

- **CONCLUSÃO:** a única alavanca de qualidade adicional já suportada é usar Quality Mode (`performance_mode=false`), mas ela já é opção explícita e seu custo pode ser contraproducente em low-FPS A6xx.

## Selected correction

- **ANÁLISE CONCLUÍDA — nenhuma correção funcional implementada.**
- Resultado **C/D**: o pipeline host utiliza corretamente os controles disponíveis; os controles restantes estão encapsulados no algoritmo proprietário.
- Não foi alterado Flow Scale, Performance Mode, FP16, constant buffer, descriptor ou shader.
- Nenhum APK novo foi gerado.

## Expected impact

- Nenhuma mudança visual nesta rodada porque o runtime foi preservado.
- **PROVÁVEL:** Quality Mode pode reduzir alguns erros, mas pode também aumentar contenção e piorar o FPS-base; deve continuar sendo escolha explícita, não correção global.
- **PROVÁVEL:** o próximo avanço real exige Parte 4 focada na semântica/quality path do algoritmo, com método de validação controlado antes de qualquer alteração.

## Performance cost

- **CONFIRMADO:** aumentar Flow Scale aumenta pixels em toda a pirâmide e nos passes derivados.
- **CONFIRMADO:** Quality Mode duplica vários grupos temporários (`m=2` versus `m=1`) e usa variantes com mais bindings/canais.
- **PROVÁVEL:** ambos elevam compute e bandwidth e podem acionar o feedback loop que reduz FPS-base.
- **NÃO CONFIRMADO:** custo percentual no Adreno 619 ou em outras A6xx sem GPU profiler.

## Regression risk

- **BAIXO nesta rodada:** somente relatório.
- **ALTO para alterar constants sem semântica:** risco de quebrar flow scaling, UI handling ou comportamento interno do modelo.
- **ALTO para inferir confidence pelos outputs:** formatos/bindings não provam significado.
- **MÉDIO para forçar Quality Mode:** comportamento suportado, mas custo pode causar regressão de performance e piorar artefatos temporais.
- **DECISÃO FINAL:** preservar o baseline e não inventar uma quality knob inexistente.

## Part 3 Round 6 - Final stabilization

- **CONFIRMADO:** a única mudança runtime da Parte 3 é a prontidão cross-device independente por intermediate introduzida na Rodada 1 e fisicamente validada.
- **CONFIRMADO:** Rodadas 2–5 alteraram somente este relatório; não modificaram código, biblioteca ou APK.
- **CONFIRMADO:** nenhuma nova otimização, política de pacing ou mudança no quality path foi introduzida nesta estabilização.
- **DECISÃO:** Parte 3 encerrada sobre a build fisicamente validada da Rodada 1.

## Functional diff audit

Classificação do working tree acumulado:

### FUNCIONAL

- `tools/lsfg-vk-glibc/compatibility.patch`: source reproduzível do LSFG glibc, incluindo otimizações estáveis da Parte 2 e a prontidão por intermediate da Parte 3 Rodada 1;
- `app/app/src/main/assets/lsfg-vk/liblsfg-vk.so`: build artifact ARM64 correspondente ao patch atual e fisicamente validado na Rodada 1.

### DOCUMENTAÇÃO

- `LSFG_ANALYSIS_REPORT.md`: análise acumulada das Partes 1–3.

### LIMPEZA DE DIAGNÓSTICO ANTERIOR

- `XServerDisplayActivity.java`, `PerfHudView.java` e `GuestProgramLauncherComponent.java`: remoção da instrumentação pesada cancelada;
- `LSFGDiagnostic.java`: removido porque existia somente para diagnóstico temporário.

### TEMPORÁRIO

- **CONFIRMADO:** nenhum source/build/test temporário novo está versionado; diretórios de build permanecem ignorados/locais.

- **CONFIRMADO:** além da Rodada 1, não existe outro delta funcional originado na Parte 3.

## Round 1 synchronization validation

- **CONFIRMADO:** quantidade de output-ready semaphores é igual a `destinationImages.size()` / `multiplier - 1`.
- **CONFIRMADO:** 2x cria uma destination, um semaphore exportável, um SYNC_FD e uma copy; comportamento é equivalente ao fluxo correto anterior de semaphore único.
- **CONFIRMADO:** 3x cria dois pares readiness/destination; 4x cria três.
- **CONFIRMADO:** cada main pass sinaliza somente `outputReadySemaphores[i]` depois do seu Gamma/Delta/Generate.
- **CONFIRMADO:** layer valida a contagem de FDs, importa cada payload no semaphore correspondente e cada destination-copy espera `acquireSemaphore[i]` mais `outputReadySemaphore[i]`.
- **CONFIRMADO:** nenhum intermediate pode ser copiado antes do Generate correspondente.
- **CONFIRMADO:** `cmdbufFence` permanece no último main submit; `renderFence` e `originalReady` permanecem na última destination-copy.
- **CONFIRMADO:** ordem de QueuePresent continua intermediate 1..N, depois real.

## Lifetime validation

- **CONFIRMADO:** cada output-ready semaphore backend e layer vive durante todo o contexto e recebe exatamente um signal/export/import/wait por ciclo.
- **CONFIRMADO:** SYNC_FD usa payload temporário; FDs não são armazenados ou reciclados como identificadores persistentes.
- **CONFIRMADO:** fence do backend impede reutilizar pre-pass/main resources antes da conclusão do último main pass.
- **CONFIRMADO:** fence da layer impede regravar copy command buffers, resinalizar semáforos e sobrescrever source/destination antes da última copy.
- **CONFIRMADO:** descriptors e command buffers pré-gravados continuam presos às imagens do mesmo contexto; a Rodada 1 não troca handles nem layouts.
- **CONFIRMADO:** image-state tracking de acquire/present permanece intacto.
- **CONFIRMADO:** não foi encontrado caminho de use-after-free, stale descriptor, premature fence reset ou semaphore reuse introduzido pela Rodada 1.

## Hot reload validation

- **CONFIRMADO:** multiplier, Flow Scale e Performance Mode atualizam perfil e forçam `VK_ERROR_OUT_OF_DATE_KHR`/recriação de swapchain.
- **CONFIRMADO:** reload executa `DeviceWaitIdle`, remove cada Swapchain context e só então cria novos semaphores/destinations/descriptors/command buffers.
- **CONFIRMADO:** resolução, fullscreen e substituição de oldSwapchain seguem remoção/destruição do contexto anterior.
- **CONFIRMADO:** backend `closeContext()` executa `DeviceWaitIdle` antes de apagar output semaphores e demais recursos.
- **CONFIRMADO:** 1x não cria contexto framegen; reativar 2x/3x/4x recria o conjunto correspondente.
- **CONFIRMADO:** nenhum recurso da Rodada 1 sobrevive indevidamente a resize, reload ou mudança de multiplier.

## Residual artifact classification

### CONFIRMADO

- artefato/tremor aumenta quando FPS-base cai;
- qualidade melhora muito quando FPS-base sobe;
- Rodada 1 reduziu pequena parte do tremor/cadence;
- 2x também apresenta tremor em aproximadamente 20–22 FPS;
- real-frame history e previous/current estão corretos;
- generated frames não alimentam o optical flow seguinte;
- present sequencing está correto dentro das APIs usadas;
- Flow Scale 0.20 reduz resolução do campo e melhora muito headroom.

### PROVÁVEL

- grande motion delta entre frames reais é a causa dominante;
- camera motion, disocclusion e bordas amplificam erros;
- resolução de flow baixa reduz precisão espacial;
- GPU contention e refresh cadence contribuem secundariamente.

### HIPÓTESE

- Gamma/Delta podem conter confidence/validity/occlusion masks internas não expostas;
- Performance Mode pode alterar de forma relevante a robustez em movimento grande além da redução de custo já confirmada.

### NÃO CONFIRMADO

- existência de quality knob oculto seguro;
- semântica real de `uiThreshold` para o algoritmo;
- possibilidade de reduzir artefato sem investigar o quality path proprietário;
- contribuição quantitativa de optical flow versus cadence/GPU contention.

## Closed investigation paths

Não reabrir sem nova evidência:

- remover `renderFence` ou `cmdbufFence`;
- multi-buffering sem redesign completo de ownership/scheduling;
- cancelamento de command buffer ou stale intermediate já submetido;
- sleep, limiter ou busy-wait pacing;
- usar `present_wait` como scheduler;
- hacks de MAILBOX/FIFO;
- auto Flow Scale/multiplier/Performance Mode;
- alterar `uiThreshold`, campos zerados ou descriptors por tentativa;
- novas micro-otimizações de allocation/command recording sem relação visual.

## Part 4 Handoff - Quality Path

Prioridade exata da próxima etapa:

1. identificar a interface real dos shaders proprietários sem modificá-los;
2. determinar semântica dos outputs Gamma e Delta;
3. identificar possíveis masks de confidence, validity ou occlusion;
4. determinar o papel real de `uiThreshold=0.5`;
5. mapear comportamento interno em disocclusion/hole regions;
6. determinar motion range/magnitude em pares low-FPS;
7. comparar controladamente Performance Mode ON versus OFF;
8. medir impacto de Flow Scale na precisão de bordas, não somente FPS;
9. verificar se já existe fallback visual interno para regiões inválidas;
10. comparar qualidade por fraction: 0.5; 1/3,2/3; 1/4,1/2,3/4.

Variáveis controladas sugeridas:

- mesmo jogo/cena/câmera e resolução;
- 2x primeiro, por possuir apenas fraction 0.5;
- Flow Scale 0.20 versus valor mais alto;
- Performance Mode ON versus OFF;
- registrar qualidade visual e FPS-base, sem usar submitted HUD como display timing.

Regra explícita para a Parte 4:

- começar por análise de interface/reflection e comportamento;
- **não** começar com patch binário, edição SPIR-V, mudança aleatória de constants ou descriptor swapping;
- qualquer futura alteração deve ter semântica provada, fallback e teste A/B isolado.

## Final risk assessment

- **BAIXO:** Parte 3 final não altera runtime; somente consolida relatório.
- **CONFIRMADO:** a mudança funcional da Rodada 1 passou por teste físico sem regressão no Tomb Raider e com pequena melhora no ETS2.
- **RISCO RESIDUAL conhecido:** SYNC_FD por destination aumenta o número de semáforos/FDs em 3x/4x, mas lifetime e completion estão protegidos por contexto/fences e foram fisicamente exercitados.
- **MÉDIO/ALTO:** qualquer avanço da Parte 4 que toque constants/descriptors/shaders sem semântica comprovada.
- **DECISÃO FINAL:** preservar exatamente a build da Parte 3 Rodada 1 como base para a investigação de quality path.

## Part 4 Round 1 - Quality Path Reconstruction

- **CONFIRMADO:** esta rodada reconstruiu a interface host-side sem alterar runtime, SPIR-V, configuração, sincronização ou assets.
- **CONFIRMADO:** o pipeline executado por par real é `Mipmaps -> Alpha0/Alpha1 (7 níveis) -> Beta0/Beta1 -> [Gamma0/Gamma1 (7 níveis) -> Delta0/Delta1 (3 níveis) -> Generate] por intermediate`.
- **CONFIRMADO:** Mipmaps/Alpha/Beta constituem o pre-pass compartilhado; Gamma/Delta/Generate são instanciados por destination/fraction.
- **CONFIRMADO:** os SPIR-V não estão armazenados no projeto: são recursos `RT_RCDATA` extraídos em runtime da `Lossless.dll` externa. A DLL não está presente no workspace, portanto não foi possível executar `spirv-dis`/`spirv-val` sobre os bytes nesta rodada.
- **CONFIRMADO:** a interface Vulkan declarada pelo host, os IDs lógicos, bindings, recursos, formatos e extents puderam ser reconstruídos integralmente pelo source.

## Proprietary shader inventory

Todos são compute shaders com entry point `main`, um descriptor set e nenhum push constant ou specialization constant criado pelo host.

| Grupo | IDs lógicos | Variantes | Uso host-side |
|---|---:|---|---|
| Mipmaps | 255 | FP16/FP32; somente quality resource | source real -> pirâmide luminance/grayscale R8 |
| Generate | 256 | FP16/FP32; SDR/HDR é patch apenas do storage format | dois frames reais + três campos refinados -> destination final |
| Gamma/Delta base | 257, 258 | quality/performance; FP16/FP32 | primeiros estágios Gamma0/Delta0 |
| Gamma restante | 259–262 | quality/performance; FP16/FP32 | Gamma1, quatro passes |
| Delta restante | 263–266 e 271–274 | quality/performance; FP16/FP32 | Delta1 e segundo ramo Delta0/Delta1 |
| Alpha | 267–270 | quality/performance; FP16/FP32 | extração/refino piramidal em quatro shaders |
| Beta | 275–279 | quality/performance; FP16/FP32 | combinação temporal e pirâmide R8 em cinco shaders |

- **CONFIRMADO:** seleção de recurso usa offset base 49, `+23` para Performance e `+49` para FP32. Generate usa sempre a família não-Performance.
- **CONFIRMADO:** FP16 só é selecionado quando permitido e `shaderFloat16` é suportado; caso contrário são usados os recursos FP32.
- **DESCONHECIDO:** nomes internos, variáveis, operações e semântica matemática de cada recurso proprietário sem reflexão dos SPIR-V reais.

## Shader reflection

- **CONFIRMADO:** `vk::Shader` declara bindings de forma determinística: uniform buffers em `0..`, samplers em `16..`, sampled images em `32..` e storage images em `48..`.
- **CONFIRMADO:** não há push-constant range nem `VkSpecializationInfo` no pipeline layout/compute pipeline.
- **CONFIRMADO:** o único uniform layout host-side é `ConstantBuffer`, com 48 bytes: `inputOffset[2]`, `firstIter`, `firstIterS`, `advancedColorKind`, `hdrSupport`, `resolutionInvScale`, `timestamp`, `uiThreshold` e padding.
- **CONFIRMADO:** o host fornece contagens exatas por shader no registry; a tabela de descriptors abaixo deriva dessas contagens e da ordem dos builders.
- **DESCONHECIDO:** decorations, nomes e tipos numéricos internos dos SPIR-V. `spirv-dis`, `spirv-val` e `spirv-cross` não estão instalados e, principalmente, não há `Lossless.dll`/SPIR-V no workspace para analisar.
- **DECISÃO:** não baixar ferramenta nem copiar/distribuir a DLL; a reflexão binária fica condicionada a uma DLL fornecida localmente pelo usuário em uma rodada futura.

## Alpha path

- **CONFIRMADO:** há sete instâncias, uma para cada mip R8 do par real selecionado.
- **CONFIRMADO:** Alpha0 reduz cada nível primeiro a `ceil(extent/2)` e depois a `ceil(extent/4)`. As temporárias e outputs usam o default `RGBA8_UNORM`.
- **CONFIRMADO:** Quality usa `m=2`: 2+2 temporárias e 4 outputs; Performance usa `m=1`: 1+1 temporárias e 2 outputs.
- **CONFIRMADO:** Alpha1 consome os outputs Alpha0 e produz histórico temporal circular: três sets no nível 0 e dois nos demais níveis. O número de imagens por set permanece `2*m`.
- **CONFIRMADO:** Alpha não recebe constant buffer; portanto não recebe `timestamp`, `uiThreshold` ou `resolutionInvScale` diretamente.
- **DESCONHECIDO:** significado dos canais Alpha e se representam features, gradients ou outra codificação.

## Beta path

- **CONFIRMADO:** Beta0 usa três grupos temporais Alpha1 (anterior-anterior, anterior e atual pela seleção modular), combina `6*m` sampled images e escreve duas imagens `RGBA8_UNORM` na resolução `flowExtent/4`.
- **CONFIRMADO:** Beta1 aplica três passes temporários e um pass final; produz seis imagens `R8_UNORM` com extents `flowExtent/4`, `/8`, `/16`, `/32`, `/64` e `/128`.
- **CONFIRMADO:** somente o pass final Beta1 recebe o constant buffer global, cujo `timestamp=0.5` e `uiThreshold=0.5` são fixos.
- **PROVÁVEL:** Beta constrói dados temporais/piramidais compartilhados para o refinement posterior.
- **DESCONHECIDO:** se seus canais codificam erro, motion, validity ou outra feature.

## Gamma path

- **CONFIRMADO:** para cada intermediate existem sete níveis Gamma, executados do nível mais grosseiro ao mais fino.
- **CONFIRMADO:** Gamma0 recebe dois grupos Alpha1 temporalmente selecionados, o resultado Gamma anterior (ou black no primeiro nível), dois samplers e o constant buffer da fraction; escreve três imagens `RGBA8_UNORM`.
- **CONFIRMADO:** Gamma1 executa quatro shaders; usa temporárias `RGBA8_UNORM` e produz um campo final `RGBA16_SFLOAT` por nível.
- **CONFIRMADO:** no pass final Gamma1 entram o Gamma anterior/black e o nível Beta correspondente; o nível mais fino é aproximadamente `flowExtent/4`.
- **CONFIRMADO:** o Gamma final mais fino é consumido diretamente por Generate e também alimenta Delta nos níveis especiais.
- **PROVÁVEL:** Gamma é um campo de reconstrução/refinement dependente da fraction.
- **DESCONHECIDO:** se os quatro canais representam motion bidirecional, confidence, weights ou combinação desses conceitos.

## Delta path

- **CONFIRMADO:** Delta existe apenas nos três níveis mais finos do loop (`flowExtent/16`, `/8`, `/4`).
- **CONFIRMADO:** Delta0 possui dois ramos. O primeiro escreve três `RGBA8_UNORM`; o segundo escreve `m` imagens `RGBA8_UNORM`. Ambos recebem a fraction, Gamma e/ou Delta anterior.
- **CONFIRMADO:** Delta1 processa os dois ramos em quatro shaders cada e produz dois campos finais `RGBA16_SFLOAT` por nível.
- **CONFIRMADO:** no nível mais fino, os dois outputs Delta e o output Gamma são as três imagens auxiliares amostradas por Generate.
- **PROVÁVEL:** Delta refina informação adicional que Gamma sozinho não fornece, possivelmente em duas direções ou duas classes de reconstruction.
- **DESCONHECIDO:** qualquer interpretação como confidence/occlusion/validity; o número de outputs e o formato não são prova semântica.

## Descriptor interface

Convenção de bindings: `UBO=0`, samplers a partir de `16`, sampled images a partir de `32`, storage images a partir de `48`.

| Pass | Sampled | Storage | UBO | Samplers | Recursos conhecidos |
|---|---:|---:|---:|---:|---|
| Mipmaps 255 | 1 | 7 | 1 | 1 | source real; 7 mips R8; constantes globais |
| Alpha 267 | 1 | `m` | 0 | 1 | mip -> temp0 |
| Alpha 268 | `m` | `m` | 0 | 1 | temp0 -> temp1 |
| Alpha 269 | `m` | `2m` | 0 | 1 | temp1 -> Alpha0 outputs |
| Alpha 270 | `2m` | `2m` | 0 | 1 | Alpha0 -> Alpha1 temporal |
| Beta 275 | `6m` | 2 | 0 | 1 | três grupos Alpha1 -> Beta0 |
| Beta 276–278 | 2 | 2 | 0 | 1 | refinamentos temporários |
| Beta 279 | 2 | 6 | 1 | 1 | temporárias -> pirâmide Beta R8 |
| Gamma 257 | `4m+1` | 3 | 1 | 2 | dois grupos Alpha1 + prior Gamma/black |
| Gamma 259 | 3 | `2m` | 0 | 1 | Gamma0 outputs -> temp0 |
| Gamma 260–261 | `2m` | `2m` | 0 | 1 | refinamentos temporários |
| Gamma 262 | `2m+2` | 1 | 1 | 2 | temporárias + prior Gamma + Beta -> Gamma RGBA16F |
| Delta 257 | `4m+1` | 3 | 1 | 2 | Alpha1 + prior Delta/black |
| Delta 258 | `4m+2` | `m` | 1 | 2 | Alpha1 + Gamma + prior Delta |
| Delta 263 | 3 | `2m` | 0 | 1 | Delta0 outputs -> temp0 |
| Delta 264–265 | `2m` | `2m` | 0 | 1 | primeiro refinement branch |
| Delta 266 | `2m+2` | 1 | 1 | 2 | branch + prior Delta + Beta -> Delta0 RGBA16F |
| Delta 271–273 | `m` | `m` | 0 | 1 | segundo refinement branch |
| Delta 274 | `m+1` | 1 | 1 | 2 | branch + prior Delta -> Delta1 RGBA16F |
| Generate 256 | 5 | 1 | 1 | 2 | current, previous, Gamma, Delta0, Delta1 -> output |

- **CONFIRMADO:** os builders fornecem a mesma quantidade e categoria declaradas no registry nos caminhos Quality e Performance.
- **CONFIRMADO:** não foi encontrado descriptor ausente, excedente, trocado ou vinculado à fraction errada no host.

## Resource formats

| Recurso | Formato host | Observação |
|---|---|---|
| source/destination SDR | `R8G8B8A8_UNORM` | resolução final, external memory |
| source/destination HDR | `R16G16B16A16_SFLOAT` | resolução final, external memory |
| Mipmaps | `R8_UNORM` | sete níveis |
| Alpha/Beta temporários e Beta0 | `R8G8B8A8_UNORM` | formato default do wrapper |
| Beta1 outputs | `R8_UNORM` | seis níveis |
| Gamma/Delta temporários | `R8G8B8A8_UNORM` | formato default |
| Gamma1 final | `R16G16B16A16_SFLOAT` | um por nível/intermediate |
| Delta1 finais | `R16G16B16A16_SFLOAT` | dois por nível/intermediate |
| Generate output | SDR RGBA8 ou HDR RGBA16F | storage format do SPIR-V é corrigido pelo host |

- **CONFIRMADO:** não foi encontrado format mismatch host-side: image creation, views e formato declarado do Generate são coerentes.
- **DESCONHECIDO:** interpretação de cada componente dos campos RGBA16F.

## Resolution chain

Definindo `S=sourceExtent` e `F=flowExtent=S*flow_scale` após a conversão recíproca layer/backend:

1. Mipmaps: `F`, `F/2`, ..., `F/64` em R8.
2. Alpha por nível: temporárias em aproximadamente `mip/2`; outputs em `mip/4`.
3. Beta0: `F/4`; Beta1: `F/4` até `F/128`.
4. Gamma: sete níveis aproximadamente `F/256` até `F/4`; output final de cada nível é RGBA16F.
5. Delta: somente aproximadamente `F/16`, `F/8`, `F/4`; dois outputs RGBA16F.
6. Generate: dispatch em `ceil(S/16)` workgroups e output em resolução final.

- **CONFIRMADO:** Flow Scale afeta Mipmaps, Alpha, Beta, Gamma e Delta.
- **CONFIRMADO:** Generate não executa um pass explícito de upscale; ele amostra Gamma/Delta de `F/4` enquanto escreve a imagem final em `S`.
- **PROVÁVEL:** sampler clamp/filter e lógica interna do Generate fazem a reconstrução espacial/upscale dos campos.
- **CONFIRMADO:** isso explica como Flow Scale 0.20 reduz muito o custo enquanto o frame final continua full-resolution; não prova que a precisão de bordas seja preservada.

## Performance Mode differences

- **CONFIRMADO:** Performance seleciona recursos SPIR-V distintos (`+23`) para Alpha/Beta/Gamma/Delta; Mipmaps e Generate não mudam de família.
- **CONFIRMADO:** `m` cai de 2 para 1. Isso reduz pela metade vários arrays de temporárias, sampled/storage bindings e canais intermediários em Alpha, Gamma e Delta.
- **CONFIRMADO:** extents, quantidade de níveis e sequência de dispatches permanecem iguais.
- **CONFIRMADO:** Beta mantém duas temporárias e seis outputs R8, mas seus shaders recebem menos inputs provenientes do Alpha.
- **PROVÁVEL:** Performance reduz largura/riqueza das features internas, bandwidth e trabalho por invocation, não a resolução espacial nem o número de refinement levels.
- **DESCONHECIDO:** impacto exato em confidence/disocclusion e qualidade visual sem semântica/reflexão interna e teste A/B controlado.

## uiThreshold interface

- **CONFIRMADO:** `uiThreshold` é `float` no offset 32 do `ConstantBuffer` de 48 bytes e vale sempre `0.5`.
- **CONFIRMADO:** não varia com multiplier, fraction, Performance Mode, Flow Scale ou HDR.
- **CONFIRMADO:** chega a Mipmaps, Beta1 final, Gamma0, Gamma1 final, ambos Delta0 branches, ambos Delta1 finais e Generate por binding UBO 0.
- **CONFIRMADO:** Alpha e os passes intermediários sem UBO não o recebem diretamente.
- **DESCONHECIDO:** quais shaders realmente leem o campo; compartilhar o mesmo UBO não prova consumo.
- **DESCONHECIDO:** semântica algorítmica. O nome host `uiThreshold` vem da reconstrução existente, mas não demonstra confidence, UI detection ou occlusion.
- **DECISÃO:** nenhum valor foi alterado.

## Confidence evidence

- **CONFIRMADO:** não existe recurso host nomeado confidence/validity/mask nem parâmetro configurável correspondente.
- **CONFIRMADO:** existem três campos auxiliares RGBA16F fornecidos ao Generate, além dos dois frames reais, capazes de transportar múltiplos valores por pixel.
- **PROVÁVEL:** ao menos parte de motion/reconstruction está codificada nesses campos.
- **HIPÓTESE:** canais adicionais podem conter weights/confidence/validity.
- **DESCONHECIDO:** qual campo/canal possui essa informação ou se ela existe de forma explícita.

## Disocclusion evidence

- **CONFIRMADO:** Generate acessa simultaneamente current real, previous real, Gamma final e dois Delta finais, com samplers border-black e edge-clamp.
- **CONFIRMADO:** Gamma/Delta propagam dados coarse-to-fine e recebem black image quando não existe histórico de nível anterior.
- **PROVÁVEL:** a combinação bidirecional dos dois frames e três campos permite reconstruction/fallback em regiões difíceis.
- **HIPÓTESE:** Delta pode carregar informação ligada a duas direções ou oclusão.
- **DESCONHECIDO:** existência e regra exata de hole filling, disocclusion mask ou nearest-frame fallback.

## Motion representation

- **CONFIRMADO:** os campos finais oferecidos ao Generate são RGBA16F; isso oferece amplo range numérico, mas não define unidade ou normalização.
- **CONFIRMADO:** o host passa `resolutionInvScale=1/flow_scale` e não aplica motion clamp, search radius, confidence threshold ou camera compensation configurável.
- **CONFIRMADO:** não há global-motion/camera-motion detection no código host.
- **DESCONHECIDO:** motion em pixels versus coordenadas normalizadas, quantização efetiva, saturação e search range internos.
- **PROVÁVEL:** grande deslocamento low-FPS aumenta erro/ambiguidade mesmo sem saturação numérica explícita.

## Temporal inputs

- **CONFIRMADO:** o único valor temporal por intermediate é `timestamp=(index+1)/(count+1)` no constant buffer.
- **CONFIRMADO:** fractions permanecem 2x `0.5`; 3x `1/3,2/3`; 4x `1/4,1/2,3/4`.
- **CONFIRMADO:** fraction chega a Gamma0, Gamma1 final, Delta0, Delta1 final e Generate; Alpha não recebe fraction e Beta usa o buffer global fixo `0.5`.
- **CONFIRMADO:** nenhum shader recebe FPS, frame duration, delta em milissegundos, present time ou timestamp real do jogo.
- **CONFIRMADO:** para o algoritmo, um par separado por 25 ms e outro por 50 ms diferem apenas pelo conteúdo/deslocamento das imagens, não por metadata temporal.
- **PROVÁVEL:** isso torna o algoritmo incapaz de ajustar explicitamente search/confidence pela distância temporal real e reforça a limitação observada em 20–22 FPS.

## Host-side bugs found

- **CONFIRMADO:** nenhum bug host-side claro foi encontrado em descriptors, ordem, format, extent, fraction, shader-family selection ou constant-buffer selection.
- **CONFIRMADO:** Performance usa suas variantes somente em Alpha/Beta/Gamma/Delta e Generate continua comum, coerente com o registry.
- **CONFIRMADO:** `flowExtent` efetivo é `sourceExtent*flow_scale`, porque a layer envia `1/flow_scale` e o backend divide por esse valor.
- **DESCONHECIDO:** compatibilidade byte-a-byte da struct com cada SPIR-V não pode ser reflita sem os recursos externos, embora o caminho funcional e os bindings existentes sejam evidência forte de compatibilidade.
- **DECISÃO:** nenhuma correção funcional é sustentada nesta rodada.

## Safe modification candidates

- **BAIXO RISCO analítico:** obter reflexão dos recursos SPIR-V a partir de uma `Lossless.dll` fornecida localmente, sem armazenar ou distribuir o binário.
- **BAIXO RISCO experimental futuro:** A/B controlado Performance ON/OFF em 2x, mesma cena, mesmo Flow Scale e FPS-base observado; usa caminhos já suportados sem mudar defaults.
- **BAIXO RISCO experimental futuro:** comparar Flow Scale 0.20 com valor maior focando edge warping/disocclusion, não apenas submitted FPS.
- **MÉDIO RISCO:** instrumentar captura visual/GPU externa para correlacionar os três campos finais; não necessário nesta rodada.
- **ALTO RISCO:** alterar `uiThreshold`, trocar descriptors ou reinterpretar canais sem reflexão/semântica.
- **PROIBIDO sem base adicional:** patch de SPIR-V ou clone especulativo do algoritmo.

## Risk assessment

- **CONFIRMADO:** risco runtime desta rodada é zero; apenas o relatório foi alterado.
- **CONFIRMADO:** nenhuma build ou APK é necessária para validar documentação/reverse engineering estático.
- **PROVÁVEL:** a próxima eliminação rápida de caminhos errados vem da reflexão real dos recursos e de um A/B Performance ON/OFF, não de modificar constantes.
- **DESCONHECIDO:** quanto do tremor ETS2 está em motion estimation, confidence/disocclusion interna ou perda espacial causada por Flow Scale 0.20.
- **DECISÃO FINAL:** preservar integralmente o baseline e avançar somente quando a interface interna estiver sustentada por SPIR-V real ou comportamento A/B controlado.

## Part 2 Round 6 - Final stabilization

- **CONFIRMADO (auditoria estática):** as Rodadas 3, 4 e 5 preservam ordem Vulkan, descriptors, imagens, barriers, layouts, shaders, submissions, semáforos, fences e índices de interpolação do caminho anterior.
- **CONFIRMADO:** o último baseline fisicamente validado continua sendo a Rodada 3. Rodadas 4, 5 e esta candidata final ainda exigem o teste completo no aparelho.
- **CONFIRMADO:** a linha residual de estatísticas por criação de contexto da Rodada 2 foi removida. Não permanece métrica por frame, jitter/variance, shader dump, DXVK debug forçado ou Vulkan-loader debug forçado.
- **NÃO CONFIRMADO:** compilação e inspeção estática não provam equivalência física no driver Turnip.

## Round 3/4/5 combined audit

- **CONFIRMADO:** a Rodada 3 troca somente vectors temporários de waits/signals das cópias da layer por arrays e spans. Ordem e contagens de semáforos permanecem iguais.
- **CONFIRMADO:** a Rodada 4 troca somente storage temporário dos timeline submits do backend. Cada pass espera o mesmo valor do pre-pass; apenas o último sinaliza `outputReadySemaphore` e recebe `cmdbufFence`.
- **CONFIRMADO:** a Rodada 5 move a gravação Gamma0/Gamma1/Delta0/Delta1/Generate de `scheduleFrames()` para a construção do contexto, mantendo a mesma sequência de comandos para cada variante selecionada.
- **CONFIRMADO:** nenhuma rodada altera shader, dimensão ou quantidade de dispatches, barriers, layouts, present mode, acquire, external memory ou quantidade de frames gerados.

## Command buffer lifetime validation

- **CONFIRMADO:** Gamma0 e Delta0 selecionam descriptors por `frame_index % 2` ou `% 3`; Generate usa `% 2`. O período conjunto é seis, logo variantes 0..5 cobrem todas as combinações.
- **CONFIRMADO:** Gamma1 e Delta1 não têm seleção variável por frame. Pipelines, descriptors, imagens, formato, extent, Flow Scale, Performance Mode e destination index são imutáveis durante a vida do contexto.
- **CONFIRMADO:** main command buffers reutilizáveis são gravados sem `ONE_TIME_SUBMIT`; pre-pass e cópias preservam o comportamento anterior.
- **CONFIRMADO:** `cmdbufFence` é aguardada antes do próximo frame real e pertence ao último submit ordenado, impedindo resubmit enquanto qualquer main pass anterior estiver em execução.
- **PROVÁVEL:** pre-recording é seguro em drivers Vulkan conformes; ainda requer validação física no Turnip do aparelho.

## Swapchain recreation validation

- **CONFIRMADO:** resolução, formato, multiplier, Flow Scale e Performance Mode são capturados ao construir `Swapchain` e `ContextImpl`.
- **CONFIRMADO:** destruição remove o contexto da layer; `closeContext()` executa `DeviceWaitIdle()` antes de destruir imagens, descriptors, pipelines e command buffers do backend.
- **CONFIRMADO:** reload executa `DeviceWaitIdle()`, remove todos os contextos, retorna `VK_ERROR_OUT_OF_DATE_KHR` e força uma nova swapchain/contexto. Command buffers antigos não sobrevivem a uma geração de configuração.
- **CONFIRMADO:** recriação por fullscreen/windowed ou mudança de resolução segue a mesma propriedade de contexto.

## Hot reload validation

- **CONFIRMADO:** mudar 1x/2x/3x/4x, Flow Scale ou Performance Mode invalida a swapchain atual e cria novas destination images, descriptors, pipelines e variantes de command buffer.
- **CONFIRMADO:** destination count permanece `multiplier - 1`: 2x tem uma, 3x duas e 4x três imagens geradas.
- **CONFIRMADO:** timestamp de interpolação permanece `(destination_index + 1) / (destination_count + 1)`: 2x = 1/2; 3x = 1/3 e 2/3; 4x = 1/4, 2/4 e 3/4.
- **CONFIRMADO:** Flow Scale menor produz flow extent menor pela conversão recíproca existente na fronteira da layer. Nenhum clamp ou ajuste automático foi introduzido.

## A6xx compatibility review

- **CONFIRMADO:** não existe hardcode por modelo de GPU ou jogo nas Rodadas 3/4/5/6.
- **CONFIRMADO:** FP16 depende de `shaderFloat16` e de `allow_fp16`; existe caminho FP32 quando FP16 não é suportado ou permitido.
- **CONFIRMADO:** criação de external images consulta compatibilidade de formato/external memory, seleção de queue verifica flags necessárias e MAILBOX só é usado quando anunciado, com FIFO como fallback.
- **PROVÁVEL:** isso mantém um caminho geral para A6xx compatíveis, sem política específica para Adreno 619.
- **NÃO CONFIRMADO / LIMITAÇÃO EXISTENTE:** timeline semaphore e extensões external-memory/semaphore FD são requisitos rígidos; não há fallback para binary-only ou backend sem FD. Isso precede estas otimizações e não foi alterado por ser uma mudança de alto risco.
- **NÃO CONFIRMADO / LIMITAÇÃO EXISTENTE:** preflight explícito para todos os descriptor/workgroup/image limits é incompleto; falhas Vulkan continuam sendo a fronteira efetiva.

## Final performance impact

- **Rodada 3 — CONFIRMADO, CPU/memória:** elimina pequenas allocations e construção de vectors nos submits de cópia da layer. Trabalho GPU igual.
- **Rodada 4 — CONFIRMADO, CPU/memória:** elimina temporários heap dos timeline submits para cada destino. Trabalho GPU igual.
- **Rodada 5 — CONFIRMADO, CPU/driver:** evita reemitir binds, barriers e dispatches Gamma/Delta/Generate a cada frame real. O benefício potencial cresce em 3x/4x. Dispatches e bandwidth GPU iguais.
- **PROVÁVEL, driver:** menos ciclos begin/record/end reduzem pressão de command recording no stack A6xx.
- **NÃO CONFIRMADO:** nenhum ganho percentual ou de FPS é afirmado sem benchmark físico.

## Remaining known limitations

- **CONFIRMADO:** FPS real baixo/irregular aumenta fortemente os artefatos do ETS2; Flow Scale isoladamente não resolveu isso nos testes.
- **PROVÁVEL:** distância temporal, GPU contention e cadence/display timing contribuem para partes diferentes da distorção e tremor residual.
- **NÃO CONFIRMADO:** a contribuição relativa não foi isolada com GPU/display timing controlado.
- **CONFIRMADO:** esta rodada não modifica pacing, MAILBOX, shaders, optical flow, Generate, multiplier ou componentes do runtime.

## Final regression checklist

- **CONFIRMADO (estático):** contagens e fractions de 2x/3x/4x permanecem corretas.
- **CONFIRMADO (estático):** optical-flow comum continua uma vez por par real; generated frames não alimentam o próximo optical flow.
- **CONFIRMADO (estático):** external handles, image-state tracking, acquire, semáforos, fences, barriers e ownership de conclusão permanecem intactos.
- **CONFIRMADO (estático):** MAILBOX/fallback e passthrough permanecem iguais.
- **CONFIRMADO (estático):** Lossless.dll e shaders proprietários não foram modificados.
- **NÃO CONFIRMADO (físico):** Tomb Raider, NFS 2012 e ETS2 permanecem pendentes do teste final do usuário.

## Part 2 Round 5 - 3x/4x GPU optimization

### Estado de validação

- **CONFIRMADO:** a Rodada 4 não foi testada no aparelho e não é baseline confirmado.
- **CONFIRMADO:** o último baseline físico é a Rodada 3, validada posteriormente no Tomb Raider/ETS2. A layer dessa rodada foi preservada localmente em `/tmp/liblsfg-vk-round3-confirmed.so`, SHA-256 `420868c8c1a944cfc14ce640e1b26826b11bd8ee5fc1bbc03635cd8c4bcc7411`.
- **CONFIRMADO estaticamente:** a mudança da Rodada 4 foi mantida porque substitui armazenamento heap por arrays equivalentes sem alterar handles, timeline values, stages, fences ou ordem de submit. Ela continua pendente de validação física junto com esta build.

## Dispatch cost breakdown

| Bloco | Fixo por par real | Por intermediário | 2x | 3x | 4x |
|---|---:|---:|---:|---:|---:|
| Mipmaps/preprocess | 1 | 0 | 1 | 1 | 1 |
| Alpha0 | 21 | 0 | 21 | 21 | 21 |
| Alpha1 | 7 | 0 | 7 | 7 | 7 |
| Beta0/Beta1 | 5 | 0 | 5 | 5 | 5 |
| Gamma0/Gamma1 | 0 | 35 | 35 | 70 | 105 |
| Delta0/Delta1 | 0 | 30 | 30 | 60 | 90 |
| Generate | 0 | 1 | 1 | 2 | 3 |
| **Compute total** | **34** | **66** | **100** | **166** | **232** |
| Blit real→source | 1 | 0 | 1 | 1 | 1 |
| Blit destination→swapchain | 0 | 1 | 1 | 2 | 3 |
| Acquire adicional | 0 | 1 | 1 | 2 | 3 |
| Presents totais | 1 real | 1 | 2 | 3 | 4 |

- **CONFIRMADO:** a contagem anterior permanece correta no source atual.
- **CONFIRMADO:** 34 dispatches e todo Alpha/Beta são compartilhados uma vez por par real.
- **CONFIRMADO:** 65 dos 66 dispatches adicionais por destino são Gamma/Delta; Generate isolado é apenas um dispatch full-resolution.
- **PROVÁVEL:** chamar todo o custo variável de “Generate cost” esconde que Gamma/Delta dominam a quantidade de passes e tráfego intermediário.

## Shared work analysis

### Já compartilhado

- **CONFIRMADO:** duas source images reais, pyramid de sete níveis, Alpha0/Alpha1, Beta0/Beta1 e o campo base são construídos uma vez e reutilizados por todas as fractions.
- **CONFIRMADO:** pipelines, samplers, image views, descriptor pool/sets, constant buffers e common resources são persistentes por contexto.
- **CONFIRMADO:** não há descriptor allocation/update, pipeline creation ou image allocation por frame.

### Necessariamente específico por fraction no desenho atual

- **CONFIRMADO:** cada destino tem constant buffer com timestamp próprio.
- **CONFIRMADO:** Gamma/Delta usam esse constant buffer e armazenam resultados intermediários próprios; Generate consome esses resultados e escreve uma destination image própria.
- **PROVÁVEL:** Gamma/Delta não podem ser simplesmente compartilhados entre 1/3, 2/3 ou 1/4, 2/4, 3/4 porque sua saída depende da fraction.
- **NÃO CONFIRMADO:** existência de subexpressões internas idênticas nos SPIR-V. Sem modificar/analisar semanticamente shaders proprietários, remover dispatches não é seguro.

### Trabalho CPU redundante confirmado

- **CONFIRMADO:** antes desta rodada, os mesmos 66 comandos de cada main pass eram gravados novamente a cada frame real, embora imagens, pipelines, descriptors, barriers e constant buffers sejam estáticos.
- **CONFIRMADO:** a única seleção variável é baseada em `fidx % 2` ou `fidx % 3`; o padrão completo repete a cada `LCM(2,3)=6` frames.
- **PROVÁVEL:** em 4x, regravar 198 dispatches e respectivos barriers/bindings por frame aumenta CPU/driver overhead e pode atrasar submits para uma GPU já carregada.

## Optical flow reuse

- **CONFIRMADO:** o mesmo pre-pass Mipmaps/Alpha/Beta atende todos os frames gerados do par real.
- **CONFIRMADO:** generated frames nunca retornam como source; somente os dois frames reais alternados alimentam o próximo optical flow.
- **CONFIRMADO:** nenhum pre-pass adicional é executado ao passar de 2x para 3x/4x.
- **PROVÁVEL:** o reuse comum já está no lugar correto. O crescimento de 3x/4x vem do refinamento/interpolação específico por fraction, não de recalcular toda a optical flow base.

## Generate cost model

Modelo mais preciso:

`custo total = B + N × (F + G + C + WSI)`

- `B`: 34 dispatches compartilhados de Mipmaps/Alpha/Beta;
- `N`: `multiplier - 1`;
- `F`: 65 dispatches Gamma/Delta dependentes da fraction;
- `G`: 1 Generate full-resolution;
- `C`: 1 blit destination→swapchain;
- `WSI`: 1 acquire e 1 present intermediário.

- **CONFIRMADO:** 2x = `B + 1×...`; 3x = `B + 2×...`; 4x = `B + 3×...`.
- **PROVÁVEL:** o custo variável cresce quase linearmente, mas stalls de acquire, cache/bandwidth e backpressure tornam wall time não linear.
- **NÃO CONFIRMADO:** porcentagem GPU de F versus G/C. Dispatch count não equivale a GPU time.

## GPU backpressure

- **CONFIRMADO:** no início de cada frame, a layer espera `renderFence` do ciclo anterior; o backend também espera `cmdbufFence` anterior antes do novo pre-pass.
- **CONFIRMADO:** isso limita acúmulo entre frames reais e protege reuse, mas transfere atraso GPU para a thread que apresenta o próximo frame real.
- **PROVÁVEL:** quando 3x/4x excedem o orçamento do intervalo real, LSFG reduz o FPS-base por competição e espera, especialmente no ETS2 pesado.
- **CONFIRMADO:** `AcquireNextImageKHR` usa timeout infinito para cada intermediário e pode bloquear quando não há imagem disponível.
- **PROVÁVEL:** submitted FPS alto pode coexistir com menor FPS-base e queue/WSI pressure; não mede throughput independente do jogo.
- **NÃO CONFIRMADO:** tempo gasto em fence, acquire, compute ou present no Adreno 619; não foi adicionada telemetria pesada.

## Swapchain image pressure

- **CONFIRMADO:** para multiplier >1, `minImageCount` solicitado pelo app recebe `+ multiplier`, limitado por `maxImageCount` quando não zero.
- **CONFIRMADO:** se o app pedir `R` imagens e o surface permitir, pedidos conceituais ficam `R+2`, `R+3`, `R+4` em 2x/3x/4x.
- **CONFIRMADO:** a layer mantém duas source images e N destination images externas; o swapchain mantém estado e dois semáforos binários por imagem efetivamente retornada.
- **CONFIRMADO:** por frame real, existe uma imagem original do app e até N imagens adquiridas/apresentadas para intermediários. Pending-present pode alcançar N+1 até o WSI liberar imagens.
- **PROVÁVEL:** 4x necessita mais margem para evitar bloqueio de acquire; reduzir image count sem trace WSI ameaça deadlock/stall e não foi feito.
- **NÃO CONFIRMADO:** o número efetivo retornado pelo Turnip em cada jogo. `minImageCount` é pedido, não garantia de contagem exata.

### Lifetime por contexto

- source images: duas, até destruir/recriar o contexto;
- destination images: N, até destruir/recriar o contexto;
- Gamma/Delta/Generate resources: duplicados por destination/fraction e persistentes;
- descriptors/image views/pipelines: persistentes, sem update por frame;
- layer command buffers/semaphores/image states: persistentes por swapchain;
- backend command buffers/timeline/binary semaphores/fence: persistentes por contexto;
- **CONFIRMADO:** não foi encontrada retenção crescente por frame ou pool reset ausente; uso cresce com N no momento da criação e depois estabiliza.

## Command buffer reuse

### Antes da Rodada 5

- **CONFIRMADO:** backend possuía `N+1` command buffers: um pre-pass e um por destino.
- **CONFIRMADO:** todos eram regravados a cada frame. O main pass repetia 66 dispatch/barrier/bind sequences por destino.
- **CONFIRMADO:** descriptors alternam em períodos 2 e 3; nenhum binding depende de handle criado por frame.

### Depois da otimização

- **CONFIRMADO:** pre-pass continua único e regravado, mantendo o escopo reduzido.
- **CONFIRMADO:** cada destino agora possui seis command buffers main pré-gravados, um para cada variante de `fidx mod 6`.
- **CONFIRMADO:** quantidades por contexto passam a 7 em 2x, 13 em 3x e 19 em 4x.
- **CONFIRMADO:** command buffers main são gravados com usage flags 0, permitindo resubmissão após conclusão. A fence existente garante que o frame anterior terminou antes de selecionar/submeter qualquer variante.
- **CONFIRMADO:** o hot path apenas seleciona `1 + destination×6 + fidx%6` e submete; não regrava Gamma/Delta/Generate.
- **PROVÁVEL:** troca pequena de memória de command buffer por redução grande de CPU/driver recording, com benefício relativo maior em 3x/4x.

## Descriptor reuse

- **CONFIRMADO:** descriptor sets são alocados e escritos durante construção dos ManagedShaders.
- **CONFIRMADO:** não há `vkUpdateDescriptorSets`, allocation ou pool reset por frame/intermediário no hot path.
- **CONFIRMADO:** cada variante pré-gravada referencia os mesmos descriptor sets persistentes que o caminho dinâmico selecionava.
- **NÃO CONFIRMADO:** possibilidade de reduzir a quantidade total de sets sem mudar bindings/shaders. Não é candidato seguro nesta rodada.

## Memory bandwidth

- **CONFIRMADO:** Mipmaps/Beta pyramid usam R8; muitas imagens temporárias usam RGBA8 por padrão; saídas Gamma1/Delta1 específicas usam RGBA16F.
- **CONFIRMADO:** Gamma/Delta criam e leem/escrevem conjuntos próprios por fraction. Portanto 3x/4x aumentam fortemente storage-image traffic e footprint.
- **CONFIRMADO:** Generate e cada blit de saída trabalham em resolução integral, independentemente de Flow Scale.
- **PROVÁVEL:** A6xx menor pode ser limitada por bandwidth/cache tanto quanto por compute; muitos passes pequenos também adicionam barrier/dispatch overhead.
- **CONFIRMADO:** a otimização desta rodada não reduz tráfego GPU nem qualidade; reduz apenas gravação CPU/driver dos mesmos comandos.
- **NÃO CONFIRMADO:** bandwidth efetiva e cache miss rate sem profiler GPU.

### Transitions/barriers audit

- **CONFIRMADO:** ManagedShader insere compute→compute barriers por sampled/storage image; cópias da layer fazem GENERAL/PRESENT/TRANSFER transitions explícitas.
- barriers genéricas potencialmente amplas: **MÉDIO/ALTO RISCO** para otimizar;
- initial GENERAL setup por contexto: **BAIXO custo**, fora do hot path;
- remover transitions de swapchain/external images: **ALTO RISCO**, ligado às correções de flicker/corrupção;
- combinar barriers somente após mapa producer/consumer por imagem: **MÉDIO RISCO**, não implementado.

## A6xx capability implications

- **CONFIRMADO:** `allow_fp16=true` apenas permite a variante; o backend consulta `VkPhysicalDeviceVulkan12Features.shaderFloat16` e seleciona FP16 somente quando suportado. Caso contrário usa recursos FP32.
- **CONFIRMADO:** FP16 é escolha de shader registry; não foi forçado por nome de GPU nesta rodada.
- **PROVÁVEL:** FP16 reduz ALU/register/bandwidth em A6xx compatível, mas precisão/driver correctness precisam do fallback existente.
- **CONFIRMADO:** pre-recording de command buffers depende somente do padrão estático de descriptors, não de capability Adreno; beneficia qualquer driver Vulkan com overhead de gravação relevante.
- **NÃO CONFIRMADO:** subgroup, occupancy, workgroup e memory-heap ideais por A6xx. Nenhum caminho novo foi habilitado com base nessas propriedades.

## Implemented optimization

**Única melhoria funcional:** pré-gravação e reuse dos main command buffers Gamma/Delta/Generate em seis variantes por destination image.

Arquivos source afetados:

- `lsfg-vk-common/include/lsfg-vk-common/vulkan/command_buffer.hpp`: permite flags explícitas no begin;
- `lsfg-vk-common/src/vulkan/command_buffer.cpp`: encaminha as flags;
- `lsfg-vk-backend/src/lsfgvk.cpp`: aloca, grava e seleciona as seis variantes.

Comportamento esperado:

- reduzir CPU/driver command recording em 3x/4x;
- fazer main submits chegarem à GPU com menos overhead/variabilidade;
- não alterar GPU dispatch count, qualidade, fractions ou sincronização;
- benefício maior quando o driver/CPU é parte do gargalo; limitado quando a GPU/bandwidth já está saturada.

## Risk assessment

- Risco classificado: **BAIXO A MÉDIO**.
- **CONFIRMADO:** sequência de render calls usada na pré-gravação é idêntica à removida do hot path.
- **CONFIRMADO:** período 6 cobre todos os acessos `idx%2` e `idx%3` de Mipmaps/Gamma/Delta/Generate.
- **CONFIRMADO:** constant buffers, descriptors, imagens e pipelines vivem por todo o contexto.
- **CONFIRMADO:** main command buffers só são resubmetidos depois da fence do frame anterior.
- **PROVÁVEL:** custo de memória adicional dos command buffers é pequeno comparado às imagens temporárias de 3x/4x.
- Risco residual: driver pode tratar command buffers grandes reutilizáveis de forma diferente; requer teste Tomb Raider 2x/3x/4x antes de considerar baseline.
- Reversão: restaurar `N+1` buffers e a gravação main dentro de `scheduleFrames()`; a layer Round 3 confirmada também permanece disponível em `/tmp`.

## Regression checklist

- **CONFIRMADO estaticamente:** 34 + N×66 dispatches preservados; somente momento de gravação mudou.
- **CONFIRMADO estaticamente:** fractions 0.5; 1/3,2/3; 1/4,2/4,3/4 preservadas.
- **CONFIRMADO estaticamente:** optical flow comum continua uma vez por frame real.
- **CONFIRMADO estaticamente:** output-ready/fence permanecem somente no último main pass.
- **CONFIRMADO estaticamente:** source/destination images, external FDs, timeline, image reuse e barriers inalterados.
- **CONFIRMADO estaticamente:** MAILBOX/FIFO fallback e image count inalterados.
- **CONFIRMADO estaticamente:** shaders/Lossless.dll e runtime Winlator inalterados.
- **PENDENTE NO APARELHO:** Tomb Raider abre rápido, não crasha e mantém 2x/3x/4x sem corrupção.
- **PENDENTE NO APARELHO:** comparar 3x/4x do Tomb Raider nas cenas 67–75 e 90–100 FPS.
- **PENDENTE NO APARELHO:** ETS2 pesado verifica FPS-base, submitted FPS e artefatos.
- **PENDENTE NO APARELHO:** ETS2 baixo verifica o tremor residual.
- **PENDENTE NO APARELHO:** NFS não piora.

## Part 4 Round 6 - Final Quality Path Stabilization

- **CONFIRMADO:** esta rodada encerra a investigação do quality path sem nova alteração runtime, shader, configuração ou asset.
- **CONFIRMADO:** a biblioteca instalada permanece a baseline `uiThreshold=0.50`, SHA256 `7c58d7a40cd8b64510614e6906c142858883e91522b9f4397394211f7350e81c`.
- **DECISÃO:** não gerar outro APK, pois a APK restaurada da Rodada 5 já contém exatamente essa biblioteca e nenhuma correção OFF segura foi encontrada.

## Final quality-path model

- **CONFIRMADO:** `Mipmaps → Alpha → Beta → Gamma → Delta → Generate` é a cadeia host observada.
- **CONFIRMADO:** Flow Scale controla a resolução dos campos internos; Generate combina campos reconstruídos com Previous/Current e escreve em resolução final.
- **CONFIRMADO:** os shaders recebem fraction lógica, mas não recebem FPS, frametime nem delta temporal real.
- **CONFIRMADO:** não existe motion clamp, search radius, confidence threshold geral ou controle de disocclusion exposto com semântica segura pelo host.

## Gamma/Delta reconstruction summary

- **CONFIRMADO:** Gamma `RG/BA` contém duas coordenadas/vetores 2D usados para amostrar Previous e Current.
- **CONFIRMADO:** Delta0 fornece uma segunda hipótese bidirecional de movimento.
- **CONFIRMADO:** Delta1 fornece quatro logits; Generate aplica softmax e usa os pesos para combinar duas amostras de Previous e duas de Current.
- **PROVÁVEL:** os recursos adicionais do caminho Quality/m=2 aumentam refinement/capacidade upstream, preservando a mesma interface final Gamma/Delta/Generate.

## uiThreshold physical result

- **CONFIRMADO FISICAMENTE:** `0.45` aumentou stutter e distorção, particularmente em 4x, sem reduzir de forma útil o tremor do ETS2.
- **CONFIRMADO:** `0.50` foi restaurado e escolhido como baseline final.
- **DIREÇÃO ENCERRADA:** não testar outros thresholds sem nova evidência independente.

## Performance Mode OFF status

- **CONFIRMADO FISICAMENTE:** Performance Mode ON funciona; OFF pode congelar jogo e Winlator.
- **CONFIRMADO ESTATICAMENTE:** OFF seleciona Quality e `m=2`; seus resources, descriptors e shader variants são criados pelo caminho esperado.
- **CONFIRMADO ESTATICAMENTE:** o período seis dos command buffers cobre `%2/%3`; timeline values, semáforos, fence e dispatch dimensions não divergem entre ON/OFF.
- **CONFIRMADO ESTATICAMENTE:** criação de images/views/memory, descriptor pool/sets, command buffers e submits verifica os `VkResult` relevantes ou propaga erro.
- **BUG CONHECIDO / NÃO RESOLVIDO:** não há causa host-side óbvia e segura para corrigir. OFF não foi convertido ou redirecionado para ON.
- **PROVÁVEL:** maior pressão de compute/bandwidth do modelo Quality pode provocar timeout/hang no stack atual; faltam dados runtime suficientes para confirmar device lost, watchdog ou driver reset.

## Residual artifact classification

- **CONFIRMADO:** artefatos aumentam quando o FPS-base cai e diminuem muito quando ele sobe; 2x também pode apresentar tremor.
- **CONFIRMADO:** history real, fractions, sincronização e present sequencing estão corretos dentro do modelo/API disponível.
- **CONFIRMADO:** a pequena correção de cadence da Parte 3 melhorou apenas uma parcela do problema.
- **PROVÁVEL:** grande distância temporal, motion magnitude, disocclusion e baixa resolução do flow em escalas pequenas dominam o artefato restante.
- **NÃO CONFIRMADO:** que todo tremor residual seja bug do fork; a limitação natural do algoritmo em aproximadamente 20 FPS deve ser considerada.

## Internal vs external limitations

- **INTERNO CONFIRMADO:** ausência de delta temporal real e de controles host-side seguros para magnitude/confidence/disocclusion.
- **INTERNO PROVÁVEL:** limites do modelo proprietário diante de movimento grande e regiões recém-reveladas.
- **EXTERNO POSSÍVEL:** Turnip, driver, ROM, SurfaceFlinger/compositor, Android graphics stack e cadence de refresh podem modificar o resultado observado.
- **NÃO CONFIRMADO:** nenhuma ROM ou componente externo específico foi identificado como causa sem teste A/B controlado.

## Closed quality experiments

- Encerrados: `uiThreshold<0.50`, patch de SPIR-V, alteração de Gamma/Delta/logits/softmax, motion clamp especulativo e knobs desconhecidos.
- Permanecem proibidos sem nova evidência: hacks por jogo/GPU, auto Flow Scale, auto multiplier, fallback silencioso OFF→ON e telemetria pesada.
- Performance Mode OFF permanece documentado para diagnóstico futuro, não para experimentação aleatória.

## Final regression assessment

- **PRESERVADO:** estabilidade das Partes 2 e 3, sync independente por intermediate, external handles, image/resource lifetime, semáforos, fences e command buffers pré-gravados.
- **PRESERVADO:** MAILBOX/FIFO, fractions 2x/3x/4x, Flow Scale, Performance Mode semantics e hot reload.
- **PRESERVADO:** Lossless.dll externa e shaders proprietários sem modificação ou redistribuição.
- **CONFIRMADO:** nenhum dump SPIR-V, DLL, disassembly ou reflection JSON está rastreado ou presente no working tree.
- **RISCO CONHECIDO:** Performance Mode OFF continua podendo congelar; ON é o baseline recomendado.

## Part 5 Handoff

A Parte 5 deve abordar **capability detection, presets e integração adaptativa**, começando pelo host e não pelos shaders.

Prioridades:

1. construir perfil de capabilities Vulkan real para A6xx e, quando possível, A7xx/A8xx;
2. separar conceitos Performance/Balanced/Quality sem alterar silenciosamente a escolha manual;
3. detectar FP16, formatos storage, workgroup/descriptor limits, memory properties, queues, timeline semaphores e present modes;
4. oferecer fallback seguro quando uma capability não existir;
5. evitar configurações excessivamente caras em GPUs menores sem hardcode por jogo ou Adreno 619;
6. preservar controles manuais de multiplier, Flow Scale e Performance Mode;
7. tratar Performance Mode OFF como capability/compatibility concern explícito, nunca como alias silencioso de ON;
8. manter Tomb Raider e NFS como regressão saudável e ETS2 como estudo low-FPS.

Fora do escopo deste handoff: implementar presets, UI, auto Flow Scale, auto multiplier, valores por GPU ou qualquer mudança da Parte 5 nesta rodada.

## Part 5 Round 1 - Capability Detection

- **CONFIRMADO:** esta rodada auditou somente o host e não alterou runtime, shader, configuração, asset ou APK.
- **CONFIRMADO:** o backend já consulta algumas capabilities no momento de uso, mas não possui uma estrutura consolidada equivalente a `LsfgGpuCapabilities`.
- **CONFIRMADO:** nome, vendor/device ID e PCI bus são usados somente para selecionar o physical device configurado; não controlam qualidade, multiplier ou Flow Scale.

## Vulkan device capabilities

Estado atual da detecção:

| Capability | Classe | Usada hoje? | Comportamento atual | Entrada futura segura |
|---|---|---:|---|---:|
| Vulkan API 1.2 | REQUIRED efetivo | Sim | private instance solicita fixamente 1.2 | Sim |
| Timeline semaphore | REQUIRED | Sim | feature e extensão são habilitadas; criação falha se indisponível | Sim |
| OPAQUE_FD external memory | REQUIRED | Sim | formato/usage e import/export são validados na criação | Sim |
| SYNC_FD external semaphore | REQUIRED | Sim | import/export é consultado antes de criar semaphore | Sim |
| Compute queue | REQUIRED | Sim | primeira family contendo `COMPUTE_BIT` | Sim |
| R8/RGBA8/RGBA16F formats | REQUIRED conforme caminho | Sim | usados pelo pipeline; não há preflight consolidado | Sim |
| Workgroup 32×32/1024 | REQUIRED para módulos que o usam | Sim | shader pipeline é criado sem comparação explícita de limits | Sim |
| FP16 arithmetic | OPTIONAL/PERFORMANCE | Sim | variante FP16 somente quando permitida e `shaderFloat16=true` | Sim |
| MAILBOX | OPTIONAL/PRESENT | Sim | consultado por surface; FIFO é fallback | Sim |
| Descriptor indexing | OPTIONAL/UNKNOWN | Não | pipeline usa descriptors estáticos | Não por enquanto |
| synchronization2 | OPTIONAL/UNKNOWN | Não | sincronização usa API/barriers tradicionais | Não por enquanto |
| Subgroup properties | PERFORMANCE/UNKNOWN | Não | shaders não possuem seleção host por subgroup | Futuro, após evidência |
| Memory budget | PERFORMANCE/SAFETY | Não | apenas memory types/heaps básicos são consultados | Sim |
| Driver properties | DIAGNOSTIC | Parcial | `driverVersion` existe em properties, mas não é armazenado/modelado | Sim |
| External fence | OPTIONAL/UNUSED | Não | sincronização externa usa semáforos/FDs | Não |
| Robustness | OPTIONAL/UNKNOWN | Não | nenhum caminho alternativo usa robustness | Não por enquanto |

## Required features

- **CONFIRMADO:** compute queue, timeline semaphore, external memory FD e external semaphore FD são requisitos estruturais da implementação atual.
- **CONFIRMADO:** source/destination externos exigem `SAMPLED`, `STORAGE`, `TRANSFER_SRC` e `TRANSFER_DST`, além de import/export OPAQUE_FD para o formato ativo.
- **CONFIRMADO:** sem FP16 existe fallback FP32; portanto FP16 não é requisito de compatibilidade.
- **CONFIRMADO:** alguns shaders extraídos usam local size 32×32, isto é, 1024 invocações. O perfil futuro deve exigir `maxComputeWorkGroupSize[0/1] >= 32` e `maxComputeWorkGroupInvocations >= 1024` para o registry atual.
- **PROVÁVEL:** todos os Adreno testados/visados podem satisfazer esse limite, mas isso não foi demonstrado device a device nesta rodada.

## Optional features

- **CONFIRMADO:** MAILBOX é opcional e possui fallback FIFO.
- **CONFIRMADO:** FP16 é opcional e possui fallback FP32 já funcional no registry.
- **CONFIRMADO:** PCI bus info é opcional e serve apenas para device selection.
- **CONFIRMADO:** descriptor indexing, synchronization2, external fence e present timing não são necessários ao caminho atual.
- **DESCONHECIDO:** se subgroups ou synchronization2 ofereceriam ganho real no host sem alterações de shader/sync; não devem influenciar preset até existir implementação correspondente.

## Format support

Formatos realmente usados:

- `VK_FORMAT_R8_UNORM`: mipmaps e pirâmide Beta/máscara;
- `VK_FORMAT_R8G8B8A8_UNORM`: temporários comuns e source/destination SDR;
- `VK_FORMAT_R16G16B16A16_SFLOAT`: Gamma/Delta finais e source/destination HDR.

- **CONFIRMADO:** external source/destination consultam `vkGetPhysicalDeviceImageFormatProperties2` com OPAQUE_FD e usage completo antes da alocação.
- **CONFIRMADO:** images internas dependem de optimal tiling com sampled/storage; hoje a falha aparece em `vkCreateImage`, view ou pipeline, não em um relatório preventivo.
- **RECOMENDAÇÃO FUTURA:** coletar `VkFormatProperties2/3` para sampled/storage/transfer em optimal tiling e separar suporte SDR, HDR e intermediários antes de escolher um perfil.

## FP16 support

- **CONFIRMADO:** o host consulta `VkPhysicalDeviceVulkan12Features.shaderFloat16`.
- **CONFIRMADO:** `allow_fp16 && shaderFloat16` seleciona os módulos FP16; caso contrário seleciona módulos FP32.
- **CONFIRMADO:** FP16 não altera multiplier, Flow Scale, masks nem fractions.
- **PERFORMANCE:** pode reduzir ALU/register/bandwidth, mas seu benefício/correção depende do driver.
- **RISCO FUTURO:** `supportsFp16` deve continuar sendo capability, nunca inferência pelo nome A6xx/A7xx/A8xx.

## Compute limits

- Workgroups refletidos: 90 módulos usam 8×8, Generate usa 16×16 e seis módulos usam 32×32.
- Máximo observado: 32×32×1 = 1024 invocações.
- **CONFIRMADO:** o código atual não coleta `maxComputeWorkGroupCount`, `maxComputeWorkGroupSize`, `maxComputeWorkGroupInvocations` nem `maxComputeSharedMemorySize` para preflight/profile.
- **CONFIRMADO:** dispatch group counts são calculados a partir de source/flow extents; nenhum workgroup é reduzido automaticamente.
- **ENTRADA SEGURA FUTURA:** limits reais e máximo extent derivado da resolução/Flow Scale. Não alterar shaders ou dispatch nesta fase.

## Memory model

- **CONFIRMADO:** o backend consulta `VkPhysicalDeviceMemoryProperties` somente para escolher memory types device-local/host-visible.
- **CONFIRMADO:** não consulta `VK_EXT_memory_budget`, heap budget/usage ou uma estimativa global antes de construir o contexto.
- Footprint cresce aproximadamente com pixels do `sourceExtent`, pixels do `flowExtent²` em termos de escala linear, número de destinations (`multiplier-1`) e largura m=1/m=2.
- **CONFIRMADO:** multiplier adiciona destinations full-resolution e cadeias Gamma/Delta/Generate por intermediate; Flow Scale altera fortemente os temporários de flow; Quality/m=2 adiciona recursos upstream.
- **RECOMENDAÇÃO FUTURA:** estimativa conservadora separando bytes full-resolution, bytes flow-resolution, external allocations e alinhamento; memory budget deve ser sinal de risco, não gatilho silencioso para mudar opções.

## Queue capabilities

- **CONFIRMADO:** o backend seleciona a primeira queue family que contém `VK_QUEUE_COMPUTE_BIT` e cria uma queue com prioridade 1.0.
- **CONFIRMADO:** ele não prefere explicitamente dedicated-compute, não registra queueCount e não troca de queue por GPU.
- **CONFIRMADO:** graphics/compute podem compartilhar a mesma engine/queue family; capability de queue não implica paralelismo ou headroom.
- **ENTRADA SEGURA FUTURA:** flags, queueCount e se a family é dedicated/shared; qualquer troca efetiva de queue exige rodada própria de sincronização.

## Present capabilities

- **CONFIRMADO:** surface capabilities e present modes são consultados por swapchain.
- **CONFIRMADO:** MAILBOX é escolhido apenas quando anunciado e framegen está ativo; FIFO é fallback garantido.
- **CONFIRMADO:** present mode deve permanecer dimensão separada de compute capability e qualidade do optical flow.
- **NÃO COLETADO:** refresh duration/timing físico confiável; submitted FPS não deve virar score de display quality.

## A6xx implications

- **CONFIRMADO FISICAMENTE:** Adreno 619/Turnip baseline satisfaz o caminho atual com FP16 permitido, external handles, timeline, formats e workgroups necessários.
- **NÃO CONFIRMADO:** capacidades idênticas em 610/612/613/618/620/630/640/642/650/660; cada device/driver deve ser consultado.
- GPUs A6xx menores podem possuir as features requeridas e ainda ter throughput/bandwidth insuficiente para 3x/4x caros. Capability não é benchmark.
- Nenhum gate futuro pode bloquear o caminho atual do 619 apenas por classificação LOW/MID.

## A7xx implications

- **PROVÁVEL:** A7xx tende a oferecer mais compute/bandwidth, mas isso não substitui consulta de features, formats, limits e external-handle support do driver.
- **CONFIRMADO:** não existe dependência host-side atual que selecione caminho por família Adreno; um A7xx que satisfaça os requisitos deve seguir o mesmo backend.
- **NÃO CONFIRMADO:** ganho de FP16, Quality OFF, multiplier ou Flow Scale ideal em qualquer modelo A7xx.

## A8xx implications

- **CONFIRMADO:** não existe bloqueio nominal por A8xx no source atual.
- **DESCONHECIDO:** exposição real de extensions, external handles, formatos e comportamento do driver/Android WSI em A8xx.
- A compatibilidade deve ser capability-first com fallback estável; nome A8xx serve somente para diagnóstico/relatório.

## Performance vs visual quality

- **CONFIRMADO FISICAMENTE:** Tomb Raider em Ultra, aproximadamente 30 FPS-base, manteve reconstrução visual limpa em 2x/3x apesar da carga alta, mas apresentou sensação de resposta pesada.
- **CONFIRMADO FISICAMENTE:** ETS2 em FPS-base menor apresenta mais warping/tremor e melhora muito quando os frames reais ficam mais próximos/estáveis.
- **CONCLUSÃO:** capability/throughput, qualidade temporal, qualidade visual e latência são eixos independentes. Não devem ser condensados em um score único opaco.
- GPU load alto pode aumentar latência ou reduzir FPS-base sem necessariamente causar flow incorreto.

## Base-FPS vs latency

- Input e estado do jogo continuam atualizados na taxa dos frames reais; generated FPS não aumenta a frequência lógica do jogo.
- Um resultado pode ser visualmente suave e ainda responder como aproximadamente 30 FPS reais.
- **ENTRADA FUTURA DISTINTA:** FPS-base, variabilidade temporal e latência percebida não são capabilities estáticas e não devem ser inferidos de vendor/deviceName.
- Nesta rodada não foi adicionada medição temporal nem telemetria.

## Safe preset inputs

Entradas estáticas seguras para a Rodada 2:

1. API version e driver properties;
2. timeline/external-memory/external-semaphore support;
3. support/feature bits por formato e usage;
4. FP16 real;
5. compute workgroup/dispatch/shared-memory limits;
6. descriptor limits necessários pelo registry;
7. memory heaps e `VK_EXT_memory_budget` quando disponível;
8. queue flags/count/dedicated status;
9. present modes por surface.

Essas entradas descrevem **viabilidade e risco**, não escolhem automaticamente multiplier/Flow Scale.

## Unsafe hardcoding candidates

- `deviceName`, “Adreno 619/740/8xx”, Android version, ROM e marketing tier não podem ser base única de decisão.
- `vendorID/deviceID` são úteis para diagnóstico, cache e eventualmente workaround documentado, não para presumir throughput.
- FPS do HUD, submitted FPS e carga gráfica configurada não são capabilities.
- Nenhum preset deve alterar silenciosamente multiplier, Flow Scale ou Performance Mode.

## Future profile model

Estrutura conceitual sugerida, ainda não implementada:

```text
LsfgGpuCapabilities
  identity: api/vendor/device/driver/name
  required: timeline, externalMemoryFd, syncFd, formats, computeLimits
  optional: fp16, mailbox, memoryBudget, dedicatedCompute
  limits: workgroups, descriptors, imageDimensions, heaps
  compatibility: baselineSupported, hdrSupported, reasons[]
  advisory: capabilityTier, warnings[]
```

- `capabilityTier` LOW/MID/HIGH deve ser apenas recomendação auditável e derivada de dados; não substitui benchmark/runtime temporal.
- Performance/Balanced/Quality futuros devem ser conveniências explícitas, mantendo override manual completo.
- Performance Mode OFF não deve entrar em Quality enquanto continuar congelando.

## Risk assessment

- **BAIXO:** somente documentação foi alterada; baseline 0.50 e runtime permaneceram intactos.
- **RISCO DE IMPLEMENTAÇÃO FUTURA:** consultar capabilities é seguro; transformar ausência em bloqueio exige preservar o A6xx já validado e fornecer razões/fallback claros.
- **BUG/PENDÊNCIA EXISTENTE:** o host solicita API 1.2 e extensões requeridas antes de construir um relatório consolidado; falhas são corretas, porém pouco explicativas.
- **PRÓXIMA RODADA:** implementar somente a estrutura/coleta read-only e um resumo único controlado, sem presets automáticos nem alteração de comportamento.

## Part 5 Round 2 - Runtime Capability Collection

- **IMPLEMENTADO:** snapshot read-only do physical device, avaliação central `supported/reasons/warnings` e resumo curto emitido uma vez na criação do backend.
- **IMPLEMENTADO:** resumo MAILBOX/FIFO emitido uma vez para a primeira surface ativa; present modes são surface-dependent e permanecem separados das capabilities estáticas da GPU.
- **CONFIRMADO:** o resultado não bloqueia inicialização, não escolhe preset e não altera multiplier, Flow Scale, Performance Mode, shaders, sync ou WSI policy.

## LsfgGpuCapabilities

A estrutura `vk::GpuCapabilities` contém:

- identidade: API, vendor/device ID, device/driver name, driver version/info;
- metadata advisory: A6xx/A7xx/A8xx/UNKNOWN e tier (mantido UNKNOWN);
- queue compute selecionada, flags e queue count;
- timeline, OPAQUE_FD, SYNC_FD e FP16;
- suporte R8/RGBA8/RGBA16F por usage;
- limites de workgroup e shared memory;
- memory budget/usage device-local quando `VK_EXT_memory_budget` existe;
- `supported`, `reasons[]` e `warnings[]`.

O snapshot vive no objeto `vk::Vulkan` e é coletado somente uma vez para o backend privado.

## Required capabilities

A avaliação marca reason quando falta:

1. Vulkan 1.2;
2. compute queue;
3. timeline semaphore;
4. RGBA8 OPAQUE_FD import/export;
5. SYNC_FD semaphore import/export;
6. R8 sampled/storage;
7. RGBA8 sampled/storage/transfer-src/transfer-dst;
8. RGBA16F sampled/storage;
9. workgroup 32×32 e 1024 invocações.

- **IMPORTANTE:** `supported=false` é somente observação nesta rodada. O código não adicionou gate novo.
- Falhas que já impediam o backend continuam falhando nos pontos existentes de device/resource creation; a estrutura não muda esse comportamento.

## Optional capabilities

- FP16 ausente gera warning e preserva fallback FP32.
- RGBA16F OPAQUE_FD ausente gera warning para o caminho externo HDR, sem bloquear SDR na avaliação.
- `VK_EXT_memory_budget` ausente gera warning e mantém a seleção de memória existente.
- MAILBOX ausente gera warning de surface e mantém FIFO.
- Driver metadata e família UNKNOWN não bloqueiam nada.

## Format validation

- `R8_UNORM`: sampled + storage para mipmaps/Beta.
- `R8G8B8A8_UNORM`: sampled + storage + transfer src/dst; external OPAQUE_FD SDR validado separadamente.
- `R16G16B16A16_SFLOAT`: sampled + storage para Gamma/Delta; external OPAQUE_FD HDR validado separadamente.
- **CONFIRMADO:** FP16 arithmetic e RGBA16F format support são campos independentes.
- Queries usam optimal-tiling format features e `vkGetPhysicalDeviceImageFormatProperties2` para external image support.

## FP16 detection

- `VkPhysicalDeviceVulkan12Features.shaderFloat16` alimenta `capabilities.fp16` e a seleção já existente de registry FP16/FP32.
- Nenhum shader ou critério de seleção foi alterado.
- Warning explícito: `FP16 unavailable; FP32 fallback selected`.

## Compute limits

Coletados:

- `maxComputeWorkGroupCount[3]`;
- `maxComputeWorkGroupSize[3]`;
- `maxComputeWorkGroupInvocations`;
- `maxComputeSharedMemorySize`;
- family index, queue flags e queue count da primeira compute-capable family.

A avaliação observa o máximo atual 32×32/1024, mas não reduz dispatch nem troca shaders.

## Memory budget

- Quando `VK_EXT_memory_budget` existe, soma budget e usage dos heaps device-local usando `VkPhysicalDeviceMemoryBudgetPropertiesEXT`.
- Quando não existe, ambos permanecem zero e um warning é registrado.
- Nenhuma alocação, Flow Scale ou multiplier é alterado com base nesses valores.

## Queue/present capabilities

- Compute queue é coletada do physical device antes da criação do logical device.
- A seleção efetiva continua exatamente a primeira family com `VK_QUEUE_COMPUTE_BIT`.
- MAILBOX/FIFO são consultados no caminho WSI já existente e resumidos uma vez porque dependem da surface.
- A política permanece MAILBOX quando disponível durante geração, caso contrário FIFO.

## Compatibility result

Exemplo conceitual do resultado emitido:

```text
baseline_supported=true
reasons=[]
warnings=[opcionais ausentes]
```

- O resumo `lsfg-vk: GPU capabilities` é uma única linha por criação do backend.
- Reasons e warnings são linhas adicionais somente quando não vazios.
- Não existe polling, arquivo em Documents ou logging por frame.

## GPU family metadata

- Device names contendo Adreno `(TM) 6`, `7` ou `8` são classificados apenas como A6xx/A7xx/A8xx metadata.
- Outros nomes permanecem UNKNOWN.
- Vendor/device ID e nome não participam de `supported` nem selecionam comportamento.

## Advisory tier

- Campo LOW/MID/HIGH/UNKNOWN foi preparado na API.
- **CONFIRMADO:** permanece sempre UNKNOWN nesta rodada para evitar estimativa de potência sem benchmark.
- Nenhum tier controla configuração.

## Runtime cost

- Coleta executa uma vez ao construir o backend, antes do logical device.
- Usa properties/features, extension list, queue families, três format queries, duas external-image queries, external semaphore query e memory budget opcional.
- Present modes são reaproveitados da query de criação da swapchain e apenas registrados na primeira surface.
- **CONFIRMADO:** custo zero no hot path por frame.

## Risk assessment

- **BAIXO:** observabilidade read-only; não há gate ou mutation de framegen.
- **CONFIRMADO ESTATICAMENTE:** `uiThreshold=0.50`, Gamma/Delta/Generate, fractions, multiplier, Flow Scale, Performance Mode, command buffers e sincronização permanecem inalterados.
- **COMPATIBILIDADE A6XX:** o Adreno 619 não é bloqueado; mesmo um resultado advisory inesperado não muda a inicialização nesta rodada.
- **RISCO RESIDUAL:** alguns drivers podem expor metadata incompleta; os campos opcionais geram warning/UNKNOWN, não fallback agressivo.
- **RODADA 3 RECOMENDADA:** expor essa coleta à integração/app de forma controlada e definir regras advisory transparentes para candidatos Performance/Balanced, ainda sem aplicar preset automaticamente.

## Part 5 Round 3 - Advisory Presets

- **CONFIRMADO:** foi criada a API read-only `PresetRecommendations getPresetRecommendations(const GpuCapabilities&)`.
- O resultado contém três alternativas, uma recomendação preferida, confiança, razões e avisos. Nenhum resultado é aplicado ao arquivo de configuração ou ao contexto ativo.
- O resumo de inicialização declara explicitamente `applied=false` e é emitido uma vez por criação do backend, sem logging por frame.

## Performance preset model

- Faixa advisory de Flow Scale: `0.20–0.35`.
- Performance Mode recomendado: ON, único caminho fisicamente validado e estável.
- Multiplier advisory: `2x–4x`, mantendo a escolha inteiramente manual e alertando que cada jogo deve ser validado.
- **CONFIRMADO:** quando os requisitos estáticos estão presentes, Performance é a preferência conservadora com confiança MEDIUM, porque a coleta não conhece headroom, FPS-base ou latência do jogo.
- **EVIDÊNCIA FÍSICA:** Flow Scale 0.20 reduziu custo no A6xx conhecido. A faixa não é selecionada pelo nome Adreno nem aplicada automaticamente.

## Balanced preset model

- Faixa advisory de Flow Scale: `0.35–0.50`.
- Performance Mode permanece ON.
- Confiança LOW até existirem dados de execução do jogo.
- FP16 disponível é uma razão favorável; ausência de FP16 gera aviso de custo FP32, mas não invalida o preset nem classifica a GPU como fraca.

## Quality preset model

- Faixa advisory de Flow Scale: `0.50–0.80`.
- Performance Mode permanece ON: OFF é marcado como caminho instável conhecido e nunca é recomendado.
- A recomendação informa o benefício possível de maior precisão do campo de movimento e os riscos de menor FPS-base, maior custo e maior latência.
- Memory budget ausente e FP16 ausente geram warnings; não impedem que a alternativa seja apresentada.

## Static capability inputs

- Entradas: resultado dos requisitos Vulkan, FP16, disponibilidade do memory budget e os warnings coletados pela Rodada 2.
- Compute queue, timeline semaphore, external handles, formatos e workgroup limits entram indiretamente no resultado central `supported`.
- GPU family, device name e tier não selecionam preset. A6xx/A7xx/A8xx permanecem metadata auxiliar.

## Runtime performance limitations

- **CONFIRMADO:** capabilities estáticas não medem FPS-base, frametime, estabilidade temporal, GPU headroom, refresh efetivo nem input latency.
- Tomb Raider mostrou reconstrução limpa com carga alta e aproximadamente 30 FPS-base, mas resposta mais pesada. Isso separa throughput/latência de qualidade visual.
- ETS2 mostrou que aproximadamente 20–22 FPS-base pode produzir distorção mesmo quando a GPU suporta o pipeline. Portanto tier estático não prevê qualidade por jogo.

## Multiplier advisory

- Todos os presets expõem somente a faixa suportada `2x–4x`.
- Nenhum multiplier é escolhido ou reduzido automaticamente.
- Os warnings recomendam validar 2x primeiro nos perfis de maior custo; refresh, FPS-base e conteúdo continuam decisões runtime/manuais.

## Flow Scale advisory

- Os valores são faixas explicáveis, não defaults aplicados: Performance `0.20–0.35`, Balanced `0.35–0.50`, Quality `0.50–0.80`.
- **CONFIRMADO:** nenhuma função escreve Flow Scale. O usuário mantém controle absoluto.
- A faixa maior representa maior resolução/custo do campo; não promete qualidade visual em conteúdo temporalmente difícil.

## Performance Mode limitation

- Todos os três modelos recomendam Performance Mode ON.
- OFF permanece `UNSTABLE / KNOWN BUG`; Quality não mascara o bug convertendo OFF em ON e não recomenda o caminho.
- Nenhuma seleção existente é alterada pelo advisory.

## Reasons and warnings

- Cada alternativa contém razões e warnings próprios.
- O resultado agregado sempre avisa que é advisory, que multiplier continua manual e que FPS/estabilidade/latência reais são desconhecidos.
- `supported=false` produz preferência UNKNOWN e confiança LOW, mas nesta rodada não bloqueia nem modifica o runtime.

## Recommendation confidence

- MEDIUM significa que a preferência conservadora é sustentada pelas capabilities estáticas disponíveis; não significa GPU de potência média.
- LOW é usado quando falta informação de execução ou quando requisitos reportam ausência.
- HIGH foi reservado pela API, mas não é atribuído sem evidência runtime suficiente.

## Unknown/fallback behavior

- Capabilities incompatíveis ou insuficientes retornam preferência UNKNOWN com razões/warnings; as três alternativas continuam disponíveis para inspeção.
- Memory budget desconhecido, metadata UNKNOWN, ausência de FP16 ou ausência de MAILBOX não causam mutação nem seleção silenciosa.
- O A6xx/Adreno 619 conhecido recebe recomendação conservadora coerente porque seu resultado required é suportado; isso não depende do texto `Adreno 619`.

## Runtime mutation audit

- **CONFIRMADO ESTATICAMENTE:** `presets.hpp/.cpp` não acessam configuração nem chamam setters de multiplier, Flow Scale ou Performance Mode.
- A única integração runtime calcula a estrutura a partir de `vk.capabilities()` e imprime um resumo único com `applied=false`.
- `uiThreshold=0.50`, shaders, fractions, present policy, hot reload, external handles, command buffers, lifetime e sincronização não foram alterados.

## Risk assessment

- **BAIXO:** cálculo read-only uma vez por backend; três pequenos vetores de recomendações e strings, fora do hot path.
- **RISCO RESIDUAL:** as faixas são advisory e não substituem medição real do jogo. A confiança foi deliberadamente limitada para evitar falsa precisão.
- **RODADA 4 RECOMENDADA:** expor a estrutura como preview read-only para a integração e definir um fluxo de aplicação somente por ação explícita do usuário, preservando overrides manuais e sem auto-preset.

## Part 5 Round 4 - Preset Selector Integration

- **IMPLEMENTADO:** o diálogo LSFG agora apresenta `Performance | Balanced | Quality | Custom` junto de multiplier e Flow Scale.
- A aplicação acontece somente ao confirmar o diálogo depois de uma seleção explícita do usuário. Não existe seleção automática por GPU, jogo ou recommendation.
- O multiplier permanece um estado independente e não é alterado pelo preset.

## Performance Mode UI removal

- O checkbox `Performance Mode` e sua string específica foram removidos exclusivamente da UI LSFG.
- O writer do perfil runtime fixa `performance_mode = true`, preservando o caminho estável.
- Shaders, variantes m=2 e implementação backend de OFF não foram removidos nem refatorados.

## Legacy OFF handling

- Containers antigos com `lsfgPerformanceMode=0` são normalizados para ON antes da criação do perfil runtime e salvos uma vez.
- Confirmar qualquer configuração pelo novo diálogo também persiste ON.
- Isso evita que uma opção invisível e fisicamente instável continue congelando silenciosamente containers existentes.

## Performance preset

- Valor aplicado por escolha explícita: Flow Scale `0.20`.
- Motivo: pertence à faixa advisory `0.20–0.35` e possui evidência física de excelente custo no A6xx conhecido.
- A escolha não depende do nome Adreno/A6xx e Performance Mode permanece ON.

## Balanced preset

- Valor aplicado por escolha explícita: Flow Scale `0.40`.
- É um ponto conservador dentro da faixa advisory `0.35–0.50`, mantendo distância do custo superior de Quality.
- Performance Mode permanece ON.

## Quality preset

- Valor aplicado por escolha explícita: Flow Scale `0.65`.
- É o ponto central da faixa advisory `0.50–0.80`.
- Quality não ativa Performance Mode OFF e não altera multiplier ou uiThreshold.

## Custom state

- `custom` é um quarto estado persistente por container e o fallback para containers sem metadata de preset.
- Alterar manualmente o SeekBar de Flow Scale enquanto Performance/Balanced/Quality está selecionado muda imediatamente o seletor para Custom.
- Selecionar Custom não sobrescreve o Flow Scale atual.

## Explicit user application

- Selecionar Performance/Balanced/Quality atualiza somente o valor mostrado no diálogo; o perfil/container muda apenas na confirmação existente.
- Nenhum preset é aplicado durante capability collection, criação do backend, abertura do diálogo ou inicialização de container.
- A recomendação backend continua advisory. Não foi replicada na UI nem marcada como “Recommended”, evitando uma segunda implementação divergente sem uma ponte backend/Android.

## Persistence

- A chave `lsfgPreset` armazena `performance`, `balanced`, `quality` ou `custom` em `extraData` do container.
- Valores ausentes ou inválidos carregam como Custom, preservando o Flow Scale legado sem reinterpretá-lo.
- O mecanismo existente de confirmação, `conf.toml` atômico e `container.saveData()` continua responsável pelo reload/persistência.

## Runtime mutation audit

- Preset altera somente Flow Scale quando o usuário seleciona uma opção e confirma.
- Multiplier continua manual; não há chamada que o aumente ou reduza em resposta ao preset.
- Runtime sempre recebe Performance Mode ON. `uiThreshold=0.50`, Gamma/Delta/Generate, fractions, MAILBOX/FIFO, synchronization, external handles e lifetime não mudaram.
- A biblioteca nativa não precisou ser recompilada: a integração desta rodada é exclusivamente Android/UI/configuração.

## Regression risk

- **BAIXO/MÉDIO:** mudança localizada no diálogo e em `extraData`; o risco principal é interação/persistência da UI, não o pipeline GPU.
- Containers existentes entram como Custom e mantêm o Flow Scale salvo. A única migração automática é OFF legado para o caminho ON estável solicitado.
- **TESTE FÍSICO CURTO RECOMENDADO:** visibilidade do seletor, ausência do toggle, valores dos três presets, transição para Custom, persistência e inicialização LSFG.

## Part 5 Round 5 - Preset Stabilization

- A integração fisicamente validada da Rodada 4 foi preservada sem novos controles ou automação.
- A auditoria concentrou-se em `extraData`, validação do Flow Scale, estado exibido no diálogo e escrita atômica do perfil runtime.

## Persistence audit

- `lsfgPreset` e `lsfgFlowScale` são persistidos no `extraData` específico de cada container por `container.saveData()`.
- Ao reabrir o diálogo ou reiniciar o app, os getters reconstroem o estado usando os mesmos dados persistidos.
- Ao iniciar o container, `configureLSFGVK()` lê diretamente o multiplier e Flow Scale validados do container e escreve `conf.toml` antes de ativar a layer.

## Custom behavior

- Custom não possui valor próprio e nunca redefine Flow Scale.
- Edição manual pelo SeekBar muda o seletor para Custom; voltar manualmente a `0.20`, `0.40` ou `0.65` continua Custom, pois não houve seleção explícita de preset.
- Valores manuais válidos entre `0.20–1.00` são preservados.

## Flow Scale consistency

- Performance é consistente somente com `0.20`, Balanced somente com `0.40` e Quality somente com `0.65`.
- Foi adicionada validação central no `Container`: metadata nomeada divergente do Flow Scale efetivo é interpretada e persistida como Custom.
- Comparação usa tolerância pequena (`0.0001`) apenas para representação float; não aproxima outros valores a um preset.
- Flow Scale fora do range, NaN ou infinito continua usando o fallback seguro existente `0.80`; nesse caso qualquer metadata nomeada divergente aparece como Custom.

## Legacy container migration

- Containers anteriores aos presets não possuem `lsfgPreset` e carregam como Custom, preservando seu Flow Scale válido.
- Performance Mode ON legado permanece ON.
- Performance Mode OFF legado é normalizado e salvo como ON antes de criar o perfil runtime; nenhum Flow Scale ou multiplier é alterado nessa migração.
- Metadata desconhecida/corrompida cai em Custom sem crash e sem sobrescrever valor manual válido.

## New container defaults

- Multiplier default permanece `1`/Off.
- Flow Scale default permanece `0.80`, sem alteração silenciosa desta rodada.
- Preset ausente tem default Custom. Assim um novo container não recebe Performance/Balanced/Quality sem ação explícita.
- Performance Mode interno continua default ON.

## Hot reload behavior

- A confirmação reutiliza exclusivamente `applyLSFGVKConfig()` e o writer atômico existente `conf.toml.staging -> conf.toml`.
- Não foi criado segundo watcher, reload ou recreation path.
- Container e arquivo runtime recebem o mesmo Flow Scale validado na mesma operação; falha ao escrever o arquivo impede persistência parcial dos novos valores.

## UI state consistency

- O getter efetivo do preset valida a relação com o Flow Scale antes de selecionar o Spinner.
- Portanto a UI não mostra Quality com `0.20`, Performance com valor manual diferente ou Balanced com configuração corrompida.
- A inferência inversa é deliberadamente desativada: digitar exatamente um valor conhecido não seleciona preset automaticamente.

## Bugs found

- **CONFIRMADO ESTATICAMENTE:** a Rodada 4 confiava apenas na string `lsfgPreset`; alteração externa/corrupção poderia deixar a UI com preset nomeado e Flow Scale divergente.
- Não foram encontrados reload duplicado, perda do multiplier, reset de Custom ou quebra na migração OFF -> ON.

## Fixes applied

- `getLSFGPreset()` agora retorna Custom quando nome e Flow Scale não correspondem.
- `setLSFGPreset(preset, flowScale)` valida e persiste Custom para combinações inconsistentes.
- Nenhum valor válido de Flow Scale é modificado pela correção.

## Regression risk

- **BAIXO:** alteração restrita à validação de metadata do preset; não toca no runtime nativo ou pipeline GPU.
- Casos já consistentes mantêm exatamente o mesmo estado.
- Risco residual limita-se a persistência/UI; APK de teste curto é recomendado por existir correção funcional observável em dados divergentes.

## LSFG v1 Development Checkpoint

### Estado final

- Branch auditada: `feature/lsfg-vk`; remoto: `origin` (`https://github.com/amyroseee/Winlator.git`). Nenhum `git add`, commit ou push foi executado nesta rodada.
- Baseline funcional preservada: 2x, 3x e 4x; Tomb Raider 2013 e NFS 2012 são casos estáveis conhecidos. ETS2 continua com possível distorção em FPS-base baixo.
- Frações estáticas confirmadas pelo source: 2x = `1/2`; 3x = `1/3, 2/3`; 4x = `1/4, 2/4, 3/4`, calculadas por `(index + 1) / (total + 1)`.
- Optical flow/pre-pass roda uma vez por par de frames reais. Cada intermediate possui main pass, semaphore de conclusão, acquire/copy/present e sincronização próprios; frames gerados não substituem as duas source images alternadas.
- Command buffers dos main passes permanecem pré-gravados em seis variantes e são reutilizados após fence. Fences, semáforos binários exportáveis/importáveis `SYNC_FD`, external images `OPAQUE_FD`, layouts/lifetime e image-state tracking permanecem no patch funcional.
- Hot reload espera device idle, remove os contextos e solicita uma única recriação da swapchain. MAILBOX é usado somente quando anunciado; FIFO permanece fallback.

### Quality path e Performance Mode

- `uiThreshold=0.50F` foi confirmado no source aplicado; não existe literal LSFG residual `0.45` no estado funcional. As menções `0.45` anteriores neste relatório documentam somente o experimento revertido.
- Gamma, Delta0, Delta1, Generate, confidence/mix, SPIR-V embarcado e fractions foram preservados sem alteração nesta rodada final.
- O toggle Performance Mode não aparece mais no diálogo. O writer runtime fixa `performance_mode=true` e containers legados OFF são normalizados e salvos com segurança.
- O caminho/shaders OFF continuam internos e não foram removidos. OFF permanece `LEGACY / KNOWN BUG` e não é recomendado.

### Presets, persistência e capabilities

- Performance = Flow Scale `0.20`; Balanced = `0.40`; Quality = `0.65`; Custom preserva o valor manual.
- Multiplier permanece manual e independente. Presets não alteram multiplier nem `uiThreshold`; editar Flow Scale manualmente mantém Custom.
- `lsfgPreset` persiste por container. Containers antigos ou metadata inválida/divergente migram para Custom sem alterar um Flow Scale válido. Não há listener ou reload adicional.
- `GpuCapabilities` permanece read-only, coletado uma vez por backend e fora do hot path. Não habilita feature, não troca device, não aplica preset e não bloqueia por nome/família; Adreno 619 e metadata UNKNOWN não recebem bloqueio específico.
- Recomendações permanecem advisory e registram `applied=false`; nenhuma configuração é aplicada silenciosamente.

### Cleanup, privacidade e auditoria do repositório

- Removidos os diagnósticos temporários `LSFGDiagnostic`, logging em Documents, captura de stdout/stderr, telemetria periódica do HUD, `VK_LOADER_DEBUG` forçado e `DXVK_LOG_LEVEL/PATH` forçados pelo LSFG. Logging legítimo existente do Winlator foi preservado.
- `.gitignore` cobre SDK/ambiente local, Gradle/CMake/build caches, APK/AAB/APKS, logs, Python cache, `Lossless.dll`, `.spv`, `.spvasm` e reflection JSON sem esconder source C/C++, Java, resources, patch ou Gradle wrapper.
- Nenhum `Lossless.dll`, APK, SPIR-V extraído, `.spvasm`, reflection dump ou log está rastreado. A DLL do usuário permanece fora do repositório e não foi removida.
- Artefatos locais ignorados incluem `.android-sdk/`, `.gradle-build-cache/`, `app/.gradle/`, `app/app/.cxx/` e `app/app/build/`; eles não fazem parte do checkpoint.
- `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` e `gradle-wrapper.properties` estão rastreados e preservados.

### Native patch e biblioteca

- `tools/lsfg-vk-glibc/compatibility.patch` passa `git apply --check` sobre a source limpa fixada no commit upstream `8b0da2661c6f3473a7fccc8ba643880050e71642`.
- O patch não contém dependência de `/tmp` nem referência a `Lossless.dll` local. `build.sh` usa `/tmp` apenas como default descartável de build/source, não como conteúdo necessário ao patch ou ao repositório.
- `app/app/src/main/assets/lsfg-vk/liblsfg-vk.so` é ELF64 little-endian, shared object AArch64. O asset coincide byte a byte com a build nativa mais recente correspondente ao patch.
- SHA-256 `liblsfg-vk.so`: `856d6a0a08394b73bf685d89c1fe12ed503859d2ac7f8fc2470be65d5a3782a9`.
- A inspeção de strings não encontrou telemetria pesada, logging em Documents, dumps, `VK_LOADER_DEBUG` ou `DXVK_LOG`. Permanecem somente mensagens nativas agregadas/de erro e resumos one-shot de capabilities/recommendations.

### Build final

- `assembleDebug`: **BUILD SUCCESSFUL** em 25 de agosto de 2026; 34 tarefas, 2 executadas e 32 up-to-date.
- APK: `app/app/build/outputs/apk/debug/app-debug.apk`, package `com.winlator`, versionCode `32`, versionName `11.2`.
- SHA-256 APK: `93eaf806f67fb7a9428b2b863ec825d244ed7880766a5d01a34b11cd41f9c255`.
- `unzip -t`: nenhuma falha. O asset LSFG dentro do APK possui o mesmo SHA-256 da biblioteca source asset.
- O APK não contém `Lossless.dll`, `.spv`, `.spvasm` nem reflection JSON. O próprio APK permanece ignorado e fora do checkpoint.
- `git diff --check`: **PASS**.

### FILES TO COMMIT

- `.gitignore`
- `LSFG_ANALYSIS_REPORT.md`
- `app/app/src/main/assets/lsfg-vk/liblsfg-vk.so`
- `app/app/src/main/java/com/winlator/XServerDisplayActivity.java`
- `app/app/src/main/java/com/winlator/container/Container.java`
- `app/app/src/main/java/com/winlator/contentdialog/LSFGVKConfigDialog.java`
- Remoção de `app/app/src/main/java/com/winlator/core/LSFGDiagnostic.java`
- `app/app/src/main/java/com/winlator/widget/PerfHudView.java`
- `app/app/src/main/java/com/winlator/xenvironment/components/GuestProgramLauncherComponent.java`
- `app/app/src/main/res/layout/lsfg_vk_config_dialog.xml`
- `app/app/src/main/res/values/strings.xml`
- `app/app/src/main/res/values-pt/strings.xml`
- `tools/lsfg-vk-glibc/compatibility.patch`
- Gradle wrapper/config e demais sources/assets já rastreados permanecem necessários e preservados, mas não têm diff nesta rodada.

### FILES TO EXCLUDE

- `app/app/build/`, `**/build/`, `intermediates/`, `outputs/apk/`, `*.apk`, `*.aab`, `*.apks`
- `.gradle-build-cache/`, `**/.gradle/`, `**/.cxx/`, `.externalNativeBuild/`, caches e temporários locais
- `.android-sdk/`, `local.properties`, estado de IDE/Codespaces
- `*.log`, diretórios de logs, telemetria/dumps diagnósticos temporários
- `Lossless.dll`, `*.spv`, `*.spvasm`, reflection JSON e qualquer recurso proprietário extraído
