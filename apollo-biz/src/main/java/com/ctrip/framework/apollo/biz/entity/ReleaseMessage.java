package com.ctrip.framework.apollo.biz.entity;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Date;

/**
 * 发送消息
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "ReleaseMessage")
public class ReleaseMessage {

    /**
     * 消息id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private long id;

    /**
     * 消息内容
     */
    @Column(name = "Message", nullable = false)
    private String message;

    /**
     * 消息改变时间
     */
    @Column(name = "DataChange_LastTime")
    private Date dataChangeLastModifiedTime;

    @PrePersist
    protected void prePersist() {
        if (this.dataChangeLastModifiedTime == null) {
            dataChangeLastModifiedTime = new Date();
        }
    }

    public ReleaseMessage() {
    }

    public ReleaseMessage(String message) {
        this.message = message;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("id", id)
                .add("message", message)
                .add("dataChangeLastModifiedTime", dataChangeLastModifiedTime)
                .toString();
    }
}
