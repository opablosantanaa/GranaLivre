# 💸 Grana Livre

Aplicativo mobile desenvolvido com **Android Studio** e **Java** para organização das finanças pessoais: contas, cartões de crédito, faturas, lançamentos recorrentes, orçamentos e metas de economia, com análises visuais e um assessor de finanças que dá dicas sobre o mês.

Todos os dados ficam **somente no aparelho**, em um banco **Room (SQLite)**. O app não tem servidor, não exige cadastro com email e oferece backups locais, opcionalmente criptografados com senha.

---

## 📲 Instalação

O aplicativo está disponível para celulares com **Android 8.0 ou superior**.

### Baixar o aplicativo

[**📥 Baixar granalivre.apk**](app/release/granalivre.apk)

1. Baixe o APK pelo link acima no celular.
2. Abra o arquivo `granalivre.apk`.
3. Se solicitado pelo Android, autorize **Instalar apps desconhecidos** para o navegador ou gerenciador de arquivos utilizado.
4. Confirme a instalação e abra o **Grana Livre**.
5. No Android 13 ou superior, permita as notificações para receber os avisos de vencimento.

Não é necessário instalar Android Studio, conectar o celular a um computador ou ter conexão com a internet para usar o aplicativo.

---

## 🚀 Funcionalidades

- **Primeiro acesso guiado**: o mascote (uma moeda sorridente) pergunta nome e data de nascimento. As categorias padrão de receitas e despesas já vêm prontas.
- **Início**: saldo total e por conta em páginas deslizáveis, receitas e despesas da semana ou do mês, fatura atual, total de despesas recorrentes, gráfico de evolução e últimos lançamentos.
- **Contas**: conta corrente, poupança, investimentos e dinheiro. O saldo é **calculado** a partir do saldo inicial, dos lançamentos pagos, das transferências e das faturas quitadas, por isso nunca fica fora de sincronia.
- **Lançamentos**: receitas, despesas e transferências entre contas, com categoria, data, status de pago/pendente e opção de repetir todo mês.
- **Cartões de crédito**: apelido, bandeira, últimos 4 dígitos, limite, dia de fechamento e de vencimento. As faturas são criadas automaticamente conforme as compras são lançadas.
- **Faturas**: navegação entre meses, compras da fatura, limite disponível, pagamento por uma conta escolhida e opção de desfazer o pagamento. Fatura paga não aceita alterações.
- **Compras parceladas**: divisão do total sem perder centavos (a primeira parcela recebe o resto), com cada parcela na fatura do seu mês.
- **Recorrentes**: despesas e receitas semanais, mensais ou anuais, com pausa, retomada, alertas de atraso e registro de pagamento total ou em parcela.
- **Movimentos**: lista mensal agrupada por dia, com totais de entradas, saídas e resultado. O filtro de categoria acompanha o tipo escolhido: categorias de receita em Entradas e de despesa em Saídas. Ao filtrar por uma categoria, os lançamentos das subcategorias dela também aparecem.
- **Análises**: resultado por semana, mês ou ano, gastos e receitas por categoria, orçamento do mês e mapa de calor da movimentação diária.
- **Orçamentos (limites)**: limite mensal por categoria para **qualquer mês**, escolhido com setas de mês anterior e próximo, com um resumo dos limites já definidos naquele mês. Um limite registrado pode ser **editado** ou **apagado**. O app avisa ao atingir parte do limite e ao estourá-lo.
- **Calculadora de salário**: o usuário informa as receitas esperadas no próximo mês e quanto quer que sobre. O app usa os gastos do último mês, por categoria, e mostra quanto cada um representaria da receita e quanto reduzir para atingir a economia. Cada categoria pode ser marcada como **Manter**, **Moderado** ou **Drástico**, e a escolha fica salva no aparelho. Se a meta não fechar, o app avisa quanto falta. O resultado pode ser aplicado como limites do mês seguinte.
- **Metas**: valor alvo, prazo opcional e reservas guardadas a partir de uma conta ou devolvidas a ela.
- **Assessor de finanças**: dicas de atenção, alerta e elogio geradas a partir dos dados do período, com alertas de contas e recebimentos recorrentes atrasados que seguem a mesma regra da aba Recorrentes.
- **Importação de extratos**: arquivos **OFX** (1.x SGML e 2.x XML) e **PDF**, com detecção de duplicados, sugestão de categoria por palavras-chave e conciliação com o saldo informado no extrato. Uma linha que corresponde a um lançamento já registrado no app (mesmo tipo e valor, até 5 dias de diferença) é **vinculada** a ele em vez de duplicar. Isso vale também para a próxima ocorrência de uma recorrente paga antes do vencimento.
- **Avisos de vencimento**: notificações às 11:30 e 17:30 para faturas e contas que vencem em 2 dias, amanhã, hoje ou que venceram ontem.
- **Backup**: exportação e importação em JSON, backups automáticos diários em uma pasta escolhida pelo usuário (os 10 mais recentes são mantidos), criptografia opcional com senha e restauração do perfil em uma instalação nova.
- **Aparência**: tema claro, escuro ou do sistema, e opção de preencher o app com dados de exemplo.
- **Política de privacidade e termos de uso**: acessíveis no cadastro e no Perfil. Os textos ficam em `app/src/main/assets/` e podem ser publicados no mesmo formato.

---

## 🎨 Interface e Animações

A interface foi implementada com **Java, layouts XML e componentes nativos do Android** (Material 3), com a fonte **Plus Jakarta Sans** em quatro pesos. As telas principais são fragmentos trocados por uma barra de abas, e os formulários são abertos em folhas inferiores (*bottom sheets*).

### Paleta de cores

| Cor       | Tema claro | Tema escuro | Aplicação                               |
|-----------|------------|-------------|-----------------------------------------|
| Principal | `#0B6E69`  | `#4FD1C5`   | Botões de ação, destaques e aba ativa   |
| Secundária| `#2EC4B6`  | `#2EC4B6`   | Elementos de apoio                       |
| Destaque  | `#FFB547`  | `#FFC870`   | Indicadores e chamadas de atenção       |
| Receita   | `#22A06B`  | `#4ADE80`   | Valores de entrada                      |
| Despesa   | `#E5484D`  | `#F87171`   | Valores de saída e alertas              |
| Fundo     | `#F7F9FA`  | `#0E1416`   | Base da interface                       |

As cores estão centralizadas em `app/src/main/res/values/colors.xml` e `values-night/colors.xml`.

### Navegação

- **Abas**: Início, Movimentos, Recorrentes, Análises e Metas ficam em uma barra flutuante no rodapé, com ícones vetoriais e o nome da aba ativa.
- **Perfil e ajustes**: acessado pelo cabeçalho do Início, reúne contas, cartões, categorias, aparência e backup.

### Gráficos e componentes próprios

Os gráficos são desenhados com `Canvas`, sem biblioteca externa: linha de tendência (`TrendChartView`), barras de receitas e despesas (`PairBarsView`), mapa de calor diário (`HeatmapView`), barras de progresso (`BarView`), controle segmentado (`SegmentedControl`) e o mascote animado (`CoinView`).

### Fundo Prism

O fundo usa **OpenGL ES 3.0 e shaders GLSL** (`res/raw/prism_vertex.glsl` e `prism_fragment.glsl`) para criar um efeito de luz em movimento nas cores do aplicativo. `FrameGovernor` e `DeviceLoad` ajustam a cadência de quadros conforme a carga do aparelho, limitando o custo gráfico.

---

## 🛠️ Tecnologias Utilizadas

| Tecnologia                      | Versão   | Descrição                                               |
|---------------------------------|----------|---------------------------------------------------------|
| Java                            | 11       | Compatibilidade do código-fonte configurada no módulo   |
| JDK                             | 25       | Versão configurada para o daemon do Gradle              |
| Android SDK                     | 37       | SDK de compilação e versão de destino                   |
| Android Gradle Plugin           | 9.4.1    | Compilação do aplicativo Android                        |
| Gradle Wrapper                  | 9.6.0    | Execução das tarefas de build                           |
| Room                            | 2.8.5    | Banco de dados local (SQLite) com migrações versionadas |
| WorkManager                     | 2.10.0   | Recorrências, avisos de vencimento e backup automático  |
| Lifecycle (ViewModel/LiveData)  | 2.9.0    | Estado da interface e atualização reativa das telas     |
| AppCompat                       | 1.8.0    | Componentes e compatibilidade da interface              |
| Material Components             | 1.14.0   | Componentes, folhas inferiores e tema Material 3        |
| ConstraintLayout                | 2.2.2    | Layouts das telas                                       |
| PdfBox-Android                  | 2.0.27.0 | Leitura de extratos e comprovantes em PDF               |
| ML Kit Text Recognition         | 16.0.1   | Reconhecimento de texto em imagens                      |
| OpenGL ES                       | 3.0      | Fundo animado com shader GLSL                           |
| JUnit                           | 4.13.2   | Testes unitários                                        |
| Robolectric                     | 4.16.1   | Testes de repositórios e banco na JVM, sem emulador     |
| Espresso                        | 3.7.0    | Infraestrutura de testes instrumentados                 |

---

## 📋 Pré-requisitos

Para executar o código-fonte e contribuir com o projeto:

- **Android Studio** compatível com o Android Gradle Plugin utilizado.
- **JDK 25**, conforme `gradle/gradle-daemon-jvm.properties`.
- **Android SDK 37**, Platform Tools e ferramentas de compilação instalados pelo SDK Manager.
- Conexão com a internet para baixar as dependências na primeira sincronização.
- Emulador ou aparelho com **Android 8.0 ou superior** (`minSdk = 26`).

Para utilizar o aplicativo, basta um aparelho Android compatível e o APK instalado.

---

## ⚙️ Configuração

### 1. Abrir no Android Studio

1. Baixe o projeto e abra sua pasta raiz, que contém `settings.gradle.kts`.
2. Aguarde a sincronização do Gradle.
3. Instale os componentes solicitados pelo SDK Manager, se necessário.
4. Confira a configuração do JDK usado pelo Gradle e a disponibilidade do JDK 25.

O arquivo `local.properties` contém o caminho do Android SDK da máquina. Ele é local e não deve ser compartilhado como configuração universal do projeto.

### 2. Banco de dados

O banco é criado no próprio aparelho pelo `AppDatabase` (versão 4). Os schemas exportados ficam em `app/schemas/` e servem de base para escrever e testar migrações (`database/Migrations.java`). Não há servidor, token, arquivo `.env` ou API externa.

### 3. Permissões

O `AndroidManifest.xml` do app declara apenas `POST_NOTIFICATIONS`, usada pelos avisos de vencimento. A biblioteca **ML Kit** acrescenta `INTERNET` e `ACCESS_NETWORK_STATE` no manifesto final, para enviar ao Google dados técnicos de diagnóstico. O reconhecimento de texto roda no aparelho, e o app em si não usa a internet. `android:allowBackup` está desativado: a cópia dos dados é feita pelo backup do próprio aplicativo.

---

## ▶️ Executar o Código-fonte

1. Abra e sincronize o projeto.
2. Selecione o módulo `app`.
3. Escolha um emulador ou um aparelho conectado com depuração USB habilitada.
4. Clique em **Run**.

A atividade inicial é `com.opablosantanaa.granalivre.App`. A classe `GranaLivreApplication` aplica o tema salvo e agenda as tarefas em segundo plano ao abrir o app.

Para gerar o APK de release (com R8 e redução de recursos ativados), o resultado sai em `app/release/granalivre.apk`.

---

## 📁 Estrutura do Projeto

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/opablosantanaa/granalivre/
│   │   ├── App.java                     # Activity principal e navegação entre abas
│   │   ├── GranaLivreApplication.java   # Tema e agendamento das tarefas
│   │   ├── entity/                      # Tabelas do banco e enums
│   │   ├── relation/                    # Entidades com filhas ou valores calculados
│   │   ├── converter/                   # Conversores de LocalDate e YearMonth
│   │   ├── dao/                         # Consultas Room (saldo, faturas, backup...)
│   │   ├── database/                    # AppDatabase e migrações
│   │   ├── domain/                      # Regras puras, sem Android nem banco
│   │   │   ├── InvoiceCalculator.java   # Fatura, fechamento e vencimento de uma compra
│   │   │   ├── InstallmentSplitter.java # Divisão das parcelas em centavos
│   │   │   ├── RecurrenceSchedule.java  # Datas das ocorrências recorrentes
│   │   │   ├── PasswordHasher.java      # Hash PBKDF2 de senhas
│   │   │   ├── BackupCipher.java        # Criptografia AES-GCM dos backups
│   │   │   ├── DueReminder.java         # Janela e horários dos avisos
│   │   │   ├── SalaryPlanner.java       # Calculadora de salário e cortes por prioridade
│   │   │   ├── ofx/                     # Leitura de extratos OFX e sugestão de categoria
│   │   │   └── pdf/                     # Leitura de extratos e comprovantes em PDF
│   │   ├── repository/                  # Validações, fluxos, importação e backup
│   │   ├── worker/                      # Recorrências, avisos e backup (WorkManager)
│   │   └── ui/
│   │       ├── screen/                  # Splash, cadastro, Início, Movimentos, Recorrentes, Análises e Metas
│   │       ├── sheet/                   # Formulários em folhas inferiores
│   │       ├── widget/                  # Gráficos e componentes desenhados em Canvas
│   │       ├── prism/                   # Fundo animado em OpenGL
│   │       ├── FinanceAdvisor.java      # Dicas do assessor de finanças
│   │       └── MainViewModel.java       # Estado compartilhado entre as telas
│   ├── assets/                          # Política de privacidade e termos de uso (HTML)
│   └── res/
│       ├── layout/                      # Telas, folhas e itens de lista
│       ├── drawable/                    # Ícones vetoriais e fundos
│       ├── font/                        # Plus Jakarta Sans
│       ├── raw/                         # Shaders GLSL do fundo
│       ├── values/                      # Textos, cores e temas (claro)
│       └── values-night/                # Cores e temas (escuro)
├── src/test/                            # Testes unitários e Robolectric
├── src/androidTest/                     # Testes instrumentados
├── schemas/                             # Schemas exportados do Room (versões 1 a 4)
├── release/granalivre.apk               # Aplicativo disponível para instalação
└── build.gradle.kts                     # Configuração do módulo Android

design/                                  # Mascote e referências visuais das telas
gradle/                                  # Wrapper e catálogo de dependências
settings.gradle.kts                      # Configuração dos módulos
```

---

## 🧮 Regras Financeiras

As principais decisões:

- **Dinheiro em centavos**: todos os valores são `long` em centavos, evitando os erros de arredondamento de `double`.
- **Valores sempre positivos**: o sentido vem do tipo do lançamento (`RECEITA` ou `DESPESA`).
- **Saldo calculado**:

```
saldo = saldo inicial
      + receitas pagas − despesas pagas
      + transferências recebidas − transferências enviadas
      − faturas pagas pela conta
```

- **Cartão**: compra feita antes do dia de fechamento entra na fatura do mês; no dia do fechamento ou depois, na do mês seguinte. Compras afetam a conta somente quando a fatura é paga.
- **Pagamento de fatura** não vira despesa, para o mesmo gasto não ser contado duas vezes.
- **Recorrências** geram lançamentos pendentes até a data de hoje e só entram no saldo quando confirmadas.
- **Importação de extrato**: cada lançamento recebe um identificador estável (`FITID` ou hash), e um índice único impede que o mesmo item entre duas vezes na mesma conta. Lançamentos feitos no app e vinculados a uma linha do extrato recebem esse identificador. Assim, a reimportação os reconhece.
- **Calculadora de salário**: valor disponível = receitas esperadas − valor a sobrar. Se o gasto do último mês passar disso, o corte é dividido proporcionalmente a gasto × peso (Manter 0, Moderado 1, Drástico 2). Uma categoria nunca fica abaixo de zero: o excedente é redistribuído, e a soma das sugestões fecha no centavo.

---

## 🔐 Segurança e Privacidade

- **Dados locais**: os dados financeiros não saem do aparelho, exceto nos backups que o próprio usuário salva. A única comunicação externa é a telemetria técnica do ML Kit, sem conteúdo dos extratos.
- **Cartões**: apenas os 4 últimos dígitos são armazenados.
- **Backups protegidos**: chave AES-256 derivada da senha com PBKDF2-HMAC-SHA256 (210.000 iterações e sal aleatório) e cifra **AES-GCM**, que recusa senha errada ou arquivo adulterado. A senha em si nunca é guardada.
- **Senhas**: armazenadas somente como hash PBKDF2 com sal, comparado em tempo constante.

---

## 🧪 Testes

Execute os testes unitários com:

```
.\gradlew.bat :app:testDebugUnitTest
```

Para executar os testes instrumentados, mantenha um emulador ou aparelho conectado:

```
.\gradlew.bat :app:connectedDebugAndroidTest
```

Para verificar recursos, compatibilidade e problemas apontados pelo Android Lint:

```
.\gradlew.bat :app:lintDebug
```

A suíte cobre as regras de domínio com JUnit puro (faturas, parcelas, recorrências, hash de senha, leitura de OFX e PDF, avisos, calculadora de salário e dicas do assessor) e os repositórios com **Robolectric** e banco em memória, sem precisar de emulador: saldo calculado, proteção de fatura paga, importação e conciliação de extratos, limites por mês, migrações do banco, ida e volta de backups criptografados e a corrente de avisos de vencimento.

---

## 🔧 Observações Importantes

1. **Uso pessoal**: o app é um organizador financeiro e não se conecta a bancos. Os valores dependem do que é lançado ou importado pelo usuário.
2. **Extratos**: a leitura de PDF depende do layout do banco. Extratos de cartão de crédito em OFX entram como receitas e despesas da conta escolhida. Arquivos acima de 15 MB são recusados.
3. **Pagamento de fatura**: a fatura é paga sempre pelo total. Pagamento parcial de fatura não é suportado.
4. **Senha do backup**: sem ela, os backups protegidos não podem ser abertos em nenhum aparelho.
5. **Notificações**: no Android 13 ou superior, os avisos dependem da permissão de notificações concedida pelo usuário.
6. **Identidade do aplicativo**: o pacote é `com.opablosantanaa.granalivre`. O nome da pasta do projeto não define a identidade instalada no Android.

---

## 📄 Licença

Projeto desenvolvido para estudo e aprendizado em desenvolvimento Android com Java.

---

## 👨‍💻 Autor

Desenvolvido por **Pablo Santana — @opablosantanaa**.
