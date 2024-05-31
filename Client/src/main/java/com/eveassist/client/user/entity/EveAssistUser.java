package com.eveassist.client.user.entity;

import com.eveassist.client.pilot.entity.Pilot;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashSet;
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
public class EveAssistUser implements Serializable {
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

    // link to keycloak
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

    @PastOrPresent
    @CreationTimestamp
    @Column(name = "create_timestamp", nullable = false)
    private Instant createTimestamp;

    @PastOrPresent
    @UpdateTimestamp
    @Column(name = "update_timestamp", nullable = false)
    private Instant updateTimestamp;


    @Column(name = "enabled", nullable = false)
    boolean enabled = false;    // users are disabled by default
    @Column(name = "account_non_expired", nullable = false)
    boolean account_non_expired = true;
    @Column(name = "account_non_locked", nullable = false)
    boolean account_non_locked = true;
    @Column(name = "credentials_non_expired", nullable = false)
    boolean credentials_non_expired = true;


    @OneToMany(mappedBy = "eveAssistUser", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Pilot> pilots = new LinkedHashSet<>();

    public String getUsername() {
        return this.email;
    }

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