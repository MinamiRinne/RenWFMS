package org.sysu.renNameService.entity;

import javax.persistence.Column;
import javax.persistence.Id;
import java.io.Serializable;
import java.util.Objects;

public class RenAuthuserEntityPK implements Serializable {
    private String username;
    private String domain;

    @Column(name = "username")
    @Id
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Column(name = "domain")
    @Id
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RenAuthuserEntityPK that = (RenAuthuserEntityPK) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(domain, that.domain);
    }

    @Override
    public int hashCode() {

        return Objects.hash(username, domain);
    }
}
