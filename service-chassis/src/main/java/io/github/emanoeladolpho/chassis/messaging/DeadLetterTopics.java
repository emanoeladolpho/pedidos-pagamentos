package io.github.emanoeladolpho.chassis.messaging;

/** Convenção de nome das Dead Letter Topics: {@code <tópico>-dlt}. */
public final class DeadLetterTopics {

    public static final String SUFFIX = "-dlt";

    private DeadLetterTopics() {
    }

    public static String of(String topic) {
        return topic + SUFFIX;
    }
}
