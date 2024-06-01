package com.eveassist.client.user.entity;

import com.eveassist.client.pilot.entity.Pilot;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    public boolean isEnabled() {
        return false;
    }
    
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ?
                ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        EveAssistUser that = (EveAssistUser) o;
        return getUniqueUser() != null && Objects.equals(getUniqueUser(), that.getUniqueUser());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uniqueUser);
    }

    @SuppressWarnings("unused")
    public static final class EveAssistUserBuilder {
        private Long id;
        private Integer version;
        private UUID uniqueUser;
        private @NotEmpty
        @Email(message = "You must enter a valid email") String email;
        private @NotEmpty
        @Length(min = 5, max = 50) String screenName;
        private @PastOrPresent Instant createTimestamp;
        private @PastOrPresent Instant updateTimestamp;
        private boolean enabled;
        private boolean account_non_expired;
        private boolean account_non_locked;
        private boolean credentials_non_expired;
        private Set<Pilot> pilots;

        private EveAssistUserBuilder() {
        }

        public static EveAssistUserBuilder anEveAssistUser() {
            return new EveAssistUserBuilder();
        }

        public EveAssistUserBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public EveAssistUserBuilder withVersion(Integer version) {
            this.version = version;
            return this;
        }

        public EveAssistUserBuilder withUniqueUser(UUID uniqueUser) {
            this.uniqueUser = uniqueUser;
            return this;
        }

        public EveAssistUserBuilder withEmail(String email) {
            this.email = email;
            return this;
        }

        public EveAssistUserBuilder withScreenName(String screenName) {
            this.screenName = screenName;
            return this;
        }

        public EveAssistUserBuilder withCreateTimestamp(Instant createTimestamp) {
            this.createTimestamp = createTimestamp;
            return this;
        }

        public EveAssistUserBuilder withUpdateTimestamp(Instant updateTimestamp) {
            this.updateTimestamp = updateTimestamp;
            return this;
        }

        public EveAssistUserBuilder withEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public EveAssistUserBuilder withAccount_non_expired(boolean account_non_expired) {
            this.account_non_expired = account_non_expired;
            return this;
        }

        public EveAssistUserBuilder withAccount_non_locked(boolean account_non_locked) {
            this.account_non_locked = account_non_locked;
            return this;
        }

        public EveAssistUserBuilder withCredentials_non_expired(boolean credentials_non_expired) {
            this.credentials_non_expired = credentials_non_expired;
            return this;
        }

        public EveAssistUserBuilder withPilots(Set<Pilot> pilots) {
            this.pilots = pilots;
            return this;
        }

        public EveAssistUser build() {
            EveAssistUser eveAssistUser = new EveAssistUser();
            eveAssistUser.setId(id);
            eveAssistUser.setVersion(version);
            eveAssistUser.setUniqueUser(uniqueUser);
            eveAssistUser.setEmail(email);
            eveAssistUser.setScreenName(screenName);
            eveAssistUser.setCreateTimestamp(createTimestamp);
            eveAssistUser.setUpdateTimestamp(updateTimestamp);
            eveAssistUser.setEnabled(enabled);
            eveAssistUser.setAccount_non_expired(account_non_expired);
            eveAssistUser.setAccount_non_locked(account_non_locked);
            eveAssistUser.setCredentials_non_expired(credentials_non_expired);
            eveAssistUser.setPilots(pilots);
            return eveAssistUser;
        }
    }
}