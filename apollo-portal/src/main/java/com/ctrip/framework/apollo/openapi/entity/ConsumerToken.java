package com.ctrip.framework.apollo.openapi.entity;

import com.ctrip.framework.apollo.common.entity.BaseEntity;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

/**
 * 第三方应用token
 * 一个第三方应用可以有多个token
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "ConsumerToken")
@SQLDelete(sql = "Update ConsumerToken set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class ConsumerToken extends BaseEntity {

    /**
     * 第三方应用编号
     */
    @Column(name = "ConsumerId", nullable = false)
    private long consumerId;

    /**
     * token
     */
    @Column(name = "token", nullable = false)
    private String token;

    /**
     * 过期时间
     */
    @Column(name = "Expires", nullable = false)
    private Date expires;

    public long getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(long consumerId) {
        this.consumerId = consumerId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    @Override
    public String toString() {
        return toStringHelper().add("consumerId", consumerId).add("token", token)
                .add("expires", expires).toString();
    }
}
