package com.example.noteworkshop.verification;

import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.security.CurrentUser;
import com.example.noteworkshop.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void shouldStoreAndClearCurrentUser() {
        CurrentUser user = new CurrentUser(1L, "student01", 0);
        UserContext.set(user);

        assertThat(UserContext.get()).isEqualTo(user);

        UserContext.clear();
        assertThat(UserContext.getOrNull()).isNull();
    }

    @Test
    void shouldRejectMissingCurrentUser() {
        UserContext.clear();

        assertThatThrownBy(UserContext::get)
                .isInstanceOf(BusinessException.class);
    }
}