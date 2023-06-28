package com.eveassist.client.pilot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "scope")
public class Scope implements Serializable {
    @Serial
    private static final long serialVersionUID = -571025616263281700L;
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "scope_seq")
    @SequenceGenerator(name = "scope_seq", allocationSize = 1, initialValue = 100)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id")
    private Token token;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "privilege", nullable = false)
    private String privilege;

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
        Scope scope = (Scope) o;
        return getId() != null && Objects.equals(getId(), scope.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}