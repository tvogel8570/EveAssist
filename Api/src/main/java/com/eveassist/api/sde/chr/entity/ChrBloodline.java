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
@Table(name = "\"chrBloodlines\"", schema = "evesde")
public class ChrBloodline implements Serializable {
    private static final long serialVersionUID = 1712235104334212689L;
    @Id
    @Column(name = "\"bloodlineID\"", nullable = false)
    private Integer id;

    @jakarta.validation.constraints.Size(max = 100)
    @Column(name = "\"bloodlineName\"", length = 100)
    private String bloodlineName;

    @Column(name = "\"raceID\"")
    private Integer raceID;

    @jakarta.validation.constraints.Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    @jakarta.validation.constraints.Size(max = 1000)
    @Column(name = "\"maleDescription\"", length = 1000)
    private String maleDescription;

    @jakarta.validation.constraints.Size(max = 1000)
    @Column(name = "\"femaleDescription\"", length = 1000)
    private String femaleDescription;

    @Column(name = "\"shipTypeID\"")
    private Integer shipTypeID;

    @Column(name = "\"corporationID\"")
    private Integer corporationID;

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

    @jakarta.validation.constraints.Size(max = 500)
    @Column(name = "\"shortDescription\"", length = 500)
    private String shortDescription;

    @jakarta.validation.constraints.Size(max = 500)
    @Column(name = "\"shortMaleDescription\"", length = 500)
    private String shortMaleDescription;

    @jakarta.validation.constraints.Size(max = 500)
    @Column(name = "\"shortFemaleDescription\"", length = 500)
    private String shortFemaleDescription;

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
        ChrBloodline that = (ChrBloodline) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}