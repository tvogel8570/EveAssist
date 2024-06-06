package com.eveassist.client.pilot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "scope_id_seq")
    @SequenceGenerator(name = "scope_id_seq", allocationSize = 1, initialValue = 100)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id")
    private Token token;

    @Column(name = "privilege", nullable = false)
    private String privilege;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ?
                ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Scope scope = (Scope) o;
        return getId() != null && Objects.equals(getId(), scope.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() :
                getClass().hashCode();
    }

    @SuppressWarnings("unused")
    public static final class ScopeBuilder {
        private Long id;
        private Token token;
        private String privilege;

        private ScopeBuilder() {
        }

        public static ScopeBuilder aScope() {
            return new ScopeBuilder();
        }

        public ScopeBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public ScopeBuilder withToken(Token token) {
            this.token = token;
            return this;
        }

        public ScopeBuilder withPrivilege(String privilege) {
            this.privilege = privilege;
            return this;
        }

        public Scope build() {
            Scope scope = new Scope();
            scope.setId(id);
            scope.setToken(token);
            scope.setPrivilege(privilege);
            return scope;
        }
    }
}