package com.eveassist.api.sde.chr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.util.Objects;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "\"chrAncestries\"", schema = "evesde")
public class ChrAncestry implements Serializable {
    private static final long serialVersionUID = -6547811978630402275L;
    @Id
    @Column(name = "\"ancestryID\"", nullable = false)
    private Integer id;

    @Size(max = 100)
    @Column(name = "\"ancestryName\"", length = 100)
    private String ancestryName;

    @Column(name = "\"bloodlineID\"")
    private Integer bloodlineID;

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "perception")
    private Integer perception;

    @Column(name = "willpower")
    private Integer willpower;

    @Column(name = "charisma")
    private Integer charisma;

    @Column(name = "memory")
    private Integer memory;

    @Column(name = "intelligence")
    private Integer intelligence;

    @Column(name = "\"iconID\"")
    private Integer iconID;

    @Size(max = 500)
    @Column(name = "\"shortDescription\"", length = 500)
    private String shortDescription;

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
        ChrAncestry that = (ChrAncestry) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}