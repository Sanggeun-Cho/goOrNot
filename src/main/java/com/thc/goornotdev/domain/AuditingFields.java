package com.thc.goornotdev.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@MappedSuperclass
public class AuditingFields {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    // 해당 값이 null 일 경우 잘못된 도메인이기에 NPE 를 잡기 위해 초기 설정값 false 로 설정
    Boolean deleted = false;

    @CreatedDate
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime createdAt;

    @LastModifiedDate
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime modifiedAt;

    @PrePersist
    public void onPrePersist(){
        this.deleted = false;
    }
}
