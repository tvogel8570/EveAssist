package com.eveassist.client.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.validator.constraints.Length;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "eve_assist_user", uniqueConstraints = {
        @UniqueConstraint(name = "eve_assist_user_business_key", columnNames = {"unique_user"}),
        @UniqueConstraint(name = "eve_assist_user_email_key", columnNames = {"email"}),
        @UniqueConstraint(name = "eve_assist_user_screen_name_key", columnNames = {"screen_name"})
})
public class EveAssistUser implements Serializable, UserDetails {
    @Serial
    private static final long serialVersionUID = 4756246154027625809L;
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eve_assist_user_gen")
    @SequenceGenerator(name = "eve_assist_user_gen", sequenceName = "eve_assist_user_id_seq",
            allocationSize = 1, initialValue = 100)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version")
    Integer version;

    @NaturalId
    @Column(name = "unique_user", nullable = false, unique = true)
    private UUID uniqueUser;

    @NotEmpty
    @Email(message = "You must enter a valid email")
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @NotEmpty
    @Length(min = 5, max = 50)
    @Column(name = "screen_name", nullable = false, unique = true, length = 50)
    private String screenName;

    @Column(name = "password")
    private String password;

    @PastOrPresent
    @NotNull
    @CreationTimestamp
    @Column(name = "create_timestamp", nullable = false)
    private LocalDateTime createTimestamp;

    @PastOrPresent
    @NotNull
    @UpdateTimestamp
    @Column(name = "update_timestamp", nullable = false)
    private LocalDateTime updateTimestamp;

    @Column(name = "account_non_expired", nullable = false)
    boolean accountNonExpired = true;

    @Column(name = "account_non_locked", nullable = false)
    boolean accountNonLocked = true;

    @Column(name = "credentials_non_expired", nullable = false)
    boolean credentialsNonExpired = true;

    @Column(name = "enabled", nullable = false)
    boolean enabled = false;    // users are disabled by defaults

    // TODO hardcoded to have USER
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        GrantedAuthority authority = new SimpleGrantedAuthority("USER");
        return Set.of(authority);
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return false;
    }

    @Override
    public boolean isAccountNonLocked() {
        return false;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @SuppressWarnings("java:S2097")
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy otherProxy ?
                otherProxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy thisProxy ?
                thisProxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        EveAssistUser that = (EveAssistUser) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}