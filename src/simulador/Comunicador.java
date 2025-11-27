package simulador;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

public class Comunicador {
    private final int porta;
    private final BlockingQueue<Mensagem> filaDeMensagens;
    
    private ServerSocket serverSocket;
    private Thread serverThread;

    public Comunicador(int porta, BlockingQueue<Mensagem> filaDeMensagens) {
        this.porta = porta;
        this.filaDeMensagens = filaDeMensagens;
    }

    /**
     * Inicia uma thread para escutar por conexões e receber mensagens.
     */
    public void iniciarServidor() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(porta); // Atribui ao campo
                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> receberMensagem(clientSocket)).start();
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("Erro no servidor na porta " + porta + ": " + e.getMessage());
                }
            }
        });
        serverThread.start();
    }

    /**
     * Lógica para receber um objeto Mensagem de um Socket.
     */
    private void receberMensagem(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Mensagem msg = (Mensagem) in.readObject();
            filaDeMensagens.put(msg); // Adiciona a mensagem na fila para ser processada
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            // Silencioso
        }
    }

    /**
     * Envia uma mensagem para um host e porta específicos.
     */
    public void enviarMensagem(String host, int portaDestino, Mensagem msg) {
        try (Socket socket = new Socket(host, portaDestino);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.writeObject(msg);
        } catch (IOException e) {
            // System.err.println("Falha ao enviar para " + host + ":" + portaDestino);
        }
    }
    
    /**
     * Para a thread do servidor e fecha o ServerSocket.
     */
    public void desligarServidor() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Isso vai causar uma IOException no accept()
            }
            if (serverThread != null) {
                serverThread.interrupt(); // Interrompe a thread principal
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar servidor na porta " + porta + ": " + e.getMessage());
        }
    }
}