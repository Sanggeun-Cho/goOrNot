package com.thc.goornotdev.DTO;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

public class DefaultDto {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class CreateResDto {
        Long id;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class UpdateReqDto {
        Long id;
        Boolean deleted;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailReqDto {
        Long id;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailResDto {
        Long id;
        Boolean deleted;
        LocalDateTime createdAt;
        LocalDateTime modifiedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ListReqDto {
        Boolean deleted;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class PagedListReqDto {
        @Builder.Default
        private Integer callPage = 1;

        @Builder.Default
        private Integer perPage = 10;

        private String orderBy;
        private String orderWay;
        private Boolean deleted;

        // DB 조회를 위한 Offset 은 상태 변경 없이 계산값만 반환
        public int getCalculatedOffset() {
            int page = Math.max(1, this.callPage);
            int size = Math.max(2, this.perPage);
            return size * (page - 1);
        }
    }

    @Getter @Builder
    public static class PagedListResDto<T> {
        private Integer callPage;
        private Integer totalPage;
        private Integer listCount;
        private List<T> list;

        // 요청 객체와 총 데이터 수, 실제 목록을 받아 응답 객체를 조립
        public static <T> PagedListResDto<T> of(PagedListReqDto req, int listCount, List<T> list) {
            int page = Math.max(1, req.getCallPage());
            int size = Math.max(2, req.getPerPage());

            int totalPage = (int) Math.ceil((double) listCount / size);
            totalPage = Math.max(1, totalPage); // 최소 1페이지 보장

            int finalCallPage = Math.min(page, totalPage); // 범위를 벗어난 페이지 요청 보정

            return PagedListResDto.<T>builder()
                    .callPage(finalCallPage)
                    .totalPage(totalPage)
                    .listCount(listCount)
                    .list(list)
                    .build();
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ScrollListReqDto {
        @Builder.Default
        private Integer perPage = 10;

        private Long cursor; // 이전 요청의 마지막 식별자 (mark)
        private String orderWay;
        private Boolean deleted;
    }
}
