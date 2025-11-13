# 🛡️ Simulador de Generais Bizantinos (BFT)

Este é um simulador acadêmico, construído em **Java Swing**, que demonstra visualmente o clássico **Problema dos Generais Bizantinos** (Byzantine Generals Problem).

O objetivo é permitir que o usuário configure um cenário com N generais, defina um comandante, designe traidores e execute o algoritmo de consenso passo a passo. A interface permite observar os logs de cada processo em tempo real e entender como os generais leais chegam (ou não) a um consenso, mesmo com traidores tentando sabotar a comunicação.

## ✨ Principais Funcionalidades

  * **Interface Gráfica (Swing):** Painel de controle simples para configurar e executar a simulação.
  * **Configuração Dinâmica:** Permite definir o número de generais (processos) na simulação.
  * **Seleção de Papéis:** O usuário pode escolher qual processo será o **comandante** e quais serão os **traidores**.
  * **Execução Passo a Passo:** A simulação não roda de uma vez. O usuário clica no botão **"Próxima Ação \>\>"** para avançar entre as rodadas do protocolo, permitindo uma análise detalhada do que acontece em cada etapa.
  * **Visualização Clara:** Cada processo possui sua própria área de log, mostrando as mensagens que envia, recebe e sua decisão final.
  * **Personalização de UI:** Um slider permite **aumentar ou diminuir o tamanho da fonte** dos logs para melhor legibilidade.
  * **Comunicação Real:** O simulador usa Sockets Java reais (`ServerSocket` e `Socket`) para a comunicação entre os processos (generais), simulando uma rede distribuída.

-----

## 🧐 O Problema: Generais Bizantinos

Para entender o simulador, é crucial entender o problema que ele demonstra.

Imagine um grupo de generais do exército bizantino acampados ao redor de uma cidade inimiga. Eles precisam decidir em conjunto se vão **ATACAR** ou **RECUAR**.

  * **Comunicação:** Eles só podem se comunicar por mensageiros.
  * **Consenso:** Todos os generais leais devem tomar a *mesma* decisão. Se alguns atacarem e outros recuarem, será um desastre.
  * **O Desafio:** Alguns generais podem ser **traidores**.

#### O que um traidor faz?

1.  **Se o Comandante for traidor:** Ele pode enviar "ATACAR" para metade dos tenentes e "RECUAR" para a outra metade, tentando dividir os leais.
2.  **Se um Tenente for traidor:** Quando ele deve retransmitir a ordem do comandante, ele mente. Ele diz "Recebi a ordem de RECUAR" quando, na verdade, recebeu "ATACAR".

O objetivo do algoritmo BFT (Byzantine Fault Tolerance) é garantir que **todos os tenentes leais cheguem à mesma decisão final**, não importa o que os traidores façam. A teoria prova que isso só é possível se houver no mínimo `3m + 1` generais no total, onde `m` é o número de traidores.

-----

## ⚙️ Como o Simulador Funciona

A simulação é dividida em duas partes: a interface (GUI) e a lógica dos processos.

### A Interface (`SimuladorGUI.java`)

1.  **Configurar Simulação:** Ao clicar, a GUI lê o número 'N' do `JSpinner`, cria 'N' áreas de log e preenche os seletores de comandante e traidores.
2.  **Iniciar Simulação:** O usuário define os papéis. Ao clicar em "Iniciar":
      * A GUI cria `N` instâncias da classe `Processo`.
      * Ela passa a cada processo seu ID, quem é o comandante, se ele é traidor, a lista de todos os outros processos, sua `JTextArea` de log e um objeto `globalStepLock`.
      * A GUI inicia `N` `Threads`, uma para cada processo.
      * Todos os controles são desabilitados, exceto o "Próxima Ação \>\>".
3.  **Próxima Ação \>\>:** Este é o coração do controle. Todas as `Threads` dos processos estão pausadas, esperando em um `stepLock.wait()`. Quando o usuário clica neste botão, a GUI chama `globalStepLock.notifyAll()`, "acordando" todas as threads, que executam a próxima etapa da lógica e voltam a pausar.
4.  **Slider de Fonte:** Simplesmente atualiza o tamanho da fonte em todas as `JTextArea`s quando o valor é alterado.

### A Lógica (`Processo.java`)

Cada `Processo` (General) executa a seguinte lógica em sua própria thread:

1.  **Passo 1: Início (automático)**

      * O processo é iniciado.
      * Ele cria seu `Comunicador` e inicia seu `ServerSocket` para ouvir mensagens.
      * **Pausa** e espera o primeiro clique no `stepLock`.

2.  **Passo 2: Rodada 1 (Clique 1)**

      * **Se for o Comandante:** Envia sua ordem para todos os Tenentes. (Se for traidor, envia ordens diferentes).
      * **Se for um Tenente:** Espera (bloqueado) até receber a mensagem do Comandante. Armazena essa ordem.
      * Todos **pausam** e esperam o próximo clique.

3.  **Passo 3: Rodada 2 (Clique 2)**

      * **Se for um Tenente:** Retransmite a ordem que *recebeu* (ou *diz* ter recebido, se for traidor) para todos os *outros* tenentes.
      * Em seguida, espera (bloqueado) até receber as retransmissões de todos os outros tenentes.
      * **Pausa** e espera o próximo clique.

4.  **Passo 4: Decisão (Clique 3)**

      * **Se for um Tenente:** Ele agora tem uma lista de ordens (a original do comandante + as retransmissões de todos os outros).
      * Ele aplica um **voto majoritário** simples nessa lista.
      * Ele exibe sua "DECISÃO FINAL" no log.
      * A thread do processo termina.

Quando todas as threads terminam, o botão "Próxima Ação \>\>" detecta isso e reabilita os controles para uma nova simulação.

-----

## 🚀 Como Executar

Você precisa ter o **Java Development Kit (JDK)** (versão 8 ou superior) instalado.

### Opção 1: Por Linha de Comando

1.  Coloque todos os 4 arquivos `.java` (`SimuladorGUI.java`, `Processo.java`, `Comunicador.java`, `Mensagem.java`) em um diretório chamado `simulador`.
2.  Abra um terminal ou prompt de comando na pasta *acima* do diretório `simulador`.
3.  Compile todos os arquivos:
    ```sh
    javac simulador/*.java
    ```
4.  Execute a classe principal (a GUI):
    ```sh
    java simulador.SimuladorGUI
    ```

### Opção 2: Por uma IDE (Eclipse, IntelliJ, VS Code)

1.  Crie um novo projeto Java.
2.  Crie um pacote (package) chamado `simulador`.
3.  Adicione os 4 arquivos `.java` a esse pacote.
4.  Encontre o arquivo `SimuladorGUI.java`, clique com o botão direito e selecione **"Run"** (Executar).

-----

## 📂 Estrutura dos Arquivos

  * `SimuladorGUI.java`

      * **O Painel de Controle.** Cria a janela, os botões, o slider e os painéis de log. Gerencia o início e o fim da simulação e controla o `globalStepLock`.

  * `Processo.java`

      * **O Cérebro de um General.** Contém toda a lógica do protocolo BFT (Rodada 1, Rodada 2, Decisão). Cada instância é executada em sua própria thread e pausa usando o `stepLock`.

  * `Comunicador.java`

      * **O Mensageiro.** Uma classe utilitária que gerencia a comunicação de rede. Cada processo tem um. Ela sabe como `iniciarServidor()` (para ouvir) e `enviarMensagem()` (para falar) usando Sockets Java.

  * `Mensagem.java`

      * **O Pergaminho.** Um objeto simples (`Serializable`) que é enviado pela rede. Ele encapsula a ordem (ex: "ATACAR") e o ID de quem a enviou.