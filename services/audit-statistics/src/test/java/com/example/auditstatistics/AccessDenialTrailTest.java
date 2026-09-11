package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.example.auditstatistics.api.AccessDenialHandler;
import com.example.auditstatistics.domain.model.AccessDenial;
import com.example.auditstatistics.domain.service.AccessDenialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Порядок на тропе отказа: <b>строка заводится ДО ответа вызывающему</b>
 * (docs/models/domain/other/AccessDenial.md §Инварианты).
 *
 * <p><b>Почему порядок мерится, а не читается.</b> Обратный порядок
 * работает ровно так же на зелёном прогоне: ответ уходит, строка ложится,
 * тест поверхности ничего не замечает. Расходятся они только там, ради
 * чего след и заведён, — на исходе, где тропа обрывается между ответом и
 * записью. Поэтому проба смотрит на состояние ответа <b>в момент вызова
 * писателя</b>: непроставленный статус означает, что ответ ещё не собран.
 *
 * <p><b>Обе точки входа проверяются порознь</b>: они разные методы разных
 * интерфейсов, и порядок в одной ничего не говорит о другой.
 *
 * <p>Подменяется здесь <b>коллаборатор</b> — писатель, у которого своя
 * проверка ({@code AccessDenialRowTest}); предмет этой пробы — тропа, а не
 * запись (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class AccessDenialTrailTest {

    private static final String CLOSED_PATH = "/api/v1/audit-statistics/journal/records";
    private static final String ACCEPTED_PRINCIPAL = "holder";
    private static final int STATUS_BEFORE_ANY_RESPONSE = 200;

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final List<Attempt> attempts = new ArrayList<>();
    private final AccessDenialService writer = mock(AccessDenialService.class);

    private AccessDenialHandler handler;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            attempts.add(new Attempt(invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    response.getStatus()));
            return null;
        }).when(writer).record(any(), any(), any());
        handler = new AccessDenialHandler(writer, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Непредъявленный принципал: строка ложится до ответа и без имени")
    void anAbsentPrincipalLeavesAnUnnamedTrailBeforeTheResponse() throws Exception {
        handler.commence(request("GET", CLOSED_PATH), response, new BadCredentialsException("no token"));

        assertThat(attempts).hasSize(1);
        Attempt attempt = attempts.getFirst();
        assertThat(attempt.outcome()).isEqualTo(AccessDenial.Outcome.PRINCIPAL_ABSENT);
        assertThat(attempt.principal())
                .as("заявленное, но не удостоверенное имя в строку не пишется")
                .isNull();
        assertThat(attempt.statusAtCallTime())
                .as("строка, заведённая после ответа, терялась бы ровно на том исходе, ради которого заводится")
                .isEqualTo(STATUS_BEFORE_ANY_RESPONSE);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Запрет операции: строка ложится до ответа и несёт имя принятого принципала")
    void aForbiddenOperationLeavesANamedTrailBeforeTheResponse() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ACCEPTED_PRINCIPAL, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));

        handler.handle(request("POST", CLOSED_PATH), response, new AccessDeniedException("not allowed"));

        assertThat(attempts).hasSize(1);
        Attempt attempt = attempts.getFirst();
        assertThat(attempt.outcome()).isEqualTo(AccessDenial.Outcome.OPERATION_FORBIDDEN);
        assertThat(attempt.principal())
                .as("на этой тропе личность удостоверена, и предмет строки — она")
                .isEqualTo(ACCEPTED_PRINCIPAL);
        assertThat(attempt.statusAtCallTime()).isEqualTo(STATUS_BEFORE_ANY_RESPONSE);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    /**
     * Поверхность — метод и путь, без query-строки: её содержимое под
     * контролем вызывающего, и туда попадают предъявленные секреты
     * (docs/models/domain/other/AccessDenial.md §«Чего строка не несёт»).
     */
    @Test
    @DisplayName("Поверхность несёт метод и путь, а параметры запроса — нет")
    void theSurfaceCarriesMethodAndPathWithoutTheQueryString() throws Exception {
        MockHttpServletRequest request = request("GET", CLOSED_PATH);
        request.setQueryString("token=secret-value");

        handler.commence(request, response, new BadCredentialsException("no token"));

        assertThat(attempts.getFirst().surface())
                .isEqualTo("GET " + CLOSED_PATH)
                .doesNotContain("secret-value");
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    /** Что писатель увидел и в каком состоянии был ответ в тот момент. */
    private record Attempt(String surface,
                           AccessDenial.Outcome outcome,
                           String principal,
                           int statusAtCallTime) {
    }
}
