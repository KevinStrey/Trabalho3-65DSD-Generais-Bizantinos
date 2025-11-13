# 🛡️ Simulador Distribuído de Generais Bizantinos (BFT)

Este é um simulador acadêmico, construído em **Java**, que demonstra visualmente o **Problema dos Generais Bizantinos**.

Diferente de outros simuladores que rodam em uma única máquina, este é um **sistema distribuído real**. Cada "General" é um processo independente (um `.jar` em execução) que se comunica com os outros através da rede (via Sockets Java), permitindo que você execute a simulação em múltiplas máquinas virtuais ou físicas.

A interface gráfica permite que cada processo seja configurado individualmente e exibe os logs de comunicação em tempo real.

## ✨ Funcionalidades

  * **GUI de Lançamento:** Chega de linha de comando. Dê dois cliques no `.jar`, e uma interface pop-up perguntará o ID do processo, o ID do comandante e se ele é um traidor.
  * **Logs em Tempo Real:** Cada processo abre sua própria janela de log, permitindo que você veja exatamente quais mensagens ele envia, recebe e qual sua decisão final.
  * **Sistema Distribuído Real:** Utiliza Sockets Java para comunicação de rede. Não é uma simulação de "threads", e sim processos reais em IPs reais.
  * **Configuração de Rede Externa:** Um arquivo `config.txt` central (mas distribuído) define o mapa de rede (ID, IP, Porta) de todos os generais. [cite: 1]
  * **Simulação Observável:** A simulação faz pausas automáticas entre as rodadas (Envio do Comandante, Retransmissão dos Tenentes, Votação) para que você possa assistir ao vivo o consenso sendo formado (ou falhando) em todas as janelas.

-----

## 🚀 Como Executar (Guia do Usuário)

Você precisará de **Java (JRE) 11 ou superior** instalado em todas as máquinas.

### Passo 1: Download

Em seu GitHub, o usuário deve baixar os dois arquivos essenciais da seção "Releases" (ou do repositório):

  * `BFT-GUI.jar`
  * `config.txt`

### Passo 2: Configurar a Rede (O Passo Mais Importante)

Antes de executar, você **deve** editar o arquivo `config.txt`. Este arquivo informa a cada general onde encontrar os outros.

1.  Decida quais máquinas (físicas ou VMs) você usará.
2.  Obtenha o endereço IP de cada uma (ex: `192.168.1.5`).
3.  Abra o `config.txt` e edite-o para refletir sua rede.

**Exemplo de `config.txt` para 4 máquinas na sua rede local:** [cite: 1]

```
# ID   IP_DA_MAQUINA   PORTA
0      192.168.1.4     8000
1      192.168.1.5     8000
2      192.168.1.7     8000
3      192.168.1.8     8000
```

🚨 **AVISO DE FIREWALL:** Esta é a causa \#1 de falhas. Você **DEVE** garantir que o firewall de todas as suas máquinas (Windows, Linux, etc.) esteja configurado para **permitir conexões de entrada** na porta que você definiu (neste exemplo, a porta `8000`).

### Passo 3: Executar a Simulação

1.  Em **CADA** uma das suas máquinas/VMs, crie uma pasta e coloque **ambos** os arquivos (`BFT-GUI.jar` e o `config.txt` que você editou) dentro dela.
2.  Em cada máquina, **dê dois cliques no `BFT-GUI.jar`** para iniciá-lo.
3.  Um pop-up de configuração aparecerá. Preencha-o de acordo com a máquina.

**Exemplo de Simulação (4 Generais, 1 Traidor):**

  * **Máquina 1 (IP 192.168.1.4):**

      * Meu ID: `0`
      * ID do Comandante: `0`
      * É Traidor: (desmarcado)
      * Clique "OK".

  * **Máquina 2 (IP 192.168.1.5):**

      * Meu ID: `1`
      * ID do Comandante: `0`
      * É Traidor: (desmarcado)
      * Clique "OK".

  * **Máquina 3 (IP 192.168.1.7):**

      * Meu ID: `2`
      * ID do Comandante: `0`
      * É Traidor: **(marcado)**
      * Clique "OK".

  * **Máquina 4 (IP 192.168.1.8):**

      * Meu ID: `3`
      * ID do Comandante: `0`
      * É Traidor: (desmarcado)
      * Clique "OK".

### Passo 4: Observar

Quatro janelas de log (uma em cada máquina) se abrirão. Elas esperarão 5 segundos para que todos os processos se iniciem e, em seguida, executarão a simulação automaticamente, pausando 3 segundos entre cada rodada para que você possa comparar os logs.

-----

## 🛠️ Como Funciona (Arquitetura)

Este projeto é dividido em quatro classes principais:

1.  **`ProcessoGUI.java` (O Lançador):**

      * Este é o ponto de entrada (`main`) do `.jar`.
      * Ele usa `JOptionPane` para mostrar o pop-up de configuração.
      * Ele lê o `config.txt` para construir o mapa da rede.
      * Ele cria a `JFrame` e a `JTextArea` para o log.
      * Ele instancia e inicia o `Processo` em uma nova thread.

2.  **`Processo.java` (O General):**

      * Contém toda a lógica principal do BFT (Rodada 1, Rodada 2, Votação).
      * Ele **não** sabe sobre a GUI de *lançamento*, mas recebe a `JTextArea` da GUI de *log* para poder imprimir nela.
      * Usa o método `aguardarProximoPasso()` (que contém um `Thread.sleep()`) para criar as pausas observáveis.
      * Usa o `Comunicador` para enviar e receber mensagens.

3.  **`Comunicador.java` (O Mensageiro):**

      * Uma classe utilitária que gerencia a rede.
      * `iniciarServidor()`: Inicia um `ServerSocket` em uma thread para ouvir mensagens de entrada.
      * `enviarMensagem()`: Abre um `Socket` para um IP/Porta específico e envia um objeto `Mensagem`.
      * Usa uma `BlockingQueue` para passar mensagens da thread do servidor para a thread do `Processo` de forma segura.

4.  **`Mensagem.java` (A Mensagem):**

      * Um simples objeto `Serializable` que encapsula a ordem ("ATACAR" / "RECUAR") e o ID do remetente.

-----

## 👨‍💻 Para Desenvolvedores (Compilando do Zero)

Se você não quiser usar o `.jar` pré-compilado:

1.  Clone o repositório.
2.  Coloque todos os 4 arquivos `.java` em um pacote `simulador`.
3.  Compile-os:
    ```bash
    javac simulador/*.java
    ```
4.  Execute o lançador principal:
    ```bash
    java simulador.ProcessoGUI
    ```
    (Lembre-se de que o `config.txt` deve estar no diretório de onde você executa este comando\!)