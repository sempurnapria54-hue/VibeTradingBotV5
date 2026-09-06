package com.example.tradingcore.domain.jobs.facade;

import com.example.tradingcore.domain.safety.ManualHaltClass;
import com.example.tradingcore.domain.safety.ManualHaltService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Асинхронный запуск ПОЛНОЙ постановки: она гоняет снятие риска по всем
 * активным сделкам радиуса с повторами по своему бюджету, и синхронный
 * ответ либо соврал бы о неподтверждённом снятии риска, либо истёк бы по
 * таймауту (docs/rules/manual-halt.md §«Что это такое структурно»).
 *
 * <p><b>Мягкая постановка и снятие сюда не приходят.</b> Они на биржу не
 * ходят и повторов не имеют, зато <b>могут отказать</b> — и асинхронная
 * форма отказ бы спрятала: держатель получил бы {@code 202}, ступень не
 * поднята, отчёта нет, а он считает объект остановленным.
 *
 * <p><b>Носитель исхода — отчёт о происшествии</b>, чей терминал и так
 * гейтится подтверждённым снятием риска: наружу исход работы не
 * транслируется.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManualHaltFacade {

    private final ManualHaltService manualHaltService;

    /** Асинхронно поднимает полную ступень названного радиуса. */
    @Async
    public void raiseFull(String accountInternalId, String instrumentInternalId) {
        log.warn("Holder full halt started accountInternalId={} instrumentInternalId={}",
                accountInternalId, instrumentInternalId);
        manualHaltService.raise(ManualHaltClass.FULL, accountInternalId, instrumentInternalId);
        log.warn("Holder full halt finished accountInternalId={} instrumentInternalId={}",
                accountInternalId, instrumentInternalId);
    }
}
