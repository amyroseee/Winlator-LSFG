# Parte 7 / Rodada 1 — Smart Save, logo e Environment Variables

Escopo desta auditoria: análise somente. Nenhum menu, string de produção, logo, variável, runtime ou código LSFG foi alterado.

Base efetivamente auditada no checkout: app `versionName 11.2`, Wine custom `10.10`, Box64 `0.4.4`, Turnip `26.1.0`, DXVK `1.10.3`/`2.4.1` e VKD3D-Proton `2.14.1`. O `AGENTS.md` ainda descreve a base como Winlator 11.1.0; essa divergência deve ser resolvida documentalmente antes da Rodada 2, sem atualizar componentes.

## Smart Save Feasibility

**Viável**, desde que seja uma operação explícita por container, com seleção/revisão antes da cópia, manifesto versionado e acesso Android por SAF. Não é seguro prometer descoberta automática perfeita: jogos podem salvar ao lado do executável, em um drive externo ou em caminhos próprios. A UI deve separar achados de alta confiança de **Possible save data**.

O scanner deve rodar somente ao tocar em **Backup saves**, em uma tarefa curta com progresso/cancelamento, sem watcher, serviço, índice persistente ou scan do container inteiro.

## Winlator Wine Prefix Structure

Fontes locais: `RootFS.java`, `Container.java`, `WineUtils.java` e o conteúdo de `container_pattern.tzst`.

- O rootfs privado fica em `context.getFilesDir()/rootfs`.
- `RootFS.USER = xuser`, `HOME_PATH = /home/xuser` e `WINEPREFIX = /home/xuser/.wine`.
- Cada `Container.rootDir` representa `/home/xuser`; portanto o prefixo real de um container fica em `<containerRoot>/.wine`.
- O usuário Windows real é `<containerRoot>/.wine/drive_c/users/xuser`.
- `Container.getUserDir()` confirma esse caminho; `getStartMenuDir()` confirma `.wine/drive_c/ProgramData/...`.
- `.wine/dosdevices/c:` é symlink para `../drive_c`.
- `.wine/dosdevices/z:` é recriado para `../../../../`, isto é, o rootfs privado.
- `x:` pode apontar para `.wine/drive_x` (CD-ROM).
- `D:`, `E:` e outros drives configurados são symlinks para diretórios Android. Os defaults atuais são Downloads e armazenamento interno. Eles não pertencem necessariamente ao prefixo e nunca devem ser seguidos recursivamente sem consentimento.
- `container_pattern.tzst` contém diretórios reais para `Documents`, `Saved Games`, `AppData/Roaming`, `AppData/Local`, `AppData/LocalLow` e `ProgramData`; não são suposições de outro fork.
- Não há redirect/symlink de `Documents`, `Saved Games` ou `AppData` no pattern atual: são diretórios dentro de `drive_c`. Só `dosdevices` faz os redirects constatados.

O manifesto deve usar caminhos relativos ao `containerRoot`, nunca caminhos Android absolutos ou o alias `C:`. Isso torna a restauração independente do caminho privado escolhido pelo Android.

## Save Locations

Ordem recomendada de inspeção, sempre limitada:

1. `.wine/drive_c/users/xuser/Saved Games/`
2. `.wine/drive_c/users/xuser/Documents/` (incluindo `My Games` se criado pelo jogo)
3. `.wine/drive_c/users/xuser/AppData/Roaming/`
4. `.wine/drive_c/users/xuser/AppData/Local/`
5. `.wine/drive_c/users/xuser/AppData/LocalLow/`
6. `.wine/drive_c/ProgramData/`
7. Diretório do executável/atalho somente quando houver evidência de uma subpasta de save/config ou extensões conhecidas; nunca copiar a instalação inteira.
8. Drives adicionais somente se o executável ou evidência do jogo apontar para eles e após autorização explícita, pois podem alcançar todo o armazenamento Android.

`Desktop`, `Downloads`, `Public`, `.cache`, `.config`, `windows`, `Program Files` e `Program Files (x86)` não entram na busca padrão. Um jogo comprovadamente instalado em outra pasta pode produzir candidatos locais, mas com confiança baixa/média e revisão obrigatória.

## Save Detection Strategy

Detecção em camadas e por pontuação:

- **Alta confiança:** descendente não genérico de `Saved Games`; pasta em `Documents/My Games`; nome normalizado correspondente ao nome do atalho, executável, produto ou publisher comprovado.
- **Média confiança:** correspondência de nome em `Documents`, `Roaming`, `Local`, `LocalLow` ou `ProgramData`, contendo arquivos plausíveis (`.sav`, `.save`, `.dat`, `.bin`, `.json`, `.xml`, `.ini`, bancos pequenos) e atividade recente.
- **Baixa confiança / Possible save data:** apenas recência ou nome aproximado dentro de um local plausível. Nunca selecionar automaticamente.

Fontes de identidade, por força: nome/propriedades do atalho e caminho do executável; metadados PE quando disponíveis; nomes de diretórios ancestrais; publisher apenas quando derivado desses dados. Não inferir publisher por lista inventada.

A recência é desempate, não prova. O scanner deve aplicar limite de profundidade, contagem e tamanho; calcular o tamanho antes de confirmar; não atravessar symlinks; deduplicar por caminho canônico; e rejeitar qualquer caminho que escape das raízes permitidas.

## False Positive Prevention

Exclusões por segmento/nome, case-insensitive, com possibilidade de revisão manual:

- `.cache`, `cache`, `shadercache`, `ShaderCache`, `GLCache`, `GPUCache`, `DawnCache`, `Code Cache`;
- arquivos `*.dxvk-cache`, cache VKD3D, Mesa shader cache e pipelines;
- `Temp`, `tmp`, dumps, logs, crash reports, telemetry e thumbnails;
- `windows`, `system32`, `syswow64`, DLLs, EXEs, MSIs, redistributables e arquivos de instalação;
- diretórios grandes que parecem conteúdo (`assets`, `data`, `pak`, `movies`, `textures`) sem evidência adicional.

Não excluir cegamente todo `.ini`, `.json`, `data` ou `config`: alguns jogos guardam progresso neles. Eles só ajudam quando combinados com localização, identidade e tamanho. Diretórios acima de um limite configurado (sugestão inicial: aviso em 256 MiB, nunca autocheck acima de 1 GiB) devem exigir revisão.

## Backup Manifest

Formato recomendado: JSON UTF-8 chamado `backup.json`, schema estrito e versionado. `data/` deve espelhar cada entry por um identificador estável, sem colisão.

```json
{
  "schema_version": 1,
  "created_at": "2026-08-25T09:30:00Z",
  "app": { "package": "com.winlator", "version": "11.2" },
  "container": {
    "id_at_backup": 1,
    "name": "Container-1",
    "wine_version": "wine-10.10-custom"
  },
  "game": {
    "display_name": "GameName",
    "executable": "drive_c/Games/GameName/game.exe"
  },
  "entries": [
    {
      "id": "e0001",
      "source": ".wine/drive_c/users/xuser/Documents/GameName",
      "restore_target": ".wine/drive_c/users/xuser/Documents/GameName",
      "kind": "directory",
      "confidence": "high",
      "size": 12345,
      "file_count": 4,
      "sha256": "manifest-or-tree-digest",
      "data_path": "data/e0001"
    }
  ]
}
```

Regras: caminhos relativos normalizados, `/` como separador, sem `..`, sem início `/`, sem symlink e com allowlist de raízes. `source` preserva a origem auditável e `restore_target` é o destino exato. Hashes por arquivo em uma lista adicional são preferíveis a um único hash de árvore para validar restauração e detectar corrupção. O container ID é informativo, não requisito absoluto; nome, Wine, arquitetura futura e layout determinam compatibilidade.

## Restore Strategy

1. Abrir a pasta/documento pelo SAF e validar `backup.json`, schema, tipos, limites, hashes e presença de `data/`.
2. Rejeitar path traversal, paths absolutos, symlinks, entries duplicadas/sobrepostas e destinos fora do container selecionado.
3. Comparar versão/schema/layout e exibir incompatibilidade como aviso ou bloqueio conforme a gravidade.
4. Mostrar resumo com origem, destino, tamanho e conflitos.
5. Se houver conflitos, oferecer **Backup current saves first** (preferido) ou sobrescrever após confirmação explícita. Nunca apagar silenciosamente.
6. Copiar para staging privado, verificar hash e espaço, e então substituir por entry. Em falha, preservar o estado anterior; um journal permite rollback.
7. Restaurar cada `data/eNNNN` exclusivamente em seu `restore_target`; nunca consolidar tudo em Documents.

## Android Documents / SAF

`Documents/Winlator/Saves/` é um destino de UX válido, mas acesso direto não é universalmente seguro. O app usa `targetSdkVersion 28` e hoje grava logs diretamente em public Documents, além de declarar `READ/WRITE_EXTERNAL_STORAGE`; isso é legado. Já existem usos de `ACTION_OPEN_DOCUMENT` e `ACTION_OPEN_DOCUMENT_TREE`, mas não há uma abstração de backup baseada em `DocumentFile`.

Rodada 2 deve usar `ACTION_OPEN_DOCUMENT_TREE` para o usuário escolher/criar `Documents/Winlator/Saves` (ou outra pasta), chamar `takePersistableUriPermission`, persistir a tree URI e operar via `ContentResolver`/`DocumentFile`. Não depender de caminho real do URI. Para restore, também pode usar a tree persistida ou um picker. Em Android atual, SAF evita depender de permissões amplas e continua válido com scoped storage.

Estrutura sugerida: `Saves/<game-slug>/<UTC timestamp>/backup.json` e `data/`. O nome visível deve ser saneado, mas identidade e caminhos verdadeiros ficam no manifesto. Escrita deve ocorrer em pasta temporária/incompleta e ser marcada completa por último.

## Container Menu Integration

O menu correto é `res/menu/container_popup_menu.xml`, inflado por `ContainersFragment.ContainersAdapter.showListItemMenu()` ao tocar em `BTMenu` (os três pontos) de `container_list_item.xml`.

Na Rodada 2, adicionar somente `menu_item_backup_saves` e `menu_item_restore_saves` nesse XML, com `android:title` apontando para resources, e tratar os IDs no mesmo switch de `showListItemMenu()`. Não adicionar nada a `ContainerDetailFragment`/Settings e não mover itens existentes.

## Localization Architecture

O app seleciona idioma em `SettingsFragment`, usando `LocaleHelper.supportedLocales`; `attachBaseContext(LocaleHelper.setSystemLocale(...))` aplica o resource locale às Activities. Portanto toda string visível nova deve ser `context.getString(R.string...)` ou `@string/...`; os nomes técnicos continuam literais.

Há lacunas antigas entre os arquivos (EN 339 strings, PT 307, RU 285 na contagem desta auditoria), resolvidas por fallback para inglês. Para Smart Save, isso não é aceitável: cada chave nova deve entrar simultaneamente nos três arquivos e ser verificada por script/lint de paridade do conjunto novo.

Descrições de env vars não devem ser construídas a partir do nome técnico. Usar uma tabela tipada (`name`, `labelRes`, `descriptionRes`, valores) e exibir o nome técnico separadamente.

## Existing Supported Locales

Exatamente três, conforme `LocaleHelper` e `@array/language_entries`:

| Locale lógico | UI | Arquivo |
|---|---|---|
| `en_US` (default) | English | `app/app/src/main/res/values/strings.xml` |
| `pt_BR` | Português | `app/app/src/main/res/values-pt/strings.xml` |
| `ru_RU` | Русский | `app/app/src/main/res/values-ru/strings.xml` |

Os qualifiers são por idioma (`values-pt`, `values-ru`), logo também atendem variantes do idioma; `LocaleHelper` hoje cria apenas `new Locale("en"|"pt"|"ru")`, apesar dos nomes lógicos com país.

## Smart Save String Keys

Nomenclatura proposta segue o padrão snake_case existente. Este é o conjunto planejado, não aplicado aos XMLs nesta rodada:

| Key | English | Português | Русский |
|---|---|---|---|
| `backup_saves` | Backup saves | Backup de saves | Создать резервную копию сохранений |
| `restore_saves` | Restore saves | Restaurar saves | Восстановить сохранения |
| `save_backup` | Save backup | Backup de saves | Резервная копия сохранений |
| `save_restore` | Save restore | Restauração de saves | Восстановление сохранений |
| `searching_for_save_data` | Searching for save data | Procurando dados de saves | Поиск данных сохранений |
| `save_data_found` | Save data found | Dados de saves encontrados | Данные сохранений найдены |
| `possible_save_data` | Possible save data | Possíveis dados de saves | Возможные данные сохранений |
| `no_save_data_found` | No save data found | Nenhum dado de save encontrado | Данные сохранений не найдены |
| `confirm_save_backup` | Confirm backup | Confirmar backup | Подтвердить создание резервной копии |
| `save_backup_completed` | Backup completed | Backup concluído | Резервная копия создана |
| `save_backup_failed` | Backup failed | Falha no backup | Не удалось создать резервную копию |
| `confirm_save_restore` | Confirm restore | Confirmar restauração | Подтвердить восстановление |
| `save_restore_completed` | Restore completed | Restauração concluída | Восстановление завершено |
| `save_restore_failed` | Restore failed | Falha na restauração | Не удалось восстановить сохранения |
| `existing_files_will_be_overwritten` | Existing files will be overwritten | Os arquivos existentes serão sobrescritos | Существующие файлы будут перезаписаны |
| `backup_current_saves_first` | Backup current saves first | Fazer backup dos saves atuais primeiro | Сначала создать резервную копию текущих сохранений |
| `select_save_backup` | Select backup | Selecionar backup | Выбрать резервную копию |
| `invalid_save_backup` | Invalid backup | Backup inválido | Недопустимая резервная копия |
| `incompatible_save_backup` | Incompatible backup | Backup incompatível | Несовместимая резервная копия |
| `source_path` | Source path | Caminho de origem | Исходный путь |
| `restore_path` | Restore path | Caminho de restauração | Путь восстановления |
| `backup_date` | Backup date | Data do backup | Дата резервного копирования |
| `backup_size` | Backup size | Tamanho do backup | Размер резервной копии |

Termos `AppData`, `ProgramData`, `LocalLow`, `Roaming`, `DXVK`, `VKD3D`, `Mesa`, `Turnip`, `LSFG` e nomes `BOX64_*` permanecem inalterados em qualquer texto.

## logo.png Usage

Existem dois arquivos diferentes nesta auditoria:

- `./logo.png`: imagem fornecida, PNG RGBA 376×128, com transparência.
- `app/app/src/main/res/drawable-hdpi/logo.png`: logo atual, também PNG RGBA 376×128.

A única referência Android encontrada para `@drawable/logo` está em `res/layout/main_menu_header.xml`. A imagem usa `ImageView` 153×52 dp, proporção praticamente idêntica à imagem (2,941 vs. 2,942), sem `scaleType` explícito. Ainda assim, a Rodada 2 deve declarar `android:scaleType="fitCenter"` e `adjustViewBounds` apenas se necessário, preservando transparência e sem crop/stretch.

## Main Drawer Logo

`res/layout/main_activity.xml` define a `NavigationView` principal com `app:headerLayout="@layout/main_menu_header"`. Logo, usa o drawable acima.

## XServer Drawer Logo

`res/layout/xserver_display_activity.xml` define a `NavigationView` runtime com o mesmo `app:headerLayout="@layout/main_menu_header"`. Logo, a substituição única de `res/drawable-hdpi/logo.png` atualiza os dois drawers. O launcher é independente: o Manifest aponta para `@mipmap/ic_launcher`; nenhum `mipmap/ic_launcher*` deve ser tocado.

## Existing Environment Variables

“Exposta” foi separada de “injetada internamente”. O editor genérico também aceita nomes arbitrários, mas os seguintes possuem suporte conhecido ou são defaults atuais.

**ALREADY PRESENT — editor/defaults do container:** `ZINK_DESCRIPTORS`, `ZINK_DEBUG`, `ZINK_CONTEXT_THREADED`, `WINEESYNC`, `WINEDLLOVERRIDES`, `TU_DEBUG`, `DXVK_HUD`, `DXVK_LOG_LEVEL`, `DXVK_ASYNC`, `GALLIUM_HUD`, `MESA_SHADER_CACHE_DISABLE`, `MESA_SHADER_CACHE_MAX_SIZE` (default, embora ausente de `knownEnvVars`), `mesa_glthread`, `MESA_EXTENSION_MAX_YEAR`, `MESA_GL_VERSION_OVERRIDE`, `PULSE_LATENCY_MSEC`.

**ALREADY PRESENT — Box64 preset editor:** `BOX64_DYNAREC_SAFEFLAGS`, `BOX64_DYNAREC_FASTNAN`, `BOX64_DYNAREC_FASTROUND`, `BOX64_DYNAREC_X87DOUBLE`, `BOX64_DYNAREC_BIGBLOCK`, `BOX64_DYNAREC_STRONGMEM`, `BOX64_DYNAREC_FORWARD`, `BOX64_DYNAREC_CALLRET`, `BOX64_DYNAREC_WAIT`, `BOX64_DYNAREC_NATIVEFLAGS`, `BOX64_DYNAREC_WEAKBARRIER`.

**ALREADY PRESENT — telas específicas:** `TU_OVERRIDE_HEAP_SIZE`, `MESA_VK_WSI_USE_HWBUF`, `MESA_VK_WSI_FORCE_WAIT_FOR_FENCES`, `TU_DEBUG=sysmem|gmem`; `MESA_EXTENSION_OVERRIDE`; `VKD3D_FEATURE_LEVEL`.

**ALREADY PRESENT — pipeline, não duplicar em UI:** `DXVK_STATE_CACHE_PATH`, `DXVK_CONFIG_FILE`, `VKD3D_SHADER_CACHE_PATH`, `VKD3D_DEBUG`, `MESA_VK_WSI_PRESENT_MODE`, `MESA_DEBUG`, `MESA_NO_ERROR`, `BOX64_LD_LIBRARY_PATH`, `BOX64_NOBANNER`, `BOX64_DYNAREC`, `BOX64_UNITYPLAYER`, `BOX64_DYNACACHE`, `BOX64_LOG`, `BOX64_DYNAREC_MISSING`, `BOX64_SHOWSEGV`, `BOX64_DLSYM_ERROR`, `BOX64_TRACE_FILE`, `BOX64_RCFILE`, `LC_ALL`, e integrações específicas como `CNC_DDRAW_CONFIG_FILE`.

Essa lista impede duplicar `SAFEFLAGS`, “heap size”, present mode, caches ou opções que já têm tela própria.

## Candidate New Variables

Resultado conservador: **uma candidata NORMAL**, duas ADVANCED e várias documentadas como DO NOT EXPOSE. “Suportada” abaixo significa verificada no componente empacotado ou na documentação da versão, não apenas na documentação atual.

### `DXVK_FRAME_RATE`

- **Name:** `DXVK_FRAME_RATE`
- **Component:** DXVK
- **Source:** DXVK upstream; uso confirmado em DXVK 1.10.1 e mantido no período de 2.4.1. A remoção ocorreu apenas em versão upstream posterior (2025), portanto não afeta os pacotes atuais.
- **Version supported:** pacotes do projeto `1.10.3` e `2.4.1`.
- **Valid values:** inteiro; `0` desabilita/default, `N > 0` limita a N FPS. Não oferecer valores negativos sem validar semântica em ambas as versões.
- **Default:** não definido / `0` (sem limite imposto pela variável).
- **Performance effect:** reduz carga, calor e consumo quando o jogo renderiza acima do alvo; não aumenta FPS.
- **Compatibility effect:** pode contornar jogos cuja simulação depende da taxa de quadros.
- **Potential risk:** frame pacing pior, latência e conflito com VSync/HUD/limitadores externos.
- **Why expose:** controle simples, real, relevante em Android e ausente de `knownEnvVars`.
- **Recommended category:** **NORMAL**, desligado por padrão.
- **UI label key:** `env_var_dxvk_frame_rate_label`
- **UI description key:** `env_var_dxvk_frame_rate_description`

### `BOX64_DYNAREC_ALIGNED_ATOMICS`

- **Name:** `BOX64_DYNAREC_ALIGNED_ATOMICS`
- **Component:** Box64 ARM64 dynarec
- **Source:** Box64 upstream `box64.pod`; o nome também existe literalmente no binário `box64-0.4.4.tzst` empacotado.
- **Version supported:** Box64 `0.4.4` do projeto.
- **Valid values:** `0`, `1`.
- **Default:** `0`.
- **Performance effect:** pode usar caminho mais rápido para atômicos quando alinhamento é seguro.
- **Compatibility effect:** `1` assume atômicos alinhados; software que viola a premissa pode falhar.
- **Potential risk:** crashes/corrupção ou regressões difíceis de diagnosticar; ganho depende do workload/CPU, não da GPU.
- **Why expose:** workaround/performance avançado verificável, útil apenas com perfil por jogo e explicação forte.
- **Recommended category:** **ADVANCED**, nunca habilitado por padrão.
- **UI label key:** `env_var_box64_dynarec_aligned_atomics_label`
- **UI description key:** `env_var_box64_dynarec_aligned_atomics_description`

### `BOX64_MAXCPU`

- **Name:** `BOX64_MAXCPU`
- **Component:** Box64
- **Source:** Box64 upstream `box64.pod`; presente no binário 0.4.4 e usado em regras do `.box64rc` upstream.
- **Version supported:** Box64 `0.4.4`.
- **Valid values:** inteiro positivo (número máximo de CPUs reportadas); UI deve limitar a `1..availableProcessors`, não aceitar texto livre.
- **Default:** não definido, reporta a topologia normal.
- **Performance effect:** nenhum ganho geral garantido; alguns jogos escalam pior com muitos cores reportados.
- **Compatibility effect:** workaround para software sensível à contagem de CPUs.
- **Potential risk:** menos paralelismo, pior performance ou afinidade inesperada.
- **Why expose:** workaround real por jogo, mas a UI de CPU affinity existente pode confundir; deve ser claramente distinto.
- **Recommended category:** **ADVANCED**, unset por padrão.
- **UI label key:** `env_var_box64_maxcpu_label`
- **UI description key:** `env_var_box64_maxcpu_description`

## Box64 Candidates

Além das duas candidatas acima:

- `BOX64_DYNACACHE`: **DO NOT EXPOSE nesta base**. É real (`0|1|2`), mas o launcher força `0`; uma opção visual sem revisar essa decisão seria enganosa e alteraria política de cache/runtime.
- `BOX64_DYNAREC_VOLATILE_METADATA`: **DO NOT EXPOSE**. Real no binário 0.4.4, default seguro automático; desligá-la tende a perder informação útil de ordenação de memória.
- `BOX64_DYNAREC_BLEEDING_EDGE`, `DIRTY`, `NOHOTPAGE`, `INTERP_SIGNAL`: **DO NOT EXPOSE** sem matriz de jogos e validação da exata semântica 0.4.4; são knobs de implementação/risco.
- trace, dump, GDB, missing-symbol e log vars: **DO NOT EXPOSE** na UI normal; debug-only, custo alto e/ou arquivos enormes.

Fonte primária: [Box64 box64.pod](https://github.com/ptitSeb/box64/blob/main/docs/box64.pod). A Rodada 2 deve fixar links no tag/commit exato usado para compilar o artefato 0.4.4, se esse provenance estiver disponível.

## Mesa / Turnip Candidates

Não foi encontrada uma variável “A6xx/A7xx/A8xx boost” real. As opções Turnip reais já expostas são heap override, hardware buffer, wait-for-fences e flags `TU_DEBUG` (`sysmem`/`gmem` etc.). `MESA_VK_WSI_PRESENT_MODE` já é definido pelo pipeline em contexto específico.

- `MESA_DISK_CACHE_SINGLE_FILE`: **DO NOT EXPOSE por enquanto**. É real em Mesa moderno, booleano, mas falta comprovar no build exato Turnip 26.1.0 empacotado e medir efeito no Android; muda formato/comportamento do cache.
- `MESA_SHADER_CACHE_DIR`: **DO NOT EXPOSE**; path técnico, SAF não fornece filesystem path compatível e um path errado prejudica cache.
- `MESA_VK_DEVICE_SELECT`: **DO NOT EXPOSE**; seleção de dispositivo, não performance por família Adreno.
- `TU_DEBUG` flags adicionais: **DO NOT EXPOSE como novas features**; já existe seleção múltipla e muitas flags são debug/workaround perigosas.

Fonte primária geral: [Mesa environment variables](https://docs.mesa3d.org/envvars.html). Para flags Turnip, exigir source do tag Mesa/Turnip exato; documentação genérica não basta.

## DXVK Candidates

`DXVK_FRAME_RATE` é a única recomendação NORMAL. `DXVK_HUD`, `DXVK_LOG_LEVEL`, `DXVK_ASYNC`, `DXVK_CONFIG_FILE` e state cache já existem no app/pipeline.

- Opções `dxgi.*`, `d3d9.*` e `d3d11.*` são chaves de `dxvk.conf`, não environment variables; não renomeá-las como env vars.
- `DXVK_FILTER_DEVICE_NAME`: real, mas **DO NOT EXPOSE** em dispositivo Android comum; seleção incorreta pode impedir criação do device.
- Debug/log vars adicionais: **DO NOT EXPOSE** na UI normal.

Fontes primárias: [DXVK configuration](https://github.com/doitsujin/dxvk/wiki/Configuration) e [DXVK releases](https://github.com/doitsujin/dxvk/releases).

## VKD3D Candidates

Nenhuma candidata NORMAL nesta rodada. `VKD3D_FEATURE_LEVEL`, cache path e debug level já são usados.

### `VKD3D_CONFIG`

- **Name:** `VKD3D_CONFIG`
- **Component:** VKD3D-Proton
- **Source:** README upstream do VKD3D-Proton 2.14.x.
- **Version supported:** `2.14.1`.
- **Valid values:** lista separada por vírgula/ponto e vírgula; opções incluem `nodxr`, `dxr`, `single_queue`, `no_upload_hvv`, `pipeline_library_app_cache` e opções debug. Cada valor precisa allowlist por versão.
- **Default:** vazio/auto.
- **Performance effect:** varia; algumas opções desabilitam filas ou mudam cache e podem reduzir desempenho.
- **Compatibility effect:** workarounds específicos, por exemplo desabilitar DXR ou evitar host-visible VRAM.
- **Potential risk:** upstream declara env vars instáveis e em grande parte destinadas a debug; `dxr` pode forçar recurso inseguro.
- **Why expose:** somente presets de workaround individualmente documentados, nunca caixa genérica na UI normal.
- **Recommended category:** **DO NOT EXPOSE** nesta Rodada 2; reconsiderar opções isoladas após testes.
- **UI label key:** `env_var_vkd3d_config_label`
- **UI description key:** `env_var_vkd3d_config_description`

Fonte primária: [VKD3D-Proton README](https://github.com/HansKristian-Work/vkd3d-proton/blob/v2.14.1/README.md). A release 2.14.1 inclui inclusive correção específica de crash em GPUs sem sparse, como Turnip, reforçando a necessidade de não forçar flags indiscriminadamente.

## Wine Candidates

Nenhuma nova candidata de performance deve ser exposta agora.

- `WINEDEBUG`: real, valores por canais, default Wine conhecido, mas **DO NOT EXPOSE como performance**; o app já possui infraestrutura própria de canais/log e debug pode gerar enorme I/O.
- `WINEESYNC`: **ALREADY PRESENT** e depende dos patches/build custom, portanto não duplicar.
- `WINEFSYNC`: **DO NOT EXPOSE** até comprovar suporte no Wine custom 10.10 e kernel/integração Android exatos. A existência em Proton/forks não prova existência nesta build.
- `WINEDLLOVERRIDES`: **ALREADY PRESENT**.
- `WINEPREFIX`, `WINEPATH`, `WINEDLLOVERRIDES` internos e locale: não transformar em opções amigáveis sem caso específico.

Fonte primária para comportamento geral: [Wine User Guide — environment variables](https://gitlab.winehq.org/wine/wine/-/wikis/Wine-User%27s-Guide). Para esta build custom, somente o source/build recipe exato pode comprovar patches não upstream.

## Env Var Localization Keys

Chaves mínimas para candidatas recomendadas/avaliadas:

| Variable | Label key | Description key |
|---|---|---|
| `DXVK_FRAME_RATE` | `env_var_dxvk_frame_rate_label` | `env_var_dxvk_frame_rate_description` |
| `BOX64_DYNAREC_ALIGNED_ATOMICS` | `env_var_box64_dynarec_aligned_atomics_label` | `env_var_box64_dynarec_aligned_atomics_description` |
| `BOX64_MAXCPU` | `env_var_box64_maxcpu_label` | `env_var_box64_maxcpu_description` |
| `VKD3D_CONFIG` (documentação apenas) | `env_var_vkd3d_config_label` | `env_var_vkd3d_config_description` |

Os três `strings.xml` devem receber as mesmas chaves no mesmo change. A label traduz o conceito (“Frame rate limit” etc.); o nome técnico aparece inalterado em campo separado. Valores técnicos (`sysmem`, `gmem`, `nodxr`) também não devem ser traduzidos; sua explicação pode ser.

## A6xx / A7xx / A8xx Notes

A6xx/A7xx/A8xx são famílias Adreno, não opções Box64. Não existe no código ou nas fontes auditadas uma env var chamada `A6xx Boost`, `GPU Turbo`, `A7xx Performance Mode`, `A8xx Booster` ou equivalente. Não criar nenhuma.

Turnip possui opções reais de Vulkan/Mesa, mas elas representam comportamento do driver e capacidades/extensões, não um “boost” universal por família. `TU_OVERRIDE_HEAP_SIZE` pode contornar memória reportada; `sysmem/gmem` muda o modo de renderização; hardware buffer e wait-for-fences são compatibilidade/integração. Todas já estão presentes. A escolha correta depende de GPU, driver e jogo; não há base para afirmar ganho em A7xx/A8xx, e nenhum teste físico foi realizado nesta auditoria.

## Risk Matrix

| Item | Benefit | Risk | Category / decision |
|---|---|---|---|
| Smart Save roots + review | Alto | Médio (false positive) | Round 2, com score e revisão |
| SAF tree in Documents | Alto | Baixo/Médio | Round 2, persistable URI |
| Manifest + hashes + exact target | Alto | Baixo | Obrigatório |
| Follow symlinks/external drives | Baixo | Crítico | Proibido por default |
| `DXVK_FRAME_RATE` | Médio | Baixo/Médio | NORMAL, off por default |
| `BOX64_DYNAREC_ALIGNED_ATOMICS` | Incerto/Médio | Alto | ADVANCED |
| `BOX64_MAXCPU` | Específico | Médio | ADVANCED |
| `VKD3D_CONFIG` genérico | Específico | Alto | DO NOT EXPOSE |
| Mesa/Turnip debug flags novas | Incerto | Alto | DO NOT EXPOSE |
| “A6xx/A7xx/A8xx boost” fictício | Nenhum | Crítico/desinformação | Não existe / proibido |
| Trocar `drawable/logo.png` | Alto/visual | Baixo | Round 2; não tocar mipmap |

## Recommended Round 2

Escopo exato recomendado, sem outras features:

1. Adicionar as 23 chaves Smart Save listadas aos três `strings.xml`, com teste de presença/paridade; adicionar somente traduções necessárias às env vars efetivamente aprovadas.
2. Substituir `res/drawable-hdpi/logo.png` pelo `./logo.png` fornecido e validar ambos os drawers em portrait/landscape; não tocar `mipmap`/Manifest.
3. Adicionar somente Backup/Restore a `container_popup_menu.xml` e ao switch de `ContainersFragment`.
4. Implementar SAF tree persistida para destino, sem depender de acesso direto a public Documents.
5. Implementar scanner on-demand limitado às raízes reais, sem seguir symlinks, com score, exclusões, limites e tela de revisão.
6. Implementar manifesto schema 1, staging, hashes, validação antitraversal, restore por `restore_target`, conflito explícito e opção de backup prévio.
7. Para env vars, começar apenas com `DXVK_FRAME_RATE` NORMAL. Colocar `BOX64_DYNAREC_ALIGNED_ATOMICS` e `BOX64_MAXCPU` atrás de seção ADVANCED somente se o usuário aprovar explicitamente e após testes de regressão. Não expor `VKD3D_CONFIG` genérico nem novas flags Mesa/Turnip.
8. Testar backup/restore com saves distribuídos entre Documents, Saved Games, Roaming, Local, LocalLow e ProgramData; corrupção, manifesto malicioso, pouco espaço, cancelamento, reinstalação e container com ID diferente.

Fora do escopo: LSFG/framegen, launch pipeline, launcher icon, atualização de Wine/Box64/DXVK/VKD3D/Turnip/Mesa, NDK/JNI/CMake/rootfs, serviço background, cloud sync e scan contínuo.

## Runtime FPS Limiter Audit

Esta auditoria complementar aplica a regra mais restritiva: uma opção **FPS Limit** no drawer do XServer só é aceitável se alterar o limiter dentro do processo DXVK já ativo. Alterar `DXVK_FRAME_RATE`, reescrever `dxvk.conf` ou salvar a configuração do container depois do launch **não é live update**.

Foram comparados os artefatos realmente empacotados (inclusive strings presentes nos DLLs 32/64-bit) com os sources oficiais dos tags correspondentes. Os SHA-256 dos pacotes auditados são:

- `dxvk-2.4.1.tzst`: `897cc48500241006c15c62f200e9a6e1ea8a674bd285da25df6f68fdcdbfe42e`
- `dxvk-1.10.3.tzst`: `18ed7c263e0d52c4bbd0e7345b4f22908c10966f37d7d6d80c639ec45123075a`

Resultado resumido: o limiter e um setter thread-safe já existem dentro de ambas as versões, mas não há canal externo nem reload. Portanto o estado atual não é LIVE SUPPORTED. Há, contudo, um caminho isolado para conectar um pequeno controle runtime ao setter existente, sem modificar o algoritmo de pacing: **LIVE POSSIBLE WITH SMALL SAFE PATCH**, condicionado a compilar e validar DLLs 32/64-bit para as duas versões suportadas. Nada foi implementado nesta auditoria.

Esta conclusão também substitui, para fins do drawer runtime, a recomendação anterior de simplesmente expor `DXVK_FRAME_RATE`: essa env var continua real e útil como configuração de launch, mas não satisfaz o requisito live e não deve ser usada para fingir hot reload.

## DXVK Version

O fork possui dois defaults selecionados por `DefaultVersion.DXVK(graphicsDriver)`:

- **DXVK 2.4.1** (`MAJOR_DXVK`), default quando Turnip está ativo ou quando outro driver reporta Vulkan 1.3+.
- **DXVK 1.10.3** (`MINOR_DXVK`), fallback para o caminho que não satisfaz Vulkan 1.3, especialmente Vortek conforme a seleção atual.

O container pode também referenciar componentes DXVK instaláveis, mas esses não podem receber a feature automaticamente: a opção runtime deve ser habilitada somente para um package/version explicitamente recompilado com o protocolo de controle. “DXVK ativo” sozinho não basta.

Nos dois DLL sets empacotados existem `DXVK_FRAME_RATE`, `dxgi.maxFrameRate` e `d3d9.maxFrameRate`. Os sources oficiais confirmam:

- [DXVK 2.4.1 README](https://github.com/doitsujin/dxvk/blob/v2.4.1/README.md) e [dxvk.conf](https://github.com/doitsujin/dxvk/blob/v2.4.1/dxvk.conf)
- [DXVK 1.10.3 README](https://github.com/doitsujin/dxvk/blob/v1.10.3/README.md) e [dxvk.conf](https://github.com/doitsujin/dxvk/blob/v1.10.3/dxvk.conf)

Não existe uma chave `dxvk.maxFrameRate` nesses dois tags. As chaves reais são `dxgi.maxFrameRate` e `d3d9.maxFrameRate`; `DXVK_FRAME_RATE` é o override por environment variable. Uma chave `dxvk.maxFrameRate` pertence a upstream posterior e não deve ser retroativamente atribuída a 1.10.3/2.4.1.

## Existing Frame Limiter

O limiter real é `dxvk::FpsLimiter`, em `src/util/util_fps_limiter.h/.cpp`:

- No 1.10.3, o target é `m_targetInterval` em unidades de 100 ns. `delay(bool vsyncEnabled)` mede o frame anterior, usa `NtDelayExecution`/sleep e compensa imprecisão com `m_deviation`; no Windows pode fazer busy-wait apenas no trecho final, enquanto o comentário do source diz que no Wine `NtDelayExecution` é suficientemente preciso.
- No 2.4.1, o target também é `m_targetInterval`, agora em nanossegundos. `delay()` calcula `m_nextFrame` e chama `Sleep::sleepUntil`. Targets negativos ativam uma heurística para limitar apenas quando a cadência excede o refresh selecionado.
- Em ambos, `Presenter` possui o `FpsLimiter` durante toda a vida daquele presenter/swapchain. O delay acontece no caminho de apresentação DXVK, não no renderer Android do XServer.

O source primário está em [util_fps_limiter.cpp do 2.4.1](https://github.com/doitsujin/dxvk/blob/v2.4.1/src/util/util_fps_limiter.cpp) e [util_fps_limiter.cpp do 1.10.3](https://github.com/doitsujin/dxvk/blob/v1.10.3/src/util/util_fps_limiter.cpp).

## Initialization Path

Há dois inputs de launch:

1. O construtor de `FpsLimiter` lê `DXVK_FRAME_RATE` uma única vez. Se estiver definido e válido, chama o setter e marca `m_envOverride=true`.
2. `Config::getUserConfig()` abre `DXVK_CONFIG_FILE`/`dxvk.conf` uma única vez durante a criação de `DxvkInstance`; as opções D3D9/DXGI são então materializadas em structs (`D3D9Options`, `DxgiOptions`/`D3D11Options`).

No Winlator atual, `DXVKConfigDialog.setEnvVars()` escreve no launch:

```text
dxgi.maxFrameRate = N
d3d9.maxFrameRate = N
```

e define `DXVK_CONFIG_FILE=Z:\home\xuser\.config\dxvk.conf`. Isso já limita FPS, mas somente depois de iniciar/reiniciar o processo e não atende esta feature.

Aplicação ao limiter:

- 1.10.3: `D3D11SwapChain::CreatePresenter()` e `D3D9SwapChainEx::CreatePresenter()` chamam `Presenter::setFrameRateLimit(...)` durante criação do presenter.
- 2.4.1 D3D9: `UpdateTargetFrameRate()` roda no present, mas sempre relê a cópia imutável `m_parent->GetOptions()->maxFrameRate` criada no início.
- 2.4.1 DXGI: `DxgiSwapChain::UpdateTargetFrameRate()` roda no present e possui uma interface interna `IDXGIVkSwapChain2::SetTargetFrameRate(double)`, porém `m_frameRateOption` também é uma cópia da opção inicial. Essa interface liga o front-end DXGI ao presenter D3D11 dentro do mesmo processo; não é uma API de controle externo para o Android.

## Runtime Mutability

O **objeto ativo é mutável**:

- 1.10.3: `FpsLimiter::setTargetFrameRate(double)` adquire `m_mutex` e altera `m_targetInterval`.
- 2.4.1: `FpsLimiter::setTargetFrameRate(double, uint32_t)` adquire `m_mutex`, altera `m_targetInterval`/`m_maxLatency` e reseta o estado da heurística quando o target muda.
- `delay()` usa o mesmo mutex. No 2.4.1, ele copia interval/latency sob lock e libera o lock antes de dormir; o próprio comentário proíbe acessar depois campos que o setter possa escrever. Isso é uma base explicitamente preparada para atualização concorrente.
- `0` desabilita o limiter. Portanto Unlimited pode ser representado sem recriar device, swapchain ou processo.

Contudo, nenhum chamador atual fornece ao setter um valor vindo do menu em runtime. Alterar a env var no processo Android não altera o environment já copiado pelo processo Wine, e mesmo dentro do guest o construtor não a relê. Reescrever `dxvk.conf` também não muda as structs de options existentes.

Há ainda uma regra de precedência: nas duas versões, `m_envOverride=true` faz o setter normal ignorar updates posteriores. Um patch live deve definir comportamento explícito: o controle runtime do Winlator deve ter precedência enquanto ativo, ou a UI deve ser desabilitada se `DXVK_FRAME_RATE` foi fornecida manualmente. Não remover silenciosamente a semântica upstream.

## Existing Reload Mechanism

**Não existe hot reload de DXVK config nesses tags.** `Config::getUserConfig()` lê o arquivo na criação do `DxvkInstance`; não há `FileObserver`, watcher, reload command ou nova chamada no present. Não existe command socket/pipe público do DXVK e não há setter exportado para outro processo.

O `IDXGIVkSwapChain2::SetTargetFrameRate` do 2.4.1 prova que o target interno pode mudar com segurança, mas é uma interface privada entre objetos COM do próprio processo. Um processo auxiliar não possui o ponteiro do swapchain do jogo. Obtê-lo por injeção/hook remoto seria frágil e está descartado.

## Winlator IPC Possibilities

Infraestrutura encontrada:

- `WinHandler` usa UDP localhost (ports 7947/7946) entre Android e o helper guest para exec, processos, gamepad, MIDI etc. Ele não conversa com objetos DXVK e o helper não possui acesso ao swapchain de outro processo.
- `XConnectorEpoll` oferece Unix sockets para XServer, ALSA, SysV SHM, VirGL e Vortek. São protocolos específicos; DXVK não é cliente deles.
- SysV shared memory existe para X11/áudio, não como barramento genérico de configuração DXVK.
- LSFG live escreve `conf.toml.staging` e faz rename atômico para `conf.toml`. Do lado Java não há socket/sinal; o live update depende de comportamento próprio da layer LSFG. Esse padrão prova apenas escrita atômica de configuração, não pode ser reutilizado presumindo que DXVK observa arquivos — hoje não observa.

Conclusão: o Winlator consegue produzir um valor runtime e um arquivo privado de controle, mas **não possui hoje o último trecho até o `FpsLimiter` ativo**. Reutilizar um request code do WinHandler sem patch DXVK não resolve isso.

## Thread Safety

O setter upstream já resolve a parte crítica:

- update e leitura do intervalo são serializados pelo `dxvk::mutex` do limiter;
- no 2.4.1, o sleep ocorre fora do lock, então uma mudança não fica bloqueada pela espera do frame atual; ela passa a valer no próximo ciclo de pacing;
- no 1.10.3, o lock permanece durante `delay/sleep`, então o update aguardaria no máximo o restante do frame corrente — aceitável para 30–120 FPS, sem race;
- ao mudar 2.4.1, estado da heurística é resetado pelo setter; Unlimited zera target e `delay()` limpa `m_nextFrame`.

O novo canal precisa validar estritamente apenas `{0,30,45,60,90,120}`, usar geração/sequence para não aplicar escrita parcial, não seguir paths externos e encerrar qualquer watcher no unload do DLL. Se houver vários swapchains ou processos DXVK, todos os limiters do processo devem observar a mesma geração; todos os processos do container devem receber o mesmo target.

## Frame Pacing Risks

- O patch proposto não deve criar um segundo limiter: deve apenas atualizar o target do limiter DXVK existente, preservando seu algoritmo e posição no present.
- Não ler arquivo a cada frame. Polling com I/O no render thread pode introduzir picos. Preferência: um monitor leve por módulo/processo que espere 200–250 ms e publique `{generation, fps}` em atomics; o present só compara atomics e chama o setter quando a geração muda.
- Uma latência de controle inferior a ~250 ms continua “imediata” para interação humana; o próximo `delay()` usa o target novo. Isso não é polling pesado, daemon nem busy loop.
- VSync/FIFO ainda estabelece seu próprio teto. Pedir 90 em display/FIFO de 60 Hz não produz 90. Pedir 30 deve limitar aproximadamente 30. MAILBOX pode permitir cadência acima do refresh, conforme suporte do driver, mas não garante apresentação física de todos os frames.
- No 1.10.3, o limiter de config pode se desativar quando VSync e refresh são próximos; `DXVK_FRAME_RATE` usa `m_envOverride` para evitar isso. O canal runtime deve especificar se age como override explícito (recomendado) para que 30/60 escolhidos pelo usuário não sejam anulados pela heurística.
- Mudança de target pode produzir um único intervalo de transição; o estado/deviation deve ser resetado quando apropriado. O 2.4.1 já reseta heurística, mas deve também reancorar pacing conforme os testes; não inserir sleeps em XServer/Java.
- Validar em hardware frametime, input latency, FIFO/MAILBOX, alt-tab, múltiplos swapchains, 32/64-bit e troca repetida `Unlimited → 30 → 60 → Unlimited`.

## LSFG Interaction

O lugar correto do limite é o DXVK base:

```text
game/D3D Present → DXVK FpsLimiter (~30 base FPS) → Vulkan present → LSFG layer (2x) → XServer/display
```

Assim, conceitualmente, base 30 + LSFG 2x pode entregar aproximadamente 60 frames de saída, enquanto a simulação/renderização do jogo permanece em ~30. Um limiter Android no XServer depois da layer limitaria a saída composta e estaria errado.

Não é necessário nem permitido alterar LSFG. Entretanto a ordem real das Vulkan layers, o pacing `none`, FIFO/MAILBOX e a métrica do HUD precisam de teste físico: contar frames gerados como “base FPS” produziria validação falsa. Devem ser medidos separadamente present calls do jogo antes da layer e output final. Não há alegação de teste em hardware nesta auditoria.

O controle só pode aparecer/habilitar quando o wrapper ativo for uma build DXVK patchada. Não alegar suporte a VKD3D, WineD3D, D7VK/D8VK, VirGL/OpenGL ou executáveis Vulkan nativos. Jogos D3D12 que usam VKD3D não passam pelo limiter D3D9/DXGI auditado como caminho de base confiável.

## Required Patch, if any

Patch mínimo proposto, ainda **não autorizado nem implementado**:

1. Recompilar os DLLs x86 e x64 dos tags oficiais `v2.4.1` e `v1.10.3`, mantendo cada patch separado devido às diferenças de source.
2. Em `src/util/util_fps_limiter.h/.cpp`, adicionar um pequeno controle runtime compartilhado por módulo que:
   - lê uma vez no startup o path privado indicado por uma nova env técnica, por exemplo `WINLATOR_DXVK_FPS_CONTROL`;
   - inicia somente quando essa env existe;
   - em uma thread interna de baixa frequência (200–250 ms), lê um registro minúsculo versionado `{magic, version, generation, fps, checksum}`;
   - publica `generation` e `fps` em atomics; sem I/O no present e sem processo/daemon separado;
   - finaliza e faz join com segurança no unload.
3. Cada `FpsLimiter::delay()` compara a geração atômica. Quando mudar, aplica o setter interno já existente antes do pacing. Para evitar relock/recursão, fazer a atualização por helper sob o mesmo mutex, não chamar o setter enquanto `delay()` já segura o lock.
4. Definir runtime override separado de `m_envOverride`: enquanto o arquivo válido estiver ativo, o valor live tem precedência; ao receber `0`, desabilita realmente o limiter, não volta inadvertidamente ao valor de launch. Um comando/version inválido mantém o último valor válido.
5. No Winlator, `XServerDisplayActivity` cria o arquivo dentro de `/home/xuser/.config/winlator/` antes do guest launch, exporta o path DOS `Z:\...` e escreve alterações com staging + rename/checksum. No teardown, invalida a geração; no próximo launch inicia explicitamente em `0` ou no estado definido, evitando arquivo stale.
6. Marcar os componentes recompilados com capability/version própria. A UI futura só habilita a opção se `dxwrapper == DXVK` **e** a capability live estiver presente. Componentes DXVK instalados sem patch permanecem sem opção.

Arquivos upstream diretamente afetados: `src/util/util_fps_limiter.h` e `src/util/util_fps_limiter.cpp`; possivelmente o arquivo Meson apenas se o monitor for separado. Arquivos Winlator futuros: `XServerDisplayActivity.java` e o dialog/menu/resources estritamente necessários. Não é preciso alterar o render loop, `Presenter`, algoritmo de sleep, Wine, Box64, XServer renderer ou LSFG.

Custo estimado: uma thread dormente por processo/módulo DXVK habilitado, quatro verificações pequenas por segundo e uma comparação atômica por present. Risco técnico **baixo a médio**, não zero: lifecycle de DLL, múltiplos módulos/processos, semântica de rename sob Wine, prioridade entre config/env/runtime e builds x86/x64 exigem testes. Se os testes mostrarem hitch, unload inseguro ou inconsistência, o veredito deve cair para NOT VIABLE e a UI não deve ser criada.

Alternativa ainda menor no número de linhas — abrir/stat/read no thread de present a cada 250 ms — não é recomendada porque I/O síncrono pode causar stutter. Hooks, remote injection, sleeps Java/XServer e alteração de env pós-launch estão rejeitados.

## Final Verdict

**B) LIVE POSSIBLE WITH SMALL SAFE PATCH**

- **Estado atual:** não há live update utilizável; não implementar UI agora.
- **Prova de viabilidade:** o limiter ativo já possui setter e mutex; no 2.4.1 existe inclusive encadeamento interno `IDXGIVkSwapChain2::SetTargetFrameRate → Presenter::setFrameRateLimit → FpsLimiter::setTargetFrameRate`. O target zero remove o limite sem recriar processo/device/swapchain.
- **Parte ausente:** canal controlado entre `XServerDisplayActivity` e o setter dentro de cada processo DXVK.
- **Tamanho do patch:** pequeno e isolado se limitado ao monitor de controle + ligação ao setter existente; não altera o algoritmo de pacing.
- **Condição absoluta:** só avançar após autorização explícita para patch/rebuild dos dois DXVK e testes 32/64-bit em hardware. Sem esse patch comprovado, a feature deve permanecer inexistente.

## Generic XServer FPS Limit Implementation

Implementado um limitador genérico no servidor X11, sem patch, rebuild ou configuração de DXVK. O caminho alterado é o `Present` do XServer: quando o limite está ativo, a cópia do pixmap e o `PresentCompleteNotify` continuam no fluxo existente, mas o `PresentIdleNotify` que libera o buffer do guest é entregue em uma cadência controlada. Isso cria backpressure no produtor em vez de apenas esconder frames no renderer Android.

Valores aceitos pela UI e persistência: `0` (Unlimited) ou `10–200 FPS`, em passos de 1 FPS. Existem presets 30, 60, 90 e 120. O valor padrão e o fallback para dados inválidos são `0`.

Esta implementação substitui, para esta rodada, a recomendação anterior de patch específico no DXVK. Nenhum source, DLL ou configuração de DXVK foi alterado.

## Bannerlator Reference Mapping

Mapeamento confirmado na referência somente leitura:

```text
XServer drawer state/callback
  -> setFrameRateLimit(...)
  -> PresentExtension.presentPixmap(...)
  -> atraso do PresentIdleNotify
  -> guest aguarda reutilização do pixmap/buffer
```

Foi portado o conceito de backpressure e timing por janela, não o arquivo completo. A UI Compose/Kotlin, renderer Bionic, binários e integrações próprias do Bannerlator não foram copiados. Este Winlator usa `ContentDialog`, XML e Java existentes.

## PresentExtension Changes

- `frameRateLimit` é `volatile`, permitindo alteração live pela thread da Activity.
- Cada window ID possui `WindowTiming.nextIdleNs` independente.
- O próximo deadline avança por intervalo absoluto (`1 s / FPS`), reduzindo drift acumulado.
- Se a cadência estiver mais de um frame atrasada, ela é reancorada em `now + frameNs`, evitando bursts de recuperação.
- O disparo ocorre 0,7 ms antes do deadline, como compensação de scheduling usada pela referência.
- Uma `DelayQueue` bloqueante acorda somente quando existe um deadline vencido: não há polling, leitura por frame, busy loop ou thread em prioridade máxima.
- Ao trocar o target, a geração é invalidada, timings são zerados e idles já enfileirados são liberados. O próximo Present começa imediatamente com a nova cadência.
- Ao receber `0`, todos os idles pendentes são liberados e os próximos seguem sem atraso.
- `onUnmapWindow` remove o estado de timing daquela janela. `close()` interrompe a thread, limpa fila/mapa e remove o listener no teardown da Activity.
- `AtomicBoolean` em cada pending idle impede entrega duplicada em corridas entre flush e deadline.

## Live Update Flow

```text
XServer drawer -> FPS Limit dialog
  -> slider ACTION_UP / preset / Unlimited
  -> XServerDisplayActivity.applyFpsLimit(value)
  -> PresentExtension.setFrameRateLimit(value)
  -> flush da cadência anterior
  -> próximo Present usa o target novo
```

O `SeekBar` desenha o valor durante o arraste e seu callback existente só dispara em `ACTION_UP`; portanto não há update por pixel. Presets e Unlimited aplicam imediatamente. Cancel restaura ao vivo o valor existente antes da abertura do diálogo.

## Per-Container Persistence

O valor é salvo em `Container.extraData` sob `fpsLimit`. A Activity carrega e aplica o valor depois de criar o XServer e antes do uso normal do drawer. Cada alteração live válida chama `container.saveData()`. Containers antigos ou valor ausente recebem Unlimited.

## Frame Pacing

O pacing não adiciona sleeps ao render loop e não descarta a apresentação já copiada. O custo quando Unlimited é apenas uma leitura `volatile` e uma chamada direta ao caminho original. Com limite ativo existe uma thread daemon bloqueada em `DelayQueue`, uma entrada curta por Present pendente e um mapa pequeno por janela ativa.

Ainda é obrigatório medir em hardware consistência de frametime, spikes, latência, CPU/GPU e comportamento após muitas trocas. O build prova integração e compilação, não prova pacing físico.

## LSFG Interaction

LSFG não foi alterado. O método controla o produtor que aguarda `PresentIdleNotify`; o teste físico solicitado deve confirmar se, neste fork, a ordem prática reproduz o Bannerlator:

- limite 10, LSFG desligado: aproximadamente 10 FPS;
- limite 10, LSFG 2x: aproximadamente 20 FPS de saída;
- limite 10, LSFG 3x: aproximadamente 30 FPS de saída;
- limite 15, LSFG 3x: aproximadamente 45 FPS de saída.

É necessário observar separadamente FPS base, frames gerados/submitted e apresentação física. O código não altera multiplier, Flow Scale, semáforos, sync, pacing ou biblioteca LSFG.

## Wrapper Compatibility

O limitador não verifica o nome do wrapper e atua somente quando a aplicação usa o `PresentPixmap`/`PresentIdleNotify` deste XServer. DXVK que passe por esse caminho é o caso esperado. VKD3D e WineD3D podem se beneficiar somente se seus caminhos efetivos de apresentação também usarem esse protocolo; isso permanece pendente de teste e não é anunciado como suporte garantido. Aplicações que bypassam X11 Present não são limitadas.

## Physical Test Checklist

- [ ] LSFG OFF: Unlimited -> 10 -> 30 -> 60 -> Unlimited, sem restart.
- [ ] LSFG ON: 10 + 2x, 10 + 3x e 15 + 3x.
- [ ] Separar FPS base, gerado/submitted e físico.
- [ ] Medir frametime, microstutter, input latency e CPU/GPU load.
- [ ] Abrir/fechar drawer e trocar limite repetidamente.
- [ ] Testar múltiplas janelas, unmap/remap, alt-tab e diálogos do jogo.
- [ ] Sair do jogo/container e confirmar ausência de thread/callback vazando.
- [ ] Reabrir o container e confirmar persistência.
- [ ] Validar DXVK; avaliar separadamente VKD3D e WineD3D.

## Known Limitations

- Não há dispositivo Android conectado nesta sessão; resultados físicos e números de LSFG permanecem não comprovados neste APK.
- A precisão final depende do scheduler Android, do guest e do número de buffers Present disponíveis.
- O método não cobre caminhos que não geram `PresentIdleNotify` neste XServer.
- Um limite acima do refresh físico não garante que todos os frames sejam exibidos no painel.
- A fila segura evita busy wait, mas a validação em hardware deve comparar seu jitter com a implementação de referência.
