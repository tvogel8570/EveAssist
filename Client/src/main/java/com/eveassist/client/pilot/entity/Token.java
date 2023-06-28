package com.eveassist.client.pilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Builder
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
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "BYTEA")
    private String refreshToken;

    @OneToMany(mappedBy = "token", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Scope> scopes = new LinkedHashSet<>();

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
        Token token = (Token) o;
        return getId() != null && Objects.equals(getId(), token.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}