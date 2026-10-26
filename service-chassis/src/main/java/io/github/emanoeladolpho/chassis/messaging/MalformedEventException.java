package io.github.emanoeladolpho.chassis.messaging;

/**
 * Mensagem que nunca poderá ser processada (JSON inválido, campos obrigatórios ausentes).
 * Não é reprocessada: vai direto para a Dead Letter Topic.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
