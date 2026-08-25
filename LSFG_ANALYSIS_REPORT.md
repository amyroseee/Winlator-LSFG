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
