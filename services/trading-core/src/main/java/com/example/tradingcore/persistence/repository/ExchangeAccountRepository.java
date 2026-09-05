package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.ExchangeAccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Запросы по строке проекции биржевого счёта. */
public interface ExchangeAccountRepository extends JpaRepository<ExchangeAccountEntity, Long> {

    Optional<ExchangeAccountEntity> findByInternalId(String internalId);

    /**
     * Идентичности тенантов, у которых есть счёт.
     *
     * <p>Проекция поля, а не выборка строк: тик заводит место под числа
     * риск-аппетита по тенантам счетов, и сами счета ему для этого не
     * нужны (.claude/rules/codestyle.md §«Выборка данных»).
     *
     * <p>Запрос объявлен, а не выведен из имени: имя метода выбирает
     * строки, а не колонку, — производная форма вернула бы сущности.
     */
    @Query("select distinct a.tenantInternalId from ExchangeAccountEntity a")
    List<String> findDistinctTenantInternalIds();
}
