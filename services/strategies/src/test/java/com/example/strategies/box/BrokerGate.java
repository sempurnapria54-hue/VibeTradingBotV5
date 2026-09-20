package com.example.strategies.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Перекрываемая тропа к брокеру: адрес, который ящик даёт сервису вместо
 * адреса контейнера, и который можно закрыть и открыть НА ХОДУ.
 *
 * <p><b>Ею закрывается ось контура, а не пишется удобный стаб.</b> Кейс,
 * объявляющий общий субстрат недоступным, обязан взять свой контейнер —
 * иначе он лишает соседей по модулю адреса
 * (.claude/decisions/test-contour-design-pass.md §«Кейс, разрушающий
 * субстрат, берёт свой контейнер и свой контекст»). Здесь контейнер
 * остаётся общим и живым, а недоступной делается ТРОПА к нему: соседи
 * ходят к брокеру своим адресом и её не замечают.
 *
 * <p><b>Зачем она нужна именно этому предмету.</b> Клетки {@code B7.4} и
 * {@code B7.5} — пара: на первом тике брокер отказывает и строки остаются
 * непомеченными, на втором те же строки уходят. Адрес брокера приезжает
 * конфигурацией, то есть фиксируется подъёмом контекста; без
 * перекрываемой тропы вторая половина ожидания не измерялась бы вовсе, а
 * пара предъявляла бы половину как целое.
 *
 * <p><b>Отказ ставится ЗАКРЫТИЕМ соединения, а не отсутствием
 * слушателя.</b> Порт держится занятым всю жизнь прогона: снимать и
 * возвращать привязку значило бы менять адрес, который сервис уже
 * прочитал.
 *
 * <p><b>Перекрывается ТОЛЬКО начальное знакомство, и это названное
 * свойство.</b> Брокер объявляет клиенту свой собственный адрес
 * ({@code advertised.listeners} контейнера), поэтому рабочие соединения
 * идут к контейнеру напрямую, минуя гейт; через гейт проходит добыча
 * раскладки темы. Направление «закрыт → открыт» этим и держится: пока
 * раскладки нет, отправка отказывает; как только гейт открыт, клиент её
 * добывает и публикует.
 *
 * <p><b>Цена названа: отказ приходит не сразу.</b> Потолок ожидания
 * раскладки у публикующего клиента — умолчание клиента Kafka
 * ({@code max.block.ms}), величиной конфигурации сервиса он не объявлен,
 * и тик против закрытого гейта стои́т этого потолка целиком. Клетка
 * поэтому ждёт своего тика дольше штатного.
 */
final class BrokerGate {

    private static final Integer BUFFER = 8192;

    private final ServerSocket entry;

    private final String targetHost;

    private final Integer targetPort;

    private final AtomicBoolean open = new AtomicBoolean(false);

    private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "box-broker-gate");
        thread.setDaemon(true);
        return thread;
    });

    private BrokerGate(ServerSocket entry, String targetHost, Integer targetPort) {
        this.entry = entry;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    /**
     * Гейт перед названным адресом брокера, изначально ЗАКРЫТЫЙ.
     *
     * @param bootstrapServers адрес брокера вида {@code host:port}
     */
    static BrokerGate closedBefore(String bootstrapServers) {
        String[] parts = bootstrapServers.split(":");
        try {
            ServerSocket entry = new ServerSocket();
            entry.bind(new InetSocketAddress("localhost", 0));
            BrokerGate gate = new BrokerGate(entry, parts[0], Integer.valueOf(parts[1]));
            gate.acceptLoop();
            return gate;
        } catch (IOException failure) {
            throw new IllegalStateException("Гейт брокера не поднялся", failure);
        }
    }

    /** Адрес гейта: его сервис и читает вместо адреса контейнера. */
    String bootstrapServers() {
        return "localhost:" + entry.getLocalPort();
    }

    /** Открывает тропу: следующее знакомство с брокером состоится. */
    void open() {
        open.set(true);
    }

    private void acceptLoop() {
        workers.submit(() -> {
            while (isFalse(entry.isClosed())) {
                try {
                    Socket incoming = entry.accept();
                    if (open.get()) {
                        forward(incoming);
                    } else {
                        incoming.close();
                    }
                } catch (IOException stop) {
                    return;
                }
            }
        });
    }

    private void forward(Socket incoming) throws IOException {
        Socket outgoing = new Socket(targetHost, targetPort);
        pump(incoming, outgoing);
        pump(outgoing, incoming);
    }

    private void pump(Socket from, Socket to) {
        workers.submit(() -> {
            byte[] buffer = new byte[BUFFER];
            try (InputStream source = from.getInputStream(); OutputStream sink = to.getOutputStream()) {
                int read = source.read(buffer);
                while (read >= 0) {
                    sink.write(buffer, 0, read);
                    sink.flush();
                    read = source.read(buffer);
                }
            } catch (IOException closed) {
                // Обрыв любой из сторон — штатный конец переливки.
            }
        });
    }
}
