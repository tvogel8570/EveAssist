package com.eveassist.client.pilot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "token")
public class Token implements Serializable {
    @Serial
    private static final long serialVersionUID = -4828404462323353250L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "token_id_seq")
    @SequenceGenerator(name = "token_id_seq", allocationSize = 1, initialValue = 100)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pilot_id")
    private Pilot pilot;

    @Column(name = "access_token", nullable = false, columnDefinition = "BYTEA")
    private byte[] accessToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "BYTEA")
    private byte[] refreshToken;

    @OneToMany(mappedBy = "token", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Scope> scopes = new LinkedHashSet<>();

    public void addScope(Scope scope) {
        if (this.scopes == null)
            this.scopes = new LinkedHashSet<>();

        scopes.add(scope);
        scope.setToken(this);
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
        Token token = (Token) o;
        return getId() != null && Objects.equals(getId(), token.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() :
                getClass().hashCode();
    }

    @SuppressWarnings("unused")
    public static final class TokenBuilder {
        private Long id;
        private Pilot pilot;
        private byte[] accessToken;
        private byte[] refreshToken;
        private Set<Scope> scopes;

        private TokenBuilder() {
        }

        public static TokenBuilder aToken() {
            return new TokenBuilder();
        }

        public TokenBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public TokenBuilder withPilot(Pilot pilot) {
            this.pilot = pilot;
            return this;
        }

        public TokenBuilder withAccessToken(byte[] accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public TokenBuilder withRefreshToken(byte[] refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public TokenBuilder withScopes(Set<Scope> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Token build() {
            Token token = new Token();
            token.setId(id);
            token.setPilot(pilot);
            token.setAccessToken(accessToken);
            token.setRefreshToken(refreshToken);
            token.setScopes(scopes);
            return token;
        }
    }
}