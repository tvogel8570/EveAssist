package com.eveassist.client.pilot.impl;

import java.util.Arrays;

public record PilotAuthPayload(
        String[] scp,
        String jti,
        String kid,
        String sub,
        String azp,
        String tenant,
        String tier,
        String region,
        String[] aud,
        String name,
        String owner,
        Long exp,
        Long iat,
        String iss
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PilotAuthPayload that = (PilotAuthPayload) o;

        if (!Arrays.equals(scp, that.scp)) return false;
        if (!jti.equals(that.jti)) return false;
        if (!kid.equals(that.kid)) return false;
        if (!sub.equals(that.sub)) return false;
        if (!azp.equals(that.azp)) return false;
        if (!tenant.equals(that.tenant)) return false;
        if (!tier.equals(that.tier)) return false;
        if (!region.equals(that.region)) return false;
        if (!Arrays.equals(aud, that.aud)) return false;
        if (!name.equals(that.name)) return false;
        if (!owner.equals(that.owner)) return false;
        if (!exp.equals(that.exp)) return false;
        if (!iat.equals(that.iat)) return false;
        return iss.equals(that.iss);
    }

    @Override
    public String toString() {
        return "PilotAuthPayload{" +
                "scp=" + Arrays.toString(scp) +
                ", jti='" + jti + '\'' +
                ", kid='" + kid + '\'' +
                ", sub='" + sub + '\'' +
                ", azp='" + azp + '\'' +
                ", tenant='" + tenant + '\'' +
                ", tier='" + tier + '\'' +
                ", region='" + region + '\'' +
                ", aud=" + Arrays.toString(aud) +
                ", name='" + name + '\'' +
                ", owner='" + owner + '\'' +
                ", exp=" + exp +
                ", iat=" + iat +
                ", iss='" + iss + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(scp);
        result = 31 * result + jti.hashCode();
        result = 31 * result + kid.hashCode();
        result = 31 * result + sub.hashCode();
        result = 31 * result + azp.hashCode();
        result = 31 * result + tenant.hashCode();
        result = 31 * result + tier.hashCode();
        result = 31 * result + region.hashCode();
        result = 31 * result + Arrays.hashCode(aud);
        result = 31 * result + name.hashCode();
        result = 31 * result + owner.hashCode();
        result = 31 * result + exp.hashCode();
        result = 31 * result + iat.hashCode();
        result = 31 * result + iss.hashCode();
        return result;
    }
}
