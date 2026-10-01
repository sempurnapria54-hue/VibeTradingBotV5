package com.example.auth.domain.service;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.auth.exception.ContourNotAdmittedException;
import com.example.auth.exception.ExchangeAccountClosedException;
import com.example.auth.persistence.model.ExchangeAccountEntity;
import com.example.auth.persistence.repository.ExchangeAccountRepository;
import com.example.auth.persistence.repository.TenantRepository;
import com.example.auth.persistence.service.ExchangeAccountDataService;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.util.InternalIdFactory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Реестр биржевых счетов тенанта: заведение, смена ключей и чтение. */
@Service
public class ExchangeAccountService {

    private final ExchangeAccountRepository accountRepository;
    private final ExchangeAccountDataService accountData;
    private final TenantRepository tenantRepository;
    private final ExchangeAccountRegistrationService registration;
    private final ExchangeAccountKeyWriter keyWriter;

    public ExchangeAccountService(ExchangeAccountRepository accountRepository,
                                  ExchangeAccountDataService accountData,
                                  TenantRepository tenantRepository,
                                  ExchangeAccountRegistrationService registration,
                                  ExchangeAccountKeyWriter keyWriter) {
        this.accountRepository = accountRepository;
        this.accountData = accountData;
        this.tenantRepository = tenantRepository;
        this.registration = registration;
        this.keyWriter = keyWriter;
    }

    /**
     * Регистрирует счёт, если окружение допускает его контур.
     *
     * <p>Проверка допуска стои́т здесь, в момент РЕГИСТРАЦИИ: отказ тут
     * дёшев, а отказ в момент сделки приходит отказом доступа площадки и
     * поздно (docs/architecture/platform.md §«Чем различаются окружения»).
     *
     * <p><b>Ключи пишутся ПОСЛЕДНИМ действием и внутри транзакции.</b>
     * Порядок несущий: сорвись запись в хранилище — строка счёта
     * откатится, и не останется счёта, для которого нечем подписать
     * запрос. Обратный порядок оставлял бы секреты по адресу счёта,
     * которого нет.
     *
     * <p><b>Названный остаток:</b> запись в хранилище не участвует в
     * транзакции базы, поэтому отказ КОММИТА после успешной записи
     * оставляет секрет по адресу несуществующего счёта. Окно узкое
     * (между записью и коммитом), а лечится оно чисткой префикса по
     * реестру счетов — операцией над хранилищем, а не кодом;
     * якорь — .claude/work/prod-checks.md §«Осиротевшие секреты счетов».
     */
    @Transactional
    public ExchangeAccountEntity register(String tenantInternalId, String exchangeCode,
                                          String label, ExchangeAccount.Contour contour,
                                          String apiKey, String secret, String passphrase) {
        tenantRepository.findByInternalId(tenantInternalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Тенант не найден: " + tenantInternalId));
        if (isFalse(registration.contourAdmitted(contour))) {
            throw new ContourNotAdmittedException(contour);
        }
        ExchangeAccountEntity account = new ExchangeAccountEntity();
        account.setInternalId(InternalIdFactory.forInternalEntity());
        account.setTenantId(tenantInternalId);
        account.setExchangeCode(exchangeCode);
        account.setLabel(label);
        account.setContour(contour.name());
        account.setStatus(ExchangeAccount.Status.ACTIVE.name());
        ExchangeAccountEntity saved = accountRepository.save(account);
        keyWriter.write(saved.getInternalId(), apiKey, secret, passphrase, contour);
        return saved;
    }

    /**
     * Меняет ключи счёта: пишет новые по пути счёта, контур берёт со строки.
     *
     * <p><b>Контур — со строки счёта, а не от вызывающего.</b> Принятый из
     * запроса, он сделал бы смену ключей тропой смены контура в обход
     * проверки допуска, которая срабатывает только при регистрации
     * (docs/architecture/tenant-and-exchange.md §Ключи).
     *
     * <p><b>Отключённый счёт ключей не принимает</b> — их отзыв и есть смысл
     * отключения. Проверка стои́т в самой точечной записи строки, а не
     * чтением перед ней: запись берёт замок строки, и параллельное
     * отключение упорядочивается с ней базой. Предусловия живого риска у
     * смены ключей нет, и это несущее — она единственная тропа к внешнему
     * выходу из ступени отказа кредов (довод —
     * .claude/decisions/auth-account-owner-operations.md).
     *
     * <p><b>Момент и автор изменения строки движутся, хотя ни одна её
     * колонка не меняется:</b> ключ — часть счёта, живущая вне строки, и без
     * точечной записи актор смены не был бы записан нигде.
     *
     * <p><b>Ключи пишутся ПОСЛЕДНИМ действием и внутри транзакции</b> — тот
     * же порядок, что у регистрации: сорвись запись в хранилище, точечная
     * запись строки откатится, и момент изменения не утверждал бы смены,
     * которой не было. Названный остаток — отказ коммита после успешной
     * записи: новые ключи уже лежат, а строка их момента не несёт.
     *
     * @param accountInternalId идентичность счёта
     * @param apiKey            новый API-ключ
     * @param secret            новый секрет ключа
     * @param passphrase        новый passphrase ключа
     * @return строка счёта — реестровые поля без изменений
     */
    @Transactional
    public ExchangeAccountEntity rotateKeys(String accountInternalId, String apiKey,
                                            String secret, String passphrase) {
        ExchangeAccountEntity account = accountData.getRequiredByInternalId(accountInternalId);
        if (isFalse(accountData.markModifiedInStatus(account.getId(), ExchangeAccount.Status.ACTIVE))) {
            throw new ExchangeAccountClosedException(accountInternalId);
        }
        keyWriter.write(account.getInternalId(), apiKey, secret, passphrase,
                ExchangeAccount.Contour.valueOf(account.getContour()));
        return account;
    }

    /** Счета тенанта. */
    @Transactional(readOnly = true)
    public List<ExchangeAccountEntity> byTenant(String tenantInternalId) {
        return accountRepository.findAllByTenantId(tenantInternalId);
    }

    /**
     * Реестр счетов целиком — чтение торгового ядра.
     *
     * <p><b>Не по тенанту, и это следствие контракта, а не удобства.</b>
     * Ядро обязано знать, какие счета существуют, иначе не начнёт ни
     * одного прохода (docs/architecture/contracts.md §«Синхронные
     * вызовы»); перечня тенантов у него нет и быть не должно — тенантами
     * владеет этот сервис.
     *
     * <p><b>Пагинации нет, и это названное ограничение.</b> Счёт заводит
     * человек, и их десятки, а не тысячи: окно за курсором стоило бы
     * больше, чем даёт (.claude/rules/design-simplicity.md). Условие
     * пересмотра — второй субъект (фаза 4): с ним число счетов растёт с
     * числом тенантов, и чтение переводится на окно.
     */
    @Transactional(readOnly = true)
    public List<ExchangeAccountEntity> all() {
        return accountRepository.findAll();
    }
}
