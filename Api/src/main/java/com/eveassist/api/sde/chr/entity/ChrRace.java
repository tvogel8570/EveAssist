package com.eveassist.api.sde.chr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "\"chrRaces\"", schema = "evesde")
public class ChrRace implements Serializable {
    private static final long serialVersionUID = -4793478671011562663L;
    @Id
    @Column(name = "\"raceID\"", nullable = false)
    private Integer id;

    @jakarta.validation.constraints.Size(max = 100)
    @Column(name = "\"raceName\"", length = 100)
    private String raceName;

    @jakarta.validation.constraints.Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "\"iconID\"")
    private Integer iconID;

    @jakarta.validation.constraints.Size(max = 500)
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
        ChrRace chrRace = (ChrRace) o;
        return getId() != null && Objects.equals(getId(), chrRace.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}