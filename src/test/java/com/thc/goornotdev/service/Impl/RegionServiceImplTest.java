package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.RegionDto;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.util.RegionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RegionServiceImplTest {

    @Mock
    private RegionCatalog regionCatalog;

    @InjectMocks
    private RegionServiceImpl regionService;

    private RegionCatalog.Region region(String code, String sido, String sigungu) {
        return new RegionCatalog.Region(code, sido + " " + sigungu, sido, sigungu, 37.0, 127.0, 1.0);
    }

    private void givenRegions(RegionCatalog.Region... regions) {
        given(regionCatalog.regions()).willReturn(List.of(regions));
    }

    private List<RegionDto.DetailResDto> search(String keyword) {
        return regionService.list(RegionDto.ListReqDto.builder().keyword(keyword).build());
    }

    @Test
    @DisplayName("시군구명으로 찾는다")
    void searchBySigunguName() {
        givenRegions(
                region("42210", "강원특별자치도", "속초시"),
                region("11680", "서울특별시", "강남구"));

        List<RegionDto.DetailResDto> result = search("속초");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("42210");
    }

    @Test
    @DisplayName("시도명으로 찾으면 해당 시도가 모두 나온다")
    void searchBySidoName() {
        givenRegions(
                region("42210", "강원특별자치도", "속초시"),
                region("42230", "강원특별자치도", "삼척시"),
                region("11680", "서울특별시", "강남구"));

        assertThat(search("강원")).hasSize(2);
    }

    @Test
    @DisplayName("띄어 쓴 검색어도 토큰이 모두 포함되면 찾는다")
    void searchWithWhitespace() {
        givenRegions(
                region("42210", "강원특별자치도", "속초시"),
                region("11680", "서울특별시", "강남구"));

        // 전체 이름은 "강원특별자치도 속초시" 라 "강원 속초" 를 통째로 비교하면 못 찾는다
        List<RegionDto.DetailResDto> result = search("강원 속초");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("42210");
    }

    @Test
    @DisplayName("일치하는 지역이 없으면 빈 목록")
    void searchNoMatch() {
        givenRegions(region("42210", "강원특별자치도", "속초시"));

        assertThat(search("제주")).isEmpty();
    }

    @Test
    @DisplayName("검색 결과는 상한을 넘지 않는다")
    void searchLimitsResults() {
        List<RegionCatalog.Region> many = new ArrayList<>();
        for (int i = 0; i < RegionServiceImpl.MAX_RESULTS + 10; i++) {
            many.add(region("1000" + i, "경기도", "테스트시" + i));
        }
        given(regionCatalog.regions()).willReturn(many);

        assertThat(search("경기")).hasSize(RegionServiceImpl.MAX_RESULTS);
    }

    @Test
    @DisplayName("너무 짧은 검색어는 거부된다")
    void searchTooShortKeyword() {
        assertThatThrownBy(() -> search("강"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> search("  "))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> search(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("추첨 가중치는 응답에 담기지 않는다")
    void searchDoesNotExposeWeight() {
        givenRegions(region("42210", "강원특별자치도", "속초시"));

        RegionDto.DetailResDto result = search("속초").get(0);

        // DetailResDto 에 weight 필드 자체가 없다는 것을 코드로 고정해 둔다
        assertThat(result).hasNoNullFieldsOrProperties();
        assertThat(RegionDto.DetailResDto.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("weight");
    }
}
