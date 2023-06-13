package com.eveassist.api.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;
import org.hibernate.annotations.NaturalId;
import org.hibernate.validator.constraints.Length;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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
    @SequenceGenerator(name = "eve_assist_user_gen", sequenceName = "eve_assist_user_id_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;
    @NaturalId
    @Length(min = 30, max = 30)
    @Column(name = "unique_user", nullable = false, unique = true, length = 30)
    @EqualsAndHashCode.Include
    private String uniqueUser;
    @NotEmpty
    @Email(message = "You must enter a valid email")
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    @NotEmpty
    @Column(name = "screen_name", nullable = false, unique = true, length = 50)
    private String screenName;
    @Column(name = "password")
    private String password;
    @PastOrPresent
    @NotNull
    @Column(name = "create_timestamp", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime createTimestamp;
    @PastOrPresent
    @NotNull
    @Column(name = "update_timestamp", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime updateTimestamp;
    @Column
    boolean accountNonExpired;
    @Column
    boolean accountNonLocked;
    @Column
    boolean credentialsNonExpired;
    @Column
    boolean enabled;

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
}