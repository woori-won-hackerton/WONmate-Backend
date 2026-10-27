package wonit.hackathon.a1.global.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import wonit.hackathon.a1.domain.user.entity.User;

public class CustomUserDetails implements UserDetails, CredentialsContainer {

    private final Long userId;
    private final String loginId;
    private String password;

    // 아이디/비밀번호 로그인 시 사용
    public CustomUserDetails(User user) {
        this.userId = Objects.requireNonNull(user.getId(), "userId");
        this.loginId = user.getLoginId();
        this.password = user.getPassword();
    }

    // JWT 인증 시 사용
    public CustomUserDetails(Long userId) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.loginId = userId.toString();
        this.password = null;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}