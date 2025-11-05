package simulador;

import java.io.Serializable;

/**
 * Representa uma mensagem trocada entre os processos (generais).
 * Precisa ser 'Serializable' para ser enviada via Sockets.
 */
public class Mensagem implements Serializable {
    private final int remetenteId;
    private final String ordem;

    public Mensagem(int remetenteId, String ordem) {
        this.remetenteId = remetenteId;
        this.ordem = ordem;
    }

    public int getRemetenteId() {
        return remetenteId;
    }

    public String getOrdem() {
        return ordem;
    }

    @Override
    public String toString() {
        return "Mensagem [de=" + remetenteId + ", ordem='" + ordem + "']";
    }
}