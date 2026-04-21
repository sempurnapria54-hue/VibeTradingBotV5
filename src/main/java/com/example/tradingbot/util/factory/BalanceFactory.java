package com.example.tradingbot.util.factory;

import com.example.tradingbot.domain.model.balance.Balance;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceExternalSnapshot;
import com.example.tradingbot.mapping.BalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static com.example.tradingbot.util.CollectionUtils.emptyIfNull;
import static io.micrometer.common.util.StringUtils.isNotBlank;
import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
public class BalanceFactory {

    private final BalanceMapper balanceMapper;

    public List<Balance> createBalances(List<BalanceExternalSnapshot> balanceSnapshots) {
        return emptyIfNull(balanceSnapshots).stream()
                                            .filter(Objects::nonNull)
                                            .filter(snapShot -> isNotBlank(snapShot.getCurrency()))
                                            .map(this::createFromSnapshot)
                                            .collect(toList());
    }

    private Balance createFromSnapshot(BalanceExternalSnapshot snapshot) {
        Balance balance = createEmptyBalance(snapshot.getCurrency());
        balanceMapper.updateDomainFromExternalSnapshot(snapshot, balance);
        return balance;
    }

    private static Balance createEmptyBalance(String currency) {
        Balance balance = new Balance();
        balance.setCurrency(currency);
        balance.setAvailable(BigDecimal.ZERO);
        balance.setFrozen(BigDecimal.ZERO);
        balance.setTotal(BigDecimal.ZERO);
        return balance;
    }
}
